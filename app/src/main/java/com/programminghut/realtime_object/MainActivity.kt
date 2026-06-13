package com.programminghut.realtime_object

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.programminghut.realtime_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    lateinit var labels:List<String>
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap:Bitmap
    lateinit var imageView: ImageView
    lateinit var textureView: TextureView
    lateinit var visionOverlay: VisionOverlayView
    
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var model:SsdMobilenetV11Metadata1

    // VisionGuide Advanced Modules
    private val depthEstimator = DepthEstimator()
    private val riskEngine = RiskAssessmentEngine()
    private val pathPlanner = SafePathPlanner()
    private lateinit var guidanceManager: GuidanceManager
    private lateinit var hapticManager: HapticManager
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_permission()

        tts = TextToSpeech(this, this)
        guidanceManager = GuidanceManager(tts)
        hapticManager = HapticManager(this)

        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = SsdMobilenetV11Metadata1.newInstance(this)
        
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        imageView = findViewById(R.id.imageView)
        visionOverlay = findViewById(R.id.visionOverlay)
        textureView = findViewById(R.id.textureView)

        textureView.surfaceTextureListener = object:TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                open_camera()
            }
            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {}
            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean = false

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                val currentBitmap = textureView.bitmap ?: return
                var image = TensorImage.fromBitmap(currentBitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray

                val detectedObstacles = mutableListOf<VisionObstacle>()

                for (i in scores.indices) {
                    if (scores[i] > 0.45f) {
                        val label = labels[classes[i].toInt()]
                        val rect = RectF(
                            locations[i * 4 + 1], // left
                            locations[i * 4],     // top
                            locations[i * 4 + 3], // right
                            locations[i * 4 + 2]  // bottom
                        )
                        
                        // 1. Perception: Distance Estimation (Imperial/Feet)
                        val distance = depthEstimator.estimateDistance(label, rect)
                        
                        // 2. Risk Assessment (Semantic + Proximity)
                        val riskScore = riskEngine.assessRisk(label, rect, distance)
                        
                        detectedObstacles.add(VisionObstacle(label, rect, distance, riskScore))
                    }
                }

                // 3. Navigation Path Planning (Polar Histogram Map)
                val plan = pathPlanner.planPath(detectedObstacles)

                // 4. Guidance & Haptics (Eye-like instructions)
                val primaryObstacle = detectedObstacles.maxByOrNull { it.riskScore }
                guidanceManager.provideGuidance(plan, primaryObstacle)
                
                val maxHazard = riskEngine.getHazardLevel(primaryObstacle?.riskScore ?: 0f)
                hapticManager.playFeedback(maxHazard)

                // 5. Visual Rendering (Safe Corridor Glow)
                runOnUiThread {
                    visionOverlay.updateData(plan, detectedObstacles)
                    imageView.setImageBitmap(currentBitmap)
                }
            }
        }
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
        tts?.stop()
        tts?.shutdown()
        hapticManager.stop()
    }

    @SuppressLint("MissingPermission")
    fun open_camera(){
        val cameraId = cameraManager.cameraIdList.getOrNull(0) ?: return
        cameraManager.openCamera(cameraId, object:CameraDevice.StateCallback(){
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0
                val st = textureView.surfaceTexture ?: return
                val surface = Surface(st)
                val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)
                cameraDevice.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback(){
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(), null, null)
                    }
                    override fun onConfigureFailed(p0: CameraCaptureSession) {}
                }, handler)
            }
            override fun onDisconnected(p0: CameraDevice) {}
            override fun onError(p0: CameraDevice, p1: Int) {}
        }, handler)
    }

    fun get_permission(){
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) get_permission()
    }
}
