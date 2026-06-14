package com.programminghut.realtime_object

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OcrManager {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    interface OcrCallback {
        fun onTextDetected(text: String)
        fun onError(e: Exception)
    }

    fun detectText(bitmap: Bitmap, callback: OcrCallback) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val resultText = visionText.text
                if (resultText.isBlank()) {
                    callback.onTextDetected("No text detected.")
                } else {
                    callback.onTextDetected(resultText)
                }
            }
            .addOnFailureListener { e ->
                callback.onError(e)
            }
    }
}
