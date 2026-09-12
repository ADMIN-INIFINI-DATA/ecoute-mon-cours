package com.infinidata.ecoutemoncours.ui.reader

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinidata.ecoutemoncours.EcouteApp
import com.infinidata.ecoutemoncours.ai.GeminiClient
import com.infinidata.ecoutemoncours.data.db.DocumentEntity
import com.infinidata.ecoutemoncours.speech.AudioExporter
import com.infinidata.ecoutemoncours.speech.SpeechController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val doc: DocumentEntity? = null,
    val paragraphs: List<Pair<Int, String>> = emptyList(),  // offset absolu -> texte
    val busyMessage: String? = null,
    val message: String? = null,
    val sheet: String? = null
)

class ReaderViewModel : ViewModel() {

    private val app = EcouteApp.instance
    private val dao = app.dao
    val settings = app.settings

    private val _ui = MutableStateFlow(ReaderUiState())
    val ui: StateFlow<ReaderUiState> = _ui.asStateFlow()

    val speech: StateFlow<com.infinidata.ecoutemoncours.speech.SpeechState> = SpeechController.state

    fun load(id: Long) {
        SpeechController.init()
        viewModelScope.launch {
            val doc = dao.byId(id) ?: return@launch
            _ui.value = _ui.value.copy(doc = doc, paragraphs = paragraphize(doc.text), sheet = doc.summary)
            SpeechController.load(doc.id, doc.title, doc.text, doc.resumeOffset)
        }
    }

    private fun paragraphize(text: String): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        var offset = 0
        text.split("\n").forEach { line ->
            if (line.isNotBlank()) out.add(offset to line)
            offset += line.length + 1
        }
        return out
    }

    fun toggle() = SpeechController.toggle()
    fun next() = SpeechController.next()
    fun previous() = SpeechController.previous()
    fun seek(offset: Int) = SpeechController.seekToOffset(offset)
    fun setRate(rate: Float) = SpeechController.setRate(rate)

    fun buildRevisionSheet() {
        val doc = _ui.value.doc ?: return
        if (!settings.hasGeminiKey) {
            _ui.value = _ui.value.copy(message = "Ajoute une clé IA dans Réglages pour générer une fiche.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busyMessage = "Rédaction de la fiche de révision…")
            runCatching {
                withContext(Dispatchers.IO) {
                    GeminiClient(settings.geminiKey).revisionSheet(doc.text, doc.subject)
                }
            }.onSuccess { sheet ->
                dao.saveSummary(doc.id, sheet)
                _ui.value = _ui.value.copy(busyMessage = null, sheet = sheet)
            }.onFailure { e ->
                _ui.value = _ui.value.copy(busyMessage = null, message = e.message)
            }
        }
    }

    /** Lit la fiche a voix haute au lieu du cours entier. */
    fun playSheet() {
        val doc = _ui.value.doc ?: return
        val sheet = _ui.value.sheet ?: return
        SpeechController.load(doc.id, "${doc.title} — fiche", sheet, 0)
        SpeechController.play()
    }

    fun playCourse() {
        val doc = _ui.value.doc ?: return
        SpeechController.load(doc.id, doc.title, doc.text, doc.resumeOffset)
        SpeechController.play()
    }

    fun export(destination: Uri) {
        val doc = _ui.value.doc ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busyMessage = "Préparation du fichier audio…")
            runCatching {
                AudioExporter.export(app, doc.text, destination) { p ->
                    _ui.value = _ui.value.copy(busyMessage = "Audio : passage ${p.done} sur ${p.total}…")
                }
            }.onSuccess { m4a ->
                _ui.value = _ui.value.copy(
                    busyMessage = null,
                    message = if (m4a) "Fichier audio enregistré."
                    else "Enregistré, mais au format WAV : l'encodeur du téléphone a refusé l'AAC."
                )
            }.onFailure { e ->
                _ui.value = _ui.value.copy(busyMessage = null, message = e.message ?: "Export impossible.")
            }
        }
    }

    fun clearMessage() { _ui.value = _ui.value.copy(message = null) }
}
