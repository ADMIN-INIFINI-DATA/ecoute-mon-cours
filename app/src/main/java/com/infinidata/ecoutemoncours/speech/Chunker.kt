package com.infinidata.ecoutemoncours.speech

import java.text.BreakIterator
import java.util.Locale

/**
 * Decoupage du cours en segments lus l'un apres l'autre.
 * Deux raisons : TextToSpeech.speak() plafonne a 4000 caracteres (echec silencieux au-dela),
 * et un decoupage a la phrase permet de reprendre exactement ou l'on s'etait arrete.
 */
data class Segment(val text: String, val start: Int, val end: Int)

object Chunker {

    private const val MIN_LEN = 220
    private const val MAX_LEN = 1600

    fun split(text: String): List<Segment> {
        if (text.isBlank()) return emptyList()
        val iterator = BreakIterator.getSentenceInstance(Locale.FRENCH)
        iterator.setText(text)

        val raw = ArrayList<Segment>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val slice = text.substring(start, end)
            if (slice.isNotBlank()) raw.add(Segment(slice, start, end))
            start = end
            end = iterator.next()
        }

        // Regroupement des phrases trop courtes (titres, puces) pour eviter le hachage a l'oral,
        // et decoupe de securite des phrases interminables.
        val out = ArrayList<Segment>()
        var bufStart = -1
        var bufEnd = -1
        for (seg in raw) {
            if (seg.text.length > MAX_LEN) {
                if (bufStart >= 0) { out.add(Segment(text.substring(bufStart, bufEnd), bufStart, bufEnd)); bufStart = -1 }
                var s = seg.start
                while (s < seg.end) {
                    val e = minOf(s + MAX_LEN, seg.end)
                    val cut = text.lastIndexOf(' ', e - 1)
                        .takeIf { it > s + MIN_LEN && it > s } ?: e
                    out.add(Segment(text.substring(s, cut), s, cut))
                    s = cut
                }
                continue
            }
            if (bufStart < 0) { bufStart = seg.start; bufEnd = seg.end } else { bufEnd = seg.end }
            if (bufEnd - bufStart >= MIN_LEN) {
                out.add(Segment(text.substring(bufStart, bufEnd), bufStart, bufEnd))
                bufStart = -1
            }
        }
        if (bufStart >= 0) out.add(Segment(text.substring(bufStart, bufEnd), bufStart, bufEnd))
        return out.filter { it.text.isNotBlank() }
    }

    /** Index du segment contenant une position caractere donnee (reprise de lecture). */
    fun indexAtOffset(segments: List<Segment>, offset: Int): Int {
        if (segments.isEmpty()) return 0
        val idx = segments.indexOfFirst { offset < it.end }
        return if (idx < 0) segments.lastIndex else idx
    }
}
