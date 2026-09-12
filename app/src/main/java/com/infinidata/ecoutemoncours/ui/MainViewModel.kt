package com.infinidata.ecoutemoncours.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinidata.ecoutemoncours.EcouteApp
import com.infinidata.ecoutemoncours.data.db.DocumentEntity
import com.infinidata.ecoutemoncours.ingest.Importer
import com.infinidata.ecoutemoncours.ingest.TextCleanup
import com.infinidata.ecoutemoncours.web.CourseSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportUiState(
    val busy: Boolean = false,
    val step: String = "",
    val lastDocId: Long? = null,
    val message: String? = null
)

data class SearchUiState(
    val busy: Boolean = false,
    val query: String = "",
    val level: String = "",
    val hits: List<CourseSearch.Hit> = emptyList(),
    val message: String? = null
)

class MainViewModel : ViewModel() {

    private val app = EcouteApp.instance
    private val dao = app.dao
    val settings = app.settings

    val documents: StateFlow<List<DocumentEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _import = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _import.asStateFlow()

    private val _search = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _search.asStateFlow()

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            _import.value = ImportUiState(busy = true, step = "Lecture du document…")
            runCatching { Importer.import(app, uri, settings) }
                .onSuccess { imported ->
                    if (imported.text.isBlank()) {
                        _import.value = ImportUiState(
                            message = imported.warning ?: "Aucun texte n'a pu être lu sur ce document."
                        )
                        return@onSuccess
                    }
                    val id = dao.insert(Importer.toDocument(imported))
                    _import.value = ImportUiState(lastDocId = id, message = imported.warning)
                }
                .onFailure { e ->
                    _import.value = ImportUiState(message = e.message ?: "Import impossible.")
                }
        }
    }

    /** Pages issues du scanner ML Kit : plusieurs images forment un seul cours. */
    fun importScannedPages(pages: List<Uri>) {
        viewModelScope.launch {
            _import.value = ImportUiState(busy = true, step = "Lecture des pages…")
            val sb = StringBuilder()
            var engine = "local"
            var warning: String? = null
            pages.forEachIndexed { index, uri ->
                _import.value = _import.value.copy(step = "Page ${index + 1} sur ${pages.size}…")
                runCatching { Importer.import(app, uri, settings) }
                    .onSuccess {
                        sb.append(it.text).append("\n\n")
                        if (it.engine == "cloud") engine = "cloud"
                        warning = warning ?: it.warning
                    }
                    .onFailure { warning = warning ?: it.message }
            }
            val text = sb.toString().trim()
            if (text.isBlank()) {
                _import.value = ImportUiState(message = warning ?: "Aucun texte lisible sur ces pages.")
            } else {
                val id = dao.insert(
                    Importer.toDocument(Importer.Imported(text, "scan", engine))
                )
                _import.value = ImportUiState(lastDocId = id, message = warning)
            }
        }
    }

    fun clearImport() { _import.value = ImportUiState() }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }

    fun rename(id: Long, title: String, subject: String) =
        viewModelScope.launch { dao.rename(id, title, subject) }

    // ---- Recherche de cours en ligne ---------------------------------------

    fun onQuery(q: String) { _search.value = _search.value.copy(query = q) }
    fun onLevel(l: String) { _search.value = _search.value.copy(level = l) }

    fun runSearch() {
        val s = _search.value
        if (s.query.isBlank()) return
        viewModelScope.launch {
            _search.value = s.copy(busy = true, message = null)
            runCatching { CourseSearch.search(s.query, s.level) }
                .onSuccess { hits ->
                    _search.value = _search.value.copy(
                        busy = false,
                        hits = hits,
                        message = if (hits.isEmpty()) "Aucun cours trouvé pour cette recherche." else null
                    )
                }
                .onFailure {
                    _search.value = _search.value.copy(busy = false, message = "Pas de connexion au moment de la recherche.")
                }
        }
    }

    fun addFromWeb(hit: CourseSearch.Hit, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            _search.value = _search.value.copy(busy = true)
            runCatching { CourseSearch.fetchPlainText(hit) }
                .onSuccess { text ->
                    if (text.isBlank()) {
                        _search.value = _search.value.copy(busy = false, message = "Cet article est vide.")
                        return@onSuccess
                    }
                    val id = dao.insert(
                        com.infinidata.ecoutemoncours.data.db.DocumentEntity(
                            title = hit.title,
                            subject = TextCleanup.guessSubject(text),
                            source = "web",
                            text = TextCleanup.clean(text),
                            ocrEngine = "aucun",
                            attribution = "${hit.attribution} — ${hit.pageUrl}"
                        )
                    )
                    _search.value = _search.value.copy(busy = false)
                    onReady(id)
                }
                .onFailure {
                    _search.value = _search.value.copy(busy = false, message = "Téléchargement impossible.")
                }
        }
    }
}
