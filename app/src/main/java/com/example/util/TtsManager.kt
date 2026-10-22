package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(context: Context) {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    private val _speakingMessageId = MutableStateFlow<Long?>(null)
    val speakingMessageId: StateFlow<Long?> = _speakingMessageId.asStateFlow()

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                isInitialized = true
            }
        }

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.toLongOrNull()?.let { id ->
                    _speakingMessageId.value = id
                }
            }

            override fun onDone(utteranceId: String?) {
                _speakingMessageId.value = null
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _speakingMessageId.value = null
            }
        })
    }

    fun speak(messageId: Long, text: String) {
        if (!isInitialized || textToSpeech == null) return

        if (_speakingMessageId.value == messageId) {
            stop()
            return
        }

        stop()
        // Strip out markdown code block delimiters and symbols for cleaner spoken text
        val cleanedText = text
            .replace(Regex("```[a-zA-Z]*"), "")
            .replace("`", "")
            .replace("#", "")
            .replace("*", "")
            .trim()

        _speakingMessageId.value = messageId
        textToSpeech?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, messageId.toString())
    }

    fun stop() {
        textToSpeech?.stop()
        _speakingMessageId.value = null
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        _speakingMessageId.value = null
    }
}
