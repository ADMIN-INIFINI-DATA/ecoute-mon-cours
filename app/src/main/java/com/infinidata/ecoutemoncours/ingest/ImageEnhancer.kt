package com.infinidata.ecoutemoncours.ingest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Redressement du contraste avant reconnaissance.
 *
 * Le modele hors-ligne decroche sur ce qui fait le quotidien d'un cours :
 * photocopie palie, crayon a papier, photo de tableau prise de biais, papier jauni,
 * eclairage inegal. On lui redonne une image nette en noir et blanc :
 *   1. mise a l'echelle,
 *   2. niveaux de gris ponderes,
 *   3. etirement d'histogramme (les gris les plus clairs deviennent blancs, les plus sombres noirs),
 *   4. seuillage adaptatif par tuiles : chaque zone de l'image a son propre seuil,
 *      ce qui absorbe une ombre de main ou un demi-page plus sombre que l'autre.
 *
 * Tout est fait en pur Kotlin, sans bibliotheque de vision : quelques dizaines de
 * millisecondes, aucun poids ajoute a l'application.
 */
object ImageEnhancer {

    private const val MAX_DIM = 2400
    private const val MIN_WIDTH = 1000
    private const val TILE = 32
    /** Une tuile dont l'ecart clair/sombre est inferieur a ce seuil est consideree vide. */
    private const val FLAT_TILE_RANGE = 22
    /** Marge sous la moyenne locale : plus elle est haute, plus le texte pale est capture. */
    private const val BIAS = 12

    /** Charge l'image en la redimensionnant et en corrigeant son orientation EXIF. */
    fun load(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null

        var sample = 1
        // On decode deja a la bonne echelle : une photo 12 Mpx ne transite jamais
        // en pleine resolution par le tas Java.
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_DIM) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        val scale = when {
            bitmap.width < MIN_WIDTH ->
                min(2f, MAX_DIM.toFloat() / max(bitmap.width, bitmap.height))  // petite photo : on agrandit, sans exces
            max(bitmap.width, bitmap.height) > MAX_DIM ->
                MAX_DIM.toFloat() / max(bitmap.width, bitmap.height)
            else -> 1f
        }

        if (scale != 1f || rotation != 0f) {
            val matrix = Matrix().apply {
                if (scale != 1f) postScale(scale, scale)
                if (rotation != 0f) postRotate(rotation)
            }
            val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (transformed != bitmap) bitmap.recycle()
            bitmap = transformed
        }
        return bitmap
    }

    /** Image noir et blanc, prete pour la reconnaissance de texte. */
    fun enhance(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // 1. Niveaux de gris.
        val gray = ByteArray(w * h)
        val histogram = IntArray(256)
        for (i in pixels.indices) {
            val p = pixels[i]
            val v = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            gray[i] = v.toByte()
            histogram[v]++
        }

        // 2. Etirement d'histogramme sur les centiles 2 et 98 : une photocopie grise
        //    occupe souvent la plage 120-200, on la ramene sur 0-255.
        val total = w * h
        val lowCut = (total * 0.02).toInt()
        val highCut = (total * 0.98).toInt()
        var acc = 0
        var low = 0
        var high = 255
        for (v in 0..255) { acc += histogram[v]; if (acc >= lowCut) { low = v; break } }
        acc = 0
        for (v in 0..255) { acc += histogram[v]; if (acc >= highCut) { high = v; break } }
        val span = max(1, high - low)
        if (span < 250) {
            for (i in gray.indices) {
                val v = gray[i].toInt() and 0xFF
                gray[i] = (((v - low) * 255 / span).coerceIn(0, 255)).toByte()
            }
        }

        // 3. Seuillage adaptatif : moyenne et amplitude calculees par tuile,
        //    puis interpolation bilineaire pour eviter les raccords visibles.
        val tx = ceil(w.toDouble() / TILE).toInt()
        val ty = ceil(h.toDouble() / TILE).toInt()
        val means = FloatArray(tx * ty)
        val ranges = FloatArray(tx * ty)

        for (tileY in 0 until ty) {
            for (tileX in 0 until tx) {
                val x0 = tileX * TILE
                val y0 = tileY * TILE
                val x1 = min(x0 + TILE, w)
                val y1 = min(y0 + TILE, h)
                var sum = 0L
                var mn = 255
                var mx = 0
                var count = 0
                for (y in y0 until y1) {
                    var index = y * w + x0
                    for (x in x0 until x1) {
                        val v = gray[index].toInt() and 0xFF
                        sum += v
                        if (v < mn) mn = v
                        if (v > mx) mx = v
                        count++
                        index++
                    }
                }
                val slot = tileY * tx + tileX
                means[slot] = if (count == 0) 255f else sum.toFloat() / count
                ranges[slot] = (mx - mn).toFloat()
            }
        }

        // On reecrit dans le tableau de pixels d'origine : une allocation de moins.
        val out = pixels
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        for (y in 0 until h) {
            val fy = (y.toFloat() / TILE - 0.5f).coerceIn(0f, (ty - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = min(y0 + 1, ty - 1)
            val wy = fy - y0
            for (x in 0 until w) {
                val fx = (x.toFloat() / TILE - 0.5f).coerceIn(0f, (tx - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = min(x0 + 1, tx - 1)
                val wx = fx - x0

                val m = means[y0 * tx + x0] * (1 - wx) * (1 - wy) +
                        means[y0 * tx + x1] * wx * (1 - wy) +
                        means[y1 * tx + x0] * (1 - wx) * wy +
                        means[y1 * tx + x1] * wx * wy
                val r = max(
                    max(ranges[y0 * tx + x0], ranges[y0 * tx + x1]),
                    max(ranges[y1 * tx + x0], ranges[y1 * tx + x1])
                )

                val index = y * w + x
                val v = gray[index].toInt() and 0xFF
                // Zone uniforme : c'est du papier, pas du texte. On la blanchit,
                // sinon le grain du papier ressortirait en points noirs.
                out[index] = if (r < FLAT_TILE_RANGE) white
                else if (v < m - BIAS) black else white
            }
        }

        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Qualite apparente d'un texte reconnu : mots plausibles plutot que caracteres isoles. */
    fun score(text: String): Int {
        if (text.isBlank()) return 0
        var score = 0
        for (word in text.split(Regex("\\s+"))) {
            val letters = word.count { it.isLetter() }
            if (letters >= 2) score += letters
            if (word.length > 2 && abs(letters - word.length) <= 1) score += 2
        }
        return score
    }
}
