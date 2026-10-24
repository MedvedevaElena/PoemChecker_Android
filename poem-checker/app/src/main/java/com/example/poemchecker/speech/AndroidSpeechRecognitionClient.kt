package com.example.poemchecker.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

fun createAndroidSpeechRecognitionClient(context: Context): SpeechRecognitionClient {
    val handler = Handler(Looper.getMainLooper())
    return SegmentedSpeechRecognitionClient(
        factory = { AndroidRecognitionEngine(context.applicationContext) },
        schedule = { restart -> handler.postDelayed({ restart() }, 250) }
    )
}

private class AndroidRecognitionEngine(private val context: Context) : RecognitionEngine {
    private var recognizer: SpeechRecognizer? = null

    override fun start(listener: RecognitionEngine.Listener) {
        check(Looper.myLooper() == Looper.getMainLooper())
        check(SpeechRecognizer.isRecognitionAvailable(context)) { "На устройстве нет поддержки SpeechRecognizer" }
        val instance = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = instance
        instance.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = listener.partial(firstResult(partialResults))
            override fun onResults(results: Bundle?) = listener.final(firstResult(results))
            override fun onError(error: Int) = listener.error(
                "Ошибка распознавания: $error",
                error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            )
        })
        instance.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    override fun close() {
        check(Looper.myLooper() == Looper.getMainLooper())
        val instance = recognizer ?: return
        recognizer = null
        try {
            instance.cancel()
        } finally {
            instance.destroy()
        }
    }

    private fun firstResult(bundle: Bundle?) =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
}
