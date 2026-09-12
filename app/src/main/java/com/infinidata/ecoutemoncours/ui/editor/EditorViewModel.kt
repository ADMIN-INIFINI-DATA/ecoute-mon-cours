package com.infinidata.ecoutemoncours.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinidata.ecoutemoncours.EcouteApp
import com.infinidata.ecoutemoncours.data.db.DocumentEntity
import com.infinidata.ecoutemoncours.ingest.TextCleanup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditorUiState(
    val docId: Long = -1,
    val title: String = "",
    val subject: String = "Divers",
    val text: String = "",
    val saving: Boolean = false,
    val savedId: Long? = null,
    val message: String? = null
)

class EditorViewModel : ViewModel() {

    private val dao = EcouteApp.instance.dao

    private val _ui = MutableStateFlow(EditorUiState())
    val ui: StateFlow<EditorUiState> = _ui.asStateFlow()

    fun load(docId: Long) {
        if (_ui.value.docId == docId && _ui.value.text.isNotBlank()) return
        if (docId <= 0) {
            _ui.value = EditorUiState(docId = -1)
            return
        }
        viewModelScope.launch {
            dao.byId(docId)?.let { doc ->
                _ui.value = EditorUiState(
                    docId = doc.id, title = doc.title, subject = doc.subject, text = doc.text
                )
            }
        }
    }

    fun onTitle(v: String) { _ui.value = _ui.value.copy(title = v) }
    fun onSubject(v: String) { _ui.value = _ui.value.copy(subject = v) }
    fun onText(v: String) { _ui.value = _ui.value.copy(text = v) }

    fun save() {
        val state = _ui.value
        if (state.text.isBlank() || state.saving) return
        viewModelScope.launch {
            _ui.value = state.copy(saving = true)
            val title = state.title.ifBlank { TextCleanup.guessTitle(state.text) }
            val subject = state.subject.ifBlank { TextCleanup.guessSubject(state.text) }
            val id = if (state.docId > 0) {
                // Le texte a change : la position de reprise et la fiche ne valent plus rien.
                dao.updateContent(
                    id = state.docId, title = title, subject = subject, text = state.text,
                    charCount = state.text.length, resumeOffset = 0
                )
                state.docId
            } else {
                dao.insert(
                    DocumentEntity(
                        title = title, subject = subject, source = "texte",
                        text = state.text, ocrEngine = "aucun"
                    )
                )
            }
            _ui.value = _ui.value.copy(saving = false, savedId = id)
        }
    }

    fun clearMessage() { _ui.value = _ui.value.copy(message = null) }
}
