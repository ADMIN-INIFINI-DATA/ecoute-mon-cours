package com.infinidata.ecoutemoncours.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.infinidata.ecoutemoncours.EcouteApp
import com.infinidata.ecoutemoncours.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class SpeechState(
    val docId: Long = -1,
    val title: String = "",
    val isPlaying: Boolean = false,
    val ready: Boolean = false,
    val segmentIndex: Int = 0,
    val segmentCount: Int = 0,
    val highlightStart: Int = -1,
    val highlightEnd: Int = -1,
    /** Position absolue en caracteres, utilisee pour la reprise. */
    val offset: Int = 0,
    val error: String? = null,
    val voiceLabel: String = ""
)

/**
 * Moteur de lecture. Un seul point de verite pour l'ecran de lecture ET la notification :
 * le service de premier plan ne fait qu'afficher et commander ce controleur.
 *
 * Le contexte et les reglages sont derives de l'Application, jamais d'une Activity :
 * le service peut etre recree par le systeme sans qu'aucun ecran n'ait ete ouvert.
 */
object SpeechController {

    private val appContext: Context get() = EcouteApp.instance
    private val settings: Settings get() = EcouteApp.instance.settings
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var initializing = false
    @Volatile private var enqueuedUpTo = -1
    @Volatile private var rangeSupported = false
    @Volatile private var stopRequested = false
    @Volatile private var pendingPlay = false

    private var segments: List<Segment> = emptyList()
    private var fullText: String = ""

    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    /** Enregistrement de la progression : installe une fois pour toutes par l'Application. */
    var onProgressPersist: ((Long, Int) -> Unit)? = null

    fun init() {
        if (tts != null || initializing) return
        initializing = true
        // Le moteur Google est le seul a implementer correctement onRangeStart
        // (indispensable au surlignage mot a mot). On retombe sur le moteur par defaut sinon.
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(appContext, { status ->
            initializing = false
            if (status == TextToSpeech.SUCCESS) {
                tts = engine
                engine.setOnUtteranceProgressListener(listener)
                configure(engine)
            } else {
                runCatching { engine.shutdown() }
                fallbackToDefaultEngine()
            }
        }, GOOGLE_TTS)
    }

    private fun fallbackToDefaultEngine() {
        if (initializing) return
        initializing = true
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(appContext) { status ->
            initializing = false
            if (status == TextToSpeech.SUCCESS) {
                tts = engine
                engine.setOnUtteranceProgressListener(listener)
                configure(engine)
            } else {
                _state.value = _state.value.copy(
                    error = "Aucun moteur de synthèse vocale n'est disponible sur ce téléphone."
                )
            }
        }
    }

    private fun configure(engine: TextToSpeech) {
        val result = engine.setLanguage(Locale.FRENCH)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            _state.value = _state.value.copy(
                error = "La voix française n'est pas installée. Ouvre Réglages Android > Langues et saisie > Synthèse vocale."
            )
        }
        preferOfflineVoice(engine)
        engine.setSpeechRate(settings.speechRate)
        engine.setPitch(settings.pitch)
        _state.value = _state.value.copy(ready = true, voiceLabel = engine.voice?.name.orEmpty())
        if (pendingPlay) {
            pendingPlay = false
            restartFrom(_state.value.segmentIndex)
        }
    }

    /** Une voix reseau casserait le hors-ligne ET le surlignage : on ne garde que le local. */
    private fun preferOfflineVoice(engine: TextToSpeech) {
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == Locale.FRENCH.language && !it.isNetworkConnectionRequired }
            .filterNot { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
        val chosen: Voice? = voices.firstOrNull { it.name == settings.voiceName }
            ?: voices.maxByOrNull { it.quality }
        chosen?.let { engine.voice = it }
    }

    fun availableVoices(): List<Voice> = runCatching { tts?.voices.orEmpty() }.getOrNull().orEmpty()
        .filter { it.locale.language == Locale.FRENCH.language && !it.isNetworkConnectionRequired }
        .sortedByDescending { it.quality }

    fun selectVoice(voice: Voice) {
        settings.voiceName = voice.name
        tts?.voice = voice
        _state.value = _state.value.copy(voiceLabel = voice.name)
    }

    fun setRate(rate: Float) {
        settings.speechRate = rate
        tts?.setSpeechRate(rate)
        if (_state.value.isPlaying) restartFrom(_state.value.segmentIndex)
    }

    fun load(docId: Long, title: String, text: String, resumeOffset: Int) {
        // Revenir sur l'ecran d'un cours deja en cours de lecture ne doit rien interrompre.
        if (_state.value.docId == docId && fullText == text) return
        stopPlayback()
        fullText = text
        segments = Chunker.split(text)
        val index = Chunker.indexAtOffset(segments, resumeOffset)
        _state.value = _state.value.copy(
            docId = docId,
            title = title,
            segmentIndex = index,
            segmentCount = segments.size,
            offset = segments.getOrNull(index)?.start ?: 0,
            highlightStart = -1,
            highlightEnd = -1,
            isPlaying = false,
            error = null
        )
    }

    fun play() {
        if (segments.isEmpty()) return
        stopRequested = false
        startService()
        if (tts == null) {
            // Le moteur met un instant a s'initialiser : on memorise l'intention
            // pour demarrer des qu'il est pret, au lieu d'ignorer l'appui.
            pendingPlay = true
            _state.value = _state.value.copy(isPlaying = true)
            init()
            return
        }
        restartFrom(_state.value.segmentIndex)
    }

    fun pause() {
        stopPlayback()
        SpeechService.refresh()
    }

    fun toggle() = if (_state.value.isPlaying) pause() else play()

    fun stop() {
        stopPlayback()
        runCatching { appContext.stopService(Intent(appContext, SpeechService::class.java)) }
    }

    private fun stopPlayback() {
        stopRequested = true
        pendingPlay = false
        runCatching { tts?.stop() }
        _state.value = _state.value.copy(isPlaying = false, highlightStart = -1, highlightEnd = -1)
        persist()
    }

    fun next() = jump(+1)
    fun previous() = jump(-1)

    private fun jump(delta: Int) {
        if (segments.isEmpty()) return
        val target = (_state.value.segmentIndex + delta).coerceIn(0, segments.lastIndex)
        _state.value = _state.value.copy(
            segmentIndex = target,
            offset = segments[target].start,
            highlightStart = -1, highlightEnd = -1
        )
        if (_state.value.isPlaying) restartFrom(target) else persist()
    }

    /** Appui sur un paragraphe : la lecture reprend a cet endroit. */
    fun seekToOffset(offset: Int) {
        if (segments.isEmpty()) return
        val index = Chunker.indexAtOffset(segments, offset)
        _state.value = _state.value.copy(segmentIndex = index, offset = offset)
        if (_state.value.isPlaying) restartFrom(index) else persist()
    }

    /** Toutes les manipulations de la file d'attente passent par le thread principal :
     *  les callbacks du moteur TTS arrivent, eux, sur un thread de service. */
    private fun restartFrom(index: Int) = main.post {
        val engine = tts ?: return@post
        stopRequested = false
        engine.stop()
        enqueuedUpTo = index - 1
        _state.value = _state.value.copy(isPlaying = true, segmentIndex = index)
        enqueue(2)
        SpeechService.refresh()
    }

    private fun enqueue(count: Int) {
        val engine = tts ?: return
        repeat(count) {
            val next = enqueuedUpTo + 1
            if (next in segments.indices) {
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, next.toString())
                }
                engine.speak(segments[next].text, TextToSpeech.QUEUE_ADD, params, next.toString())
                enqueuedUpTo = next
            }
        }
    }

    private val listener = object : UtteranceProgressListener() {

        override fun onStart(utteranceId: String?) {
            val index = utteranceId?.toIntOrNull() ?: return
            val seg = segments.getOrNull(index) ?: return
            _state.value = _state.value.copy(
                segmentIndex = index,
                isPlaying = true,
                offset = seg.start,
                highlightStart = if (rangeSupported) _state.value.highlightStart else seg.start,
                highlightEnd = if (rangeSupported) _state.value.highlightEnd else seg.end
            )
            SpeechService.refresh()
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            rangeSupported = true
            val index = utteranceId?.toIntOrNull() ?: return
            val seg = segments.getOrNull(index) ?: return
            _state.value = _state.value.copy(
                highlightStart = seg.start + start,
                highlightEnd = seg.start + end,
                offset = seg.start + start
            )
        }

        override fun onDone(utteranceId: String?) {
            val index = utteranceId?.toIntOrNull() ?: return
            if (stopRequested) return
            if (index >= segments.lastIndex) {
                _state.value = _state.value.copy(isPlaying = false, highlightStart = -1, highlightEnd = -1)
                persist()
                SpeechService.refresh()
                return
            }
            main.post { if (!stopRequested) enqueue(1) }
            persist()
        }

        @Deprecated("Remplacee par onError(utteranceId, errorCode)")
        override fun onError(utteranceId: String?) = Unit

        override fun onError(utteranceId: String?, errorCode: Int) {
            _state.value = _state.value.copy(
                isPlaying = false,
                error = "La synthèse vocale s'est interrompue (code $errorCode)."
            )
            SpeechService.refresh()
        }
    }

    private fun persist() {
        val s = _state.value
        if (s.docId > 0) onProgressPersist?.invoke(s.docId, s.offset)
    }

    private fun startService() {
        val intent = Intent(appContext, SpeechService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appContext.startForegroundService(intent)
            else appContext.startService(intent)
        }
    }

    fun hasContent(): Boolean = segments.isNotEmpty()

    fun textSnapshot(): String = fullText

    private const val GOOGLE_TTS = "com.google.android.tts"
}
