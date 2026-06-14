package com.programminghut.realtime_object

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.*
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.programminghut.realtime_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var labels: List<String>
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var textureView: TextureView
    private lateinit var visionOverlay: VisionOverlayView
    private lateinit var aliaUi: android.view.View
    private lateinit var aliaBars: android.view.View
    private lateinit var tvAliaSubtitle: android.widget.TextView
    private lateinit var tvAliaTranscription: android.widget.TextView
    
    private lateinit var cameraDevice: CameraDevice
    private lateinit var handler: Handler
    private lateinit var cameraManager: CameraManager
    private lateinit var model: SsdMobilenetV11Metadata1

    // VisionGuide Core
    private val memory = NavigationMemory()
    private val predictor = CollisionPredictor()
    private val depthEstimator = DepthEstimator()
    private val riskEngine = RiskAssessmentEngine()
    private val pathPlanner = SafePathPlanner()
    private val blueprintManager = BlueprintManager()
    private val roomRecognizer = RoomRecognizer(blueprintManager)
    private val sceneContext = SceneContextEngine()
    
    // Latest State for Voice Queries
    private var latestTrackedObstacles: List<NavigationMemory.TrackedObstacle> = emptyList()
    private var latestPlan: PathPlan? = null
    
    private lateinit var guidanceManager: GuidanceManager
    private lateinit var hapticManager: HapticManager
    private lateinit var ocrManager: OcrManager
    private lateinit var aliaAgent: AliaAgent
    private val bookingManager = BookingManager()
    private var tts: TextToSpeech? = null
    
    // Voice/Interactivity
    private var speechRecognizer: SpeechRecognizer? = null
    private var isWaitingForDestination = false
    private var isWaitingForUberConfirmation = false
    private var uberDestination = ""
    private var currentDetectedRoom: BlueprintManager.Room? = null
    private var isProcessing = false
    private var isSpeechListening = false
    private var isAliaActive = false 
    private var forceAliaProcess = false
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermissions()

        tts = TextToSpeech(this, this)
        guidanceManager = GuidanceManager(tts)
        ocrManager = OcrManager()
        aliaAgent = AliaAgent()
        guidanceManager.setOnArrivalListener(object : GuidanceManager.OnArrivalListener {
            override fun onArrivedAtTarget(roomName: String) {
                // Arrival logic handled by guidanceManager (e.g. announcement)
            }
        })
        hapticManager = HapticManager(this)
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setupSpeechRecognizer()

        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = SsdMobilenetV11Metadata1.newInstance(this)
        
        val handlerThread = HandlerThread("visionThread"); handlerThread.start()
        handler = Handler(handlerThread.looper)

        visionOverlay = findViewById(R.id.visionOverlay)
        textureView = findViewById(R.id.textureView)
        aliaUi = findViewById(R.id.aliaUi)
        aliaBars = findViewById(R.id.aliaBars)
        tvAliaSubtitle = findViewById(R.id.tvAliaSubtitle)
        tvAliaTranscription = findViewById(R.id.tvAliaTranscription)

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnAliaTrigger).setOnClickListener {
            activateAlia()
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) { openCamera() }
            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {}
            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean = false

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                if (isProcessing) return
                
                val currentBitmap = textureView.bitmap ?: return
                isProcessing = true
                
                handler.post {
                    try {
                        val isBlocked = checkCameraBlockage(currentBitmap)

                        var image = TensorImage.fromBitmap(currentBitmap)
                        image = imageProcessor.process(image)

                        val outputs = model.process(image)
                        val locations = outputs.locationsAsTensorBuffer.floatArray
                        val classes = outputs.classesAsTensorBuffer.floatArray
                        val scores = outputs.scoresAsTensorBuffer.floatArray

                        val instantaneousObstacles = mutableListOf<VisionObstacle>()
                        val hazardLabels = setOf("person", "car", "bus", "truck", "motorcycle", "bicycle", "chair", "couch", "bed", "dining table", "toilet")

                        for (i in scores.indices) {
                            if (scores[i] > 0.35f) { // Lowered to catch walls more aggressively
                                var label = labels[classes[i].toInt()]
                                if (label == "???") label = "Obstacle"
                                
                                val rect = RectF(locations[i * 4 + 1], locations[i * 4], locations[i * 4 + 3], locations[i * 4 + 2])
                                val distance = depthEstimator.estimateDistance(label, rect)
                                
                                // Enhanced wall/obstacle detection
                                val isHighRisk = (hazardLabels.contains(label) && distance < 4.0f) || distance < 3.5f
                                
                                if (isHighRisk) {
                                    val riskScore = riskEngine.assessRisk(label, rect, distance)
                                    instantaneousObstacles.add(VisionObstacle(label, rect, distance, riskScore + 100f)) 
                                } else if (distance < 12.0f) {
                                    val riskScore = riskEngine.assessRisk(label, rect, distance)
                                    instantaneousObstacles.add(VisionObstacle(label, rect, distance, riskScore))
                                }
                            }
                        }

                        val trackedObstacles = memory.update(instantaneousObstacles, 0f, 0f, 0f)
                        latestTrackedObstacles = trackedObstacles
                        currentDetectedRoom = roomRecognizer.processDetections(trackedObstacles)

                        val prediction = predictor.predict(trackedObstacles)
                        val plan = pathPlanner.planPath(trackedObstacles)
                        latestPlan = plan

                        // Only silence navigation if Alia is ACTIVELY speaking or if the UI is showing transcription
                        val isAliaBusy = isAliaActive || (tts?.isSpeaking == true)
                        val primaryObstacle = trackedObstacles.maxByOrNull { it.riskScore }
                        
                        // Pass isAliaBusy to guidanceManager to suppress voice/haptics
                        guidanceManager.provideGuidance(plan, primaryObstacle, trackedObstacles, prediction, isBlocked, currentDetectedRoom, isAliaBusy)
                        
                        val maxHazard = riskEngine.getHazardLevel(primaryObstacle?.riskScore ?: 0f)
                        if (!isAliaBusy) {
                            hapticManager.playFeedback(maxHazard, plan.steeringAngle)
                        }

                        runOnUiThread {
                            visionOverlay.updateOrientation(0f, 0f)
                            visionOverlay.updateData(plan, trackedObstacles)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isSpeechListening = true
            }
            override fun onBeginningOfSpeech() {
                // If Alia UI is visible, show "Hearing..."
                if (aliaUi.visibility == android.view.View.VISIBLE || isWaitingForDestination || isWaitingForUberConfirmation) {
                    runOnUiThread { tvAliaSubtitle.text = "Hearing..." }
                }
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isSpeechListening = false
                if (aliaUi.visibility == android.view.View.VISIBLE || isWaitingForDestination || isWaitingForUberConfirmation) {
                    runOnUiThread { tvAliaSubtitle.text = "Processing..." }
                }
            }
            override fun onError(error: Int) { 
                isSpeechListening = false
                Log.e("MainActivity", "Speech Error: $error")
                val delay = if (error == 7 || error == 6) 1000L else 2500L
                if (isAliaActive) {
                    runOnUiThread { tvAliaSubtitle.text = "Listening..." }
                }
                Handler(Looper.getMainLooper()).postDelayed({ startListening() }, delay)
            }

            override fun onResults(results: Bundle?) {
                isSpeechListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.get(0)?.lowercase()?.trim() ?: ""
                Log.i("MainActivity", "Speech Result: '$text'")
                
                if (text.isEmpty()) {
                    if (isAliaActive) startListening()
                    return
                }

                val triggers = listOf("alia", "aliyah", "area", "assistant")
                val isTriggered = triggers.any { text.contains(it) }

                runOnUiThread {
                    if (isAliaActive || isTriggered || isWaitingForDestination || isWaitingForUberConfirmation) {
                        if (aliaUi.visibility != android.view.View.VISIBLE) {
                            aliaUi.visibility = android.view.View.VISIBLE
                            startAliaAnimation()
                        }
                        tvAliaTranscription.text = text
                        tvAliaSubtitle.text = "Thinking..."
                    }
                }

                processVoiceCommand(text)
                
                // If it wasn't an Alia command or navigation prompt, restart listening immediately
                if (!isAliaActive && !isTriggered && !isWaitingForDestination && !isWaitingForUberConfirmation) {
                    startListening()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        startListening()
    }

    private fun processVoiceCommand(text: String) {
        val rawText = text.lowercase().trim()
        Log.i("MainActivity", "COMMAND_FLOW: '$rawText'. Active=$isAliaActive, Force=$forceAliaProcess")
        
        val triggers = listOf("alia", "aliyah", "area", "assistant")
        val isAliaTriggered = triggers.any { rawText.contains(it) }
        
        if (isAliaTriggered || isAliaActive || forceAliaProcess) {
            Log.i("MainActivity", "ALIA_ROUTING: Triggered=$isAliaTriggered, Active=$isAliaActive, Force=$forceAliaProcess")
            
            // If it was a voice trigger, ensure UI is active
            if (isAliaTriggered || forceAliaProcess) {
                if (!isAliaActive) activateAlia()
            }
            forceAliaProcess = false 

            // Clean the command by removing triggers for better processing
            var command = rawText
            triggers.forEach { command = command.replace(it, "") }
            command = command.trim()

            // If user just said the name, greet them
            if (command.isEmpty() && isAliaTriggered) {
                speakWithAlia("I'm here. How can I help you?", "ALIA_GREET")
                return
            }

            if (command.contains("uber") || command.contains("ride") || command.contains("taxi") || command.contains("cab")) {
                Log.i("MainActivity", "Intent: Uber Detected")
                handleUberIntent(command)
                return
            } else if (command.contains("test") || command.contains("check api") || command.contains("status") || command.contains("validity")) {
                checkApiStatus()
                return
            } else if (command.contains("look") || command.contains("describe") || command.contains("see")) {
                describeScene(command)
                return
            } else if (command.contains("read") || command.contains("ocr") || command.contains("text")) {
                performOCR()
                return
            } else if (command.contains("hello") || command.contains("hi") || command.contains("hey")) {
                speakWithAlia("I'm here. How can I help you?", "ALIA_GREET")
                return
            } else if (command.contains("stop") || command.contains("dismiss") || command.contains("thank you") || command.contains("bye")) {
                speakWithAlia("You're welcome. Returning to navigation.", "ALIA_DISMISS")
                return
            } else if (command.isNotEmpty() && command.length > 2) {
                // If it's a general question, send to Alia with context
                describeScene(command)
                return
            }
        } 
        
        if (isWaitingForDestination) {
            if (rawText.length > 2) {
                uberDestination = rawText
                isWaitingForDestination = false
                askForUberConfirmation()
            }
            return
        } 
        
        if (isWaitingForUberConfirmation) {
            handleUberConfirmation(rawText)
            return
        }

        if (rawText.contains("move ahead") || rawText.contains("is it clear") || rawText.contains("can i go")) {
            latestPlan?.let { guidanceManager.confirmPathClear(it) }
        } else if (rawText.contains("read this") || rawText.contains("read that") || rawText.contains("read text")) {
            performOCR()
        } 
    }

    private fun handleUberIntent(text: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val extracted = aliaAgent.processVoiceIntent(text)
            when {
                extracted == "MISSING" -> promptForDestination()
                extracted != "NONE" && extracted.length > 2 -> {
                    uberDestination = extracted
                    askForUberConfirmation()
                }
                else -> handleUberVoiceCommand(text)
            }
        }
    }

    private fun checkApiStatus() {
        Log.i("MainActivity", "checkApiStatus started")
        speakWithAlia("Checking my connection to the cloud. Please wait.", "API_CHECK_START")
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val working = aliaAgent.testApiConnection()
                Log.i("MainActivity", "API Status Check Result: $working")
                if (working) {
                    speakWithAlia("Connection successful. My artificial intelligence is online and ready to help.", "API_CHECK_SUCCESS")
                } else {
                    speakWithAlia("I'm sorry, I'm having trouble connecting to the server. Please check your internet connection.", "API_CHECK_FAIL")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error in checkApiStatus", e)
                speakWithAlia("An error occurred while checking the status.", "API_CHECK_ERROR")
            }
        }
    }

    private fun describeScene(userQuery: String? = null) {
        Log.d("MainActivity", "describeScene started. Query: $userQuery")
        runOnUiThread { tvAliaSubtitle.text = "Processing..." }
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val summary = aliaAgent.analyzeScene(latestTrackedObstacles, currentDetectedRoom?.name, userQuery)
                Log.d("MainActivity", "describeScene result: $summary")
                speakWithAlia(summary, "SCENE_SUMMARY")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error in describeScene", e)
                speakWithAlia("I'm having trouble reaching my brain right now.", "SCENE_SUMMARY_ERROR")
            }
        }
    }

    private fun handleUberConfirmation(text: String) {
        val confirmed = listOf("yes", "confirm", "yeah", "ok", "book it", "correct", "sure", "do it")
        val cancelled = listOf("no", "cancel", "stop", "don't", "wait")
        
        if (confirmed.any { text.contains(it) }) {
            isWaitingForUberConfirmation = false
            bookUber(uberDestination)
            if (currentDetectedRoom?.name != "Front Door") startNavigatingToExit()
        } else if (cancelled.any { text.contains(it) }) {
            speakWithAlia("Uber booking cancelled.", "UBER_CANCEL")
        }
    }

    private fun activateAlia() {
        Log.i("MainActivity", "Activating Alia Mode")
        isAliaActive = true
        forceAliaProcess = true
        
        speechRecognizer?.stopListening()
        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 100)
        runOnUiThread {
            if (aliaUi.visibility != android.view.View.VISIBLE) {
                aliaUi.visibility = android.view.View.VISIBLE
                tvAliaSubtitle.text = "I'm listening..."
                tvAliaTranscription.text = ""
                startAliaAnimation()
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({ startListening() }, 500)
    }

    private fun startAliaAnimation() {
        if (!isAliaActive) return
        
        aliaBars.animate()
            .scaleY(1.3f)
            .scaleX(1.1f)
            .setDuration(400)
            .withEndAction {
                aliaBars.animate()
                    .scaleY(0.9f)
                    .scaleX(0.95f)
                    .setDuration(450)
                    .withEndAction { startAliaAnimation() }
                    .start()
            }.start()
    }

    private fun speakWithAlia(message: String, id: String) {
        Log.i("MainActivity", "Alia Speaking: $message")
        runOnUiThread {
            tvAliaSubtitle.text = "Speaking..."
            tvAliaTranscription.text = message
        }
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, id)
    }

    private fun deactivateAlia() {
        isAliaActive = false
        isWaitingForDestination = false
        isWaitingForUberConfirmation = false
        runOnUiThread {
            aliaUi.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction { 
                    aliaUi.visibility = android.view.View.GONE 
                    aliaUi.alpha = 1f
                }.start()
        }
    }

    private fun startListening() {
        if (isSpeechListening) return 
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        
        runOnUiThread { 
            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                isSpeechListening = false
                startListening()
            }
        }
    }

    private fun startNavigatingToExit() {
        val currentRoomId = currentDetectedRoom?.id ?: "living_room"
        val path = blueprintManager.findPath(currentRoomId, "exit")
        if (path != null) guidanceManager.setNavigationPath(path, "Front Door")
    }

    private fun handleUberVoiceCommand(text: String) {
        runOnUiThread { 
            aliaUi.visibility = android.view.View.VISIBLE 
            tvAliaSubtitle.text = "Booking Mode Active"
        }
        val toIndex = text.indexOf(" to ")
        if (toIndex != -1) {
            uberDestination = text.substring(toIndex + 4).trim()
            if (uberDestination.isNotEmpty()) askForUberConfirmation() else promptForDestination()
        } else promptForDestination()
    }

    private fun promptForDestination() {
        isWaitingForDestination = true
        runOnUiThread {
            latestPlan?.let { 
                visionOverlay.updateData(it.copy(steeringAngle = 0f, suggestedAction = Action.MOVE_FORWARD), emptyList())
            } ?: visionOverlay.updateData(PathPlan(0f, 1f, Action.MOVE_FORWARD, FloatArray(0)), emptyList())
            aliaUi.visibility = android.view.View.VISIBLE
            tvAliaSubtitle.text = "Where would you like to go?"
        }
        speakWithAlia("Where would you like to go?", "DEST_ASK")
    }

    private fun askForUberConfirmation() {
        isWaitingForUberConfirmation = true
        runOnUiThread {
            latestPlan?.let { 
                visionOverlay.updateData(it.copy(steeringAngle = 0f, suggestedAction = Action.MOVE_FORWARD), emptyList())
            } ?: visionOverlay.updateData(PathPlan(0f, 1f, Action.MOVE_FORWARD, FloatArray(0)), emptyList())
            aliaUi.visibility = android.view.View.VISIBLE
            tvAliaSubtitle.text = "Confirm Uber to $uberDestination?"
        }
        speakWithAlia("I found a ride to $uberDestination. Should I confirm this booking?", "UBER_CONFIRM")
    }

    private fun performOCR() {
        val bitmap = textureView.bitmap ?: return
        speakWithAlia("Reading. Please hold still.", "OCR_START")
        ocrManager.detectText(bitmap, object : OcrManager.OcrCallback {
            override fun onTextDetected(text: String) { guidanceManager.speakOCRResult(text) }
            override fun onError(e: Exception) { speakWithAlia("Sorry, I couldn't read the text.", "OCR_ERROR") }
        })
    }

    private fun bookUber(destination: String) {
        runOnUiThread {
            aliaUi.visibility = android.view.View.VISIBLE
            tvAliaSubtitle.text = "BOOKING IN PROGRESS..."
        }
        speakWithAlia("Okay, I'm booking your ride to $destination now. One moment.", "UBER_BOOK_START")
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val ride = bookingManager.bookRide(destination)
                runOnUiThread {
                    tvAliaSubtitle.text = "RIDE CONFIRMED"
                    tvAliaTranscription.text = "${ride.carModel}\nETA: ${ride.etaMinutes} mins"
                }
                val announcement = "Success. I've booked your ride. Driver ${ride.driverName} is in a ${ride.carModel}. They will arrive in ${ride.etaMinutes} minutes."
                speakWithAlia(announcement, "UBER_BOOK_SUCCESS")
                Handler(Looper.getMainLooper()).postDelayed({ deactivateAlia() }, 8000)
            } catch (e: Exception) {
                speakWithAlia("I'm sorry, I couldn't complete the booking.", "UBER_BOOK_ERROR")
                runOnUiThread { tvAliaSubtitle.text = "BOOKING FAILED" }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, data.data)
            blueprintManager.scanBlueprintImage(bitmap)
            Toast.makeText(this, "Blueprint scanned.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() { super.onResume(); startListening() }
    override fun onPause() { super.onPause(); speechRecognizer?.stopListening() }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { runOnUiThread { speechRecognizer?.stopListening() } }
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == "ALIA_DISMISS" || utteranceId == "UBER_BOOK_SUCCESS" || utteranceId == "UBER_CANCEL") {
                        deactivateAlia()
                    }
                    runOnUiThread { startListening() }
                }
                override fun onError(utteranceId: String?) { 
                    Log.e("MainActivity", "TTS Error: $utteranceId")
                    runOnUiThread { 
                        tvAliaSubtitle.text = "Error speaking response."
                        startListening() 
                    }
                }
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close(); tts?.stop(); tts?.shutdown(); hapticManager.stop(); speechRecognizer?.destroy()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val cameraId = cameraManager.cameraIdList.getOrNull(0) ?: return
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0
                val st = textureView.surfaceTexture ?: return
                val surface = Surface(st)
                val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)
                cameraDevice.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(p0: CameraCaptureSession) { p0.setRepeatingRequest(captureRequest.build(), null, null) }
                    override fun onConfigureFailed(p0: CameraCaptureSession) {}
                }, handler)
            }
            override fun onDisconnected(p0: CameraDevice) {}
            override fun onError(p0: CameraDevice, p1: Int) {}
        }, handler)
    }

    private fun checkPermissions() {
        val permissions = arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 101)
    }

    private fun checkCameraBlockage(bitmap: Bitmap): Boolean {
        val step = 40; var total = 0L; var count = 0
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val p = bitmap.getPixel(x, y)
                total += (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toLong()
                count++
            }
        }
        return (if (count > 0) total / count else 255) < 15
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) checkPermissions()
    }
}
