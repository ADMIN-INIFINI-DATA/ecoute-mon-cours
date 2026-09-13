package com.infinidata.ecoutemoncours.ui.dictation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinidata.ecoutemoncours.EcouteApp
import com.infinidata.ecoutemoncours.data.db.DocumentEntity
import com.infinidata.ecoutemoncours.ingest.TextCleanup
import com.infinidata.ecoutemoncours.speech.Dictation
import com.infinidata.ecoutemoncours.speech.DictationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DictationViewModel : ViewModel() {

    private val app = EcouteApp.instance
    private val dao = app.dao

    val dictation = Dictation(app)
    val state: StateFlow<DictationState> = dictation.state

    private val _savedId = MutableStateFlow<Long?>(null)
    val savedId: StateFlow<Long?> = _savedId.asStateFlow()

    fun toggle() {
        if (dictation.state.value.listening) dictation.pause() else dictation.start()
    }

    /** Enregistre la dictee comme un cours, puis laisse l'ecran d'edition prendre le relais :
     *  la reconnaissance vocale se trompe, et corriger doit etre la suite naturelle. */
    fun save() {
        if (dictation.state.value.text.isBlank()) return
        dictation.pause()
        viewModelScope.launch {
            // stopListening() rend un dernier resultat de facon asynchrone :
            // sans cette attente, la phrase en cours serait perdue.
            delay(450)
            val text = dictation.state.value.text.trim()
            if (text.isBlank()) return@launch
            val clean = TextCleanup.clean(text)
            val id = dao.insert(
                DocumentEntity(
                    title = TextCleanup.guessTitle(clean),
                    subject = TextCleanup.guessSubject(clean),
                    source = "dictée",
                    text = clean,
                    ocrEngine = "aucun"
                )
            )
            _savedId.value = id
        }
    }

    override fun onCleared() {
        dictation.release()
        super.onCleared()
    }
}
