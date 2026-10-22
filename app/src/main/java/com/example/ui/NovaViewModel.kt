package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.RetrofitClient
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.repository.NovaAssistantRepository
import com.example.model.Persona
import com.example.model.PersonaRepository
import com.example.util.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NovaUiState(
    val isGenerating: Boolean = false,
    val selectedImage: Bitmap? = null,
    val inputText: String = "",
    val activePersona: Persona = PersonaRepository.personas.first(),
    val customApiKey: String = "",
    val isApiKeyMissing: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NovaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NovaAssistantRepository.create(application)
    val ttsManager = TtsManager(application)

    private val prefs = application.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        NovaUiState(
            customApiKey = prefs.getString("custom_api_key", "") ?: "",
            isApiKeyMissing = checkApiKeyMissing(prefs.getString("custom_api_key", ""))
        )
    )
    val uiState: StateFlow<NovaUiState> = _uiState.asStateFlow()

    private val _activeConversationId = MutableStateFlow<Long?>(null)
    val activeConversationId: StateFlow<Long?> = _activeConversationId.asStateFlow()

    val conversations: StateFlow<List<ConversationEntity>> = repository.getAllConversations()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val currentMessages: StateFlow<List<MessageEntity>> = _activeConversationId
        .flatMapLatest { id ->
            if (id != null && id > 0) {
                repository.getMessagesForConversation(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var generationJob: Job? = null

    init {
        // Load the latest conversation if available
        viewModelScope.launch {
            conversations.collect { list ->
                if (_activeConversationId.value == null && list.isNotEmpty()) {
                    _activeConversationId.value = list.first().id
                    val persona = PersonaRepository.getById(list.first().personaId)
                    _uiState.value = _uiState.value.copy(activePersona = persona)
                }
            }
        }
    }

    private fun checkApiKeyMissing(customKey: String?): Boolean {
        val effective = RetrofitClient.getEffectiveApiKey(customKey)
        return effective.isBlank() || effective == "MY_GEMINI_API_KEY"
    }

    fun onInputTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun onImageSelected(bitmap: Bitmap?) {
        _uiState.value = _uiState.value.copy(selectedImage = bitmap)
    }

    fun onClearSelectedImage() {
        _uiState.value = _uiState.value.copy(selectedImage = null)
    }

    fun onPersonaSelected(persona: Persona) {
        _uiState.value = _uiState.value.copy(activePersona = persona)
        val convId = _activeConversationId.value
        if (convId != null && convId > 0) {
            // Update conversation persona tag
            viewModelScope.launch {
                val current = repository.getConversation(convId)
                if (current != null) {
                    // Start a new conversation for this persona if current already has messages
                    if (currentMessages.value.isNotEmpty()) {
                        startNewChat(persona)
                    }
                }
            }
        }
    }

    fun setCustomApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("custom_api_key", trimmed).apply()
        _uiState.value = _uiState.value.copy(
            customApiKey = trimmed,
            isApiKeyMissing = checkApiKeyMissing(trimmed),
            errorMessage = null
        )
    }

    fun startNewChat(persona: Persona = _uiState.value.activePersona) {
        stopGeneration()
        viewModelScope.launch {
            val newId = repository.createNewConversation(
                title = "New Chat",
                personaId = persona.id
            )
            _activeConversationId.value = newId
            _uiState.value = _uiState.value.copy(
                activePersona = persona,
                selectedImage = null,
                inputText = "",
                errorMessage = null
            )
        }
    }

    fun selectConversation(id: Long) {
        if (_activeConversationId.value == id) return
        stopGeneration()
        viewModelScope.launch {
            val conv = repository.getConversation(id)
            if (conv != null) {
                _activeConversationId.value = id
                val persona = PersonaRepository.getById(conv.personaId)
                _uiState.value = _uiState.value.copy(
                    activePersona = persona,
                    selectedImage = null,
                    errorMessage = null
                )
            }
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_activeConversationId.value == id) {
                val remaining = conversations.value.filter { it.id != id }
                if (remaining.isNotEmpty()) {
                    selectConversation(remaining.first().id)
                } else {
                    startNewChat()
                }
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            repository.deleteAllConversations()
            startNewChat()
        }
    }

    fun renameConversation(id: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameConversation(id, newTitle)
        }
    }

    fun sendMessage(promptOverride: String? = null) {
        val prompt = (promptOverride ?: _uiState.value.inputText).trim()
        val image = _uiState.value.selectedImage
        if (prompt.isEmpty() && image == null) return

        val effectiveKey = RetrofitClient.getEffectiveApiKey(_uiState.value.customApiKey)
        if (effectiveKey.isBlank() || effectiveKey == "MY_GEMINI_API_KEY") {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Gemini API key is missing. Set GEMINI_API_KEY in the AI Studio Secrets panel, or tap Settings to configure a key."
            )
            return
        }

        val persona = _uiState.value.activePersona
        _uiState.value = _uiState.value.copy(
            inputText = "",
            selectedImage = null,
            isGenerating = true,
            errorMessage = null
        )

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            try {
                var convId = _activeConversationId.value
                if (convId == null || convId <= 0) {
                    convId = repository.createNewConversation("New Chat", persona.id)
                    _activeConversationId.value = convId
                }

                repository.saveUserMessage(convId, prompt, image)
                val assistantMsgId = repository.saveAssistantPlaceholder(convId)

                var latestAccumulated = ""
                val result = repository.streamGenerateResponse(
                    conversationId = convId,
                    persona = persona,
                    apiKey = effectiveKey,
                    latestUserBitmap = image,
                    onChunk = { accumulated ->
                        latestAccumulated = accumulated
                        repository.updateAssistantMessage(assistantMsgId, accumulated, hasError = false)
                    }
                )

                if (result.isFailure) {
                    val error = result.exceptionOrNull()?.localizedMessage ?: "Generation failed"
                    repository.updateAssistantMessage(
                        assistantMsgId,
                        "I ran into an issue while generating the response: $error\n\nPlease ensure your Gemini API key is valid and has sufficient quota.",
                        hasError = true
                    )
                } else {
                    repository.updateAssistantMessage(assistantMsgId, latestAccumulated, hasError = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage)
            } finally {
                _uiState.value = _uiState.value.copy(isGenerating = false)
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.value = _uiState.value.copy(isGenerating = false)
        ttsManager.stop()
    }

    fun retryLastMessage() {
        val messages = currentMessages.value
        val lastUserMessage = messages.lastOrNull { it.role == "user" }
        if (lastUserMessage != null) {
            sendMessage(lastUserMessage.content)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
