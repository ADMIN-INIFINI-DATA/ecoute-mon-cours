package com.infinidata.ecoutemoncours.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class DictationState(
    val listening: Boolean = false,
    /** Texte deja valide par le moteur. */
    val text: String = "",
    /** Ce qui est en train d'etre dit, pas encore stabilise. */
    val partial: String = "",
    val onDevice: Boolean = false,
    val level: Float = 0f,
    val error: String? = null
)

/**
 * Dictee vocale continue.
 *
 * Le moteur Android est concu pour des phrases : il s'arrete de lui-meme apres quelques
 * secondes de silence. Pour tenir la duree d'une fiche dictee, on le relance a chaque
 * fin de phrase tant que l'utilisatrice n'a pas mis en pause.
 *
 * Deux precautions apprises a la relecture : on REUTILISE la meme instance
 * (la detruire et la recreer a chaque phrase rebinde le service et provoque un
 * ERROR_RECOGNIZER_BUSY des la deuxieme), et on compte les echecs consecutifs pour
 * ne pas partir en boucle de relance si le micro ne capte rien.
 *
 * Toutes les methodes s'appellent depuis le thread principal, comme l'exige l'API.
 */
class Dictation(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wantsToListen = false
    private var consecutiveEmpty = 0

    private val _state = MutableStateFlow(DictationState())
    val state: StateFlow<DictationState> = _state.asStateFlow()

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (_state.value.listening) return
        wantsToListen = true
        consecutiveEmpty = 0
        _state.value = _state.value.copy(listening = true, error = null)
        listen()
    }

    fun pause() {
        wantsToListen = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.stopListening() }
        _state.value = _state.value.copy(listening = false, partial = "", level = 0f)
    }

    fun clear() {
        _state.value = _state.value.copy(text = "", partial = "")
    }

    fun release() {
        wantsToListen = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
        _state.value = DictationState()
    }

    private fun ensureRecognizer(): SpeechRecognizer {
        recognizer?.let { return it }
        // createOnDeviceSpeechRecognizer existe depuis l'API 31 : garde litteral,
        // sinon lint refuse de valider l'appel au moment du build release.
        val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        _state.value = _state.value.copy(onDevice = onDevice)

        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && onDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        created.setRecognitionListener(listener)
        recognizer = created
        return created
    }

    private fun listen(delayMs: Long = 0L) {
        if (!wantsToListen) return
        handler.postDelayed({
            if (!wantsToListen) return@postDelayed
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, _state.value.onDevice)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            runCatching { ensureRecognizer().startListening(intent) }
                .onFailure { e ->
                    wantsToListen = false
                    _state.value = _state.value.copy(
                        listening = false,
                        error = "La dictée n'a pas pu démarrer : ${e.message}"
                    )
                }
        }, delayMs)
    }

    /** Recolle les morceaux en un texte lisible, avec majuscules et espaces. */
    private fun append(chunk: String) {
        val piece = chunk.trim()
        if (piece.isEmpty()) return
        val current = _state.value.text
        val separator = when {
            current.isEmpty() -> ""
            current.last() in ".!?" -> " "
            else -> ". "
        }
        val capitalized = piece.replaceFirstChar { it.titlecase(Locale.FRENCH) }
        _state.value = _state.value.copy(text = current + separator + capitalized, partial = "")
    }

    private val listener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // Niveau sonore pour l'animation du micro : -2 dB a 10 dB en pratique.
            _state.value = _state.value.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) _state.value = _state.value.copy(partial = text)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isBlank()) consecutiveEmpty++ else consecutiveEmpty = 0
            append(text)
            relaunchOrStop(250L)
        }

        override fun onError(error: Int) {
            // Silence, absence de correspondance, moteur encore occupe : la respiration
            // normale d'une dictee continue. On relance sans rien dire.
            val benign = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                error == SpeechRecognizer.ERROR_CLIENT
            if (!benign) {
                wantsToListen = false
                _state.value = _state.value.copy(listening = false, error = messageFor(error))
                return
            }
            consecutiveEmpty++
            relaunchOrStop(400L)
        }

        /** Garde-fou : au bout de plusieurs tours sans un mot, on arrete au lieu de
         *  rebinder le service en boucle — micro ouvert et batterie pour rien. */
        private fun relaunchOrStop(delayMs: Long) {
            when {
                !wantsToListen -> _state.value = _state.value.copy(listening = false, level = 0f)
                consecutiveEmpty >= 6 -> {
                    wantsToListen = false
                    _state.value = _state.value.copy(
                        listening = false, level = 0f,
                        error = "Je n'entends rien depuis un moment, j'ai arrêté d'écouter."
                    )
                }
                else -> listen(delayMs)
            }
        }
    }

    private fun messageFor(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Problème avec le micro."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "L'autorisation micro a été refusée."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "La reconnaissance vocale a besoin d'internet sur ce téléphone, ou du pack français hors-ligne."
        SpeechRecognizer.ERROR_SERVER -> "Le service de reconnaissance vocale a répondu une erreur."
        else -> "La dictée s'est interrompue (code $code)."
    }
}
