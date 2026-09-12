package com.infinidata.ecoutemoncours.ingest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDF : rendu page par page avec le moteur du systeme (aucune dependance externe),
 * puis OCR. Marche aussi bien sur un PDF texte que sur un PDF scanne,
 * au prix d'une passe OCR. Rendu plafonne pour eviter l'OutOfMemory.
 */
object PdfImporter {

    private const val TARGET_WIDTH = 2200
    private const val MAX_PAGES = 40

    suspend fun extract(
        context: Context,
        uri: Uri,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Impossible d'ouvrir le PDF")
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val pages = minOf(renderer.pageCount, MAX_PAGES)
                val sb = StringBuilder()
                for (index in 0 until pages) {
                    onProgress(index + 1, pages)
                    renderer.openPage(index).use { page ->
                        val scale = (TARGET_WIDTH.toFloat() / page.width).coerceIn(1f, 4.5f)
                        val w = (page.width * scale).toInt()
                        val h = (page.height * scale).toInt()
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, Matrix().apply { setScale(scale, scale) },
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val result = MlKitOcr.recognize(bmp)
                        bmp.recycle()
                        sb.append(result.text).append("\n\n")
                    }
                }
                TextCleanup.clean(sb.toString())
            }
        }
    }
}
