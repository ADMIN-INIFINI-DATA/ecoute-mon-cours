package com.infinidata.ecoutemoncours.ingest

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * DOCX = un ZIP contenant word/document.xml. On lit le XML avec le parseur du systeme :
 * zero dependance, zero probleme de licence, ~2 Mo d'APK economises face a Apache POI
 * (qui, de toute facon, ne fonctionne pas correctement sur Android).
 */
object DocxImporter {

    suspend fun extract(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val xml = readEntry(context, uri, "word/document.xml") ?: error("Fichier .docx illisible")
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(xml.inputStream(), "UTF-8")

        val sb = StringBuilder()
        var event = parser.eventType
        var inText = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "t" -> inText = true
                    "tab" -> sb.append('\t')
                    "br" -> sb.append('\n')
                }
                XmlPullParser.TEXT -> if (inText) sb.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "p" -> sb.append('\n')
                }
            }
            event = parser.next()
        }
        TextCleanup.clean(sb.toString())
    }

    private fun readEntry(context: Context, uri: Uri, entryName: String): ByteArray? {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == entryName) {
                        val out = ByteArrayOutputStream()
                        zip.copyTo(out)
                        return out.toByteArray()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return null
    }
}
