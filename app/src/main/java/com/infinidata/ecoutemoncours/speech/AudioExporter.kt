package com.infinidata.ecoutemoncours.speech

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Export audio du cours pour l'ecouter comme un morceau de musique.
 * TextToSpeech.synthesizeToFile ne produit que du WAV : on concatene les morceaux,
 * puis on encode en M4A (AAC) avec le codec du systeme — environ 10 fois plus leger,
 * lisible par tous les lecteurs, sans ajouter la moindre bibliotheque a l'application.
 */
object AudioExporter {

    data class Progress(val done: Int, val total: Int)

    /** Retourne true si le fichier ecrit est bien un M4A, false s'il a fallu se rabattre
     *  sur le WAV parce que l'encodeur du telephone a refuse. */
    suspend fun export(
        context: Context,
        text: String,
        destination: Uri,
        onProgress: (Progress) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val segments = Chunker.split(text)
        require(segments.isNotEmpty()) { "Rien à exporter." }

        val workDir = File(context.cacheDir, "export").apply { deleteRecursively(); mkdirs() }
        val engine = awaitEngine(context)
        try {
            val parts = ArrayList<File>(segments.size)
            segments.forEachIndexed { index, segment ->
                val file = File(workDir, "part_$index.wav")
                synthesize(engine, segment.text, file, index)
                parts.add(file)
                onProgress(Progress(index + 1, segments.size))
            }
            val merged = File(workDir, "merged.wav")
            mergeWav(parts, merged)
            val encoded = runCatching { encodeToM4a(merged, File(workDir, "cours.m4a")) }.getOrNull()
            // Si l'encodeur du telephone refuse, on livre le WAV plutot que rien —
            // l'appelant en est informe par le message de retour.
            val source = encoded ?: merged
            context.contentResolver.openOutputStream(destination)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: error("Impossible d'écrire le fichier choisi.")
            encoded != null
        } finally {
            engine.shutdown()
            workDir.deleteRecursively()
        }
    }

    private suspend fun awaitEngine(context: Context): TextToSpeech = suspendCancellableCoroutine { cont ->
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine.setLanguage(Locale.FRENCH)
                cont.resume(engine)
            } else {
                runCatching { engine.shutdown() }
                cont.resumeWithException(IllegalStateException("Synthèse vocale indisponible."))
            }
        }
        cont.invokeOnCancellation { runCatching { engine.shutdown() } }
    }

    private suspend fun synthesize(engine: TextToSpeech, text: String, file: File, index: Int) =
        suspendCancellableCoroutine { cont ->
            val id = "export_$index"
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }
                @Deprecated("Remplacee par onError(utteranceId, errorCode)")
                override fun onError(utteranceId: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("Échec de synthèse."))
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("Échec de synthèse ($errorCode)."))
                }
            })
            val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id) }
            val result = engine.synthesizeToFile(text, params, file, id)
            if (result != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resumeWithException(IllegalStateException("Échec de synthèse."))
            }
        }

    // ---- WAV ----------------------------------------------------------------

    private data class WavInfo(val sampleRate: Int, val channels: Int, val bits: Int, val dataOffset: Long, val dataSize: Long)

    private fun readWavInfo(file: File): WavInfo {
        RandomAccessFile(file, "r").use { raf ->
            raf.skipBytes(12) // en-tete RIFF/WAVE
            var sampleRate = 22050; var channels = 1; var bits = 16
            var dataOffset = 0L; var dataSize = 0L
            while (raf.filePointer < raf.length() - 8) {
                val id = ByteArray(4).also { raf.readFully(it) }.toString(Charsets.US_ASCII)
                val size = readLeInt(raf)
                when (id) {
                    "fmt " -> {
                        require(size in 16..4096) { "En-tête WAV invalide." }
                        val chunk = ByteArray(size).also { raf.readFully(it) }
                        channels = le16(chunk, 2)
                        sampleRate = le32(chunk, 4)
                        bits = le16(chunk, 14)
                    }
                    "data" -> {
                        dataOffset = raf.filePointer
                        dataSize = size.toLong().coerceAtMost(raf.length() - dataOffset)
                        raf.seek(dataOffset + dataSize)
                    }
                    else -> raf.seek(raf.filePointer + size + (size % 2))
                }
            }
            return WavInfo(sampleRate, channels, bits, dataOffset, dataSize)
        }
    }

    private fun readLeInt(raf: RandomAccessFile): Int {
        val b = ByteArray(4).also { raf.readFully(it) }
        return le32(b, 0)
    }

    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun mergeWav(parts: List<File>, output: File) {
        val first = readWavInfo(parts.first())
        val total = parts.sumOf { readWavInfo(it).dataSize }
        output.outputStream().use { out ->
            out.write(wavHeader(first.sampleRate, first.channels, first.bits, total))
            parts.forEach { part ->
                val info = readWavInfo(part)
                RandomAccessFile(part, "r").use { raf ->
                    raf.seek(info.dataOffset)
                    val buffer = ByteArray(64 * 1024)
                    var remaining = info.dataSize
                    while (remaining > 0) {
                        val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
        }
    }

    private fun wavHeader(sampleRate: Int, channels: Int, bits: Int, dataSize: Long): ByteArray {
        val byteRate = sampleRate * channels * bits / 8
        val header = ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt((36 + dataSize).toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * bits / 8).toShort())
        header.putShort(bits.toShort())
        header.put("data".toByteArray())
        header.putInt(dataSize.toInt())
        return header.array()
    }

    // ---- AAC / M4A ----------------------------------------------------------

    private fun encodeToM4a(wav: File, output: File): File {
        val info = readWavInfo(wav)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, info.sampleRate, info.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, if (info.channels > 1) 96_000 else 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32 * 1024)
        }
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = try {
            MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Throwable) {
            codec.release()
            throw e
        }

        try {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        RandomAccessFile(wav, "r").use { raf ->
            raf.seek(info.dataOffset)
            var remaining = info.dataSize
            var presentationUs = 0L
            var inputDone = false
            val deadline = System.currentTimeMillis() + 10 * 60 * 1000

            while (true) {
                if (System.currentTimeMillis() > deadline) {
                    error("L'encodage audio a dépassé le temps imparti.")
                }
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        buffer.clear()
                        val chunk = ByteArray(minOf(buffer.capacity().toLong(), remaining).toInt())
                        val read = if (chunk.isEmpty()) 0 else raf.read(chunk)
                        if (read <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buffer.put(chunk, 0, read)
                            codec.queueInputBuffer(inIndex, 0, read, presentationUs, 0)
                            remaining -= read
                            val frames = read / (info.channels * info.bits / 8)
                            presentationUs += frames * 1_000_000L / info.sampleRate
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val encoded = codec.getOutputBuffer(outIndex)!!
                        if (bufferInfo.size > 0 && muxerStarted &&
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            return output
                        }
                    }
                }
            }
        }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }
}
