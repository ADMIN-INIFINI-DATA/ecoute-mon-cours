package com.infinidata.ecoutemoncours.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinidata.ecoutemoncours.EcouteApp
import com.infinidata.ecoutemoncours.ai.GeminiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiagnosticState(val running: Boolean = false, val report: String? = null)

/**
 * Test de la cle IA. Il repond a la seule question qui compte quand la lecture
 * assistee « ne marche pas » : est-ce la cle, le reseau, ou le modele ?
 * Le message du service est affiche tel quel, sans reformulation.
 */
class DiagnosticViewModel : ViewModel() {

    private val settings = EcouteApp.instance.settings

    private val _state = MutableStateFlow(DiagnosticState())
    val state: StateFlow<DiagnosticState> = _state.asStateFlow()

    fun test() {
        if (_state.value.running) return
        _state.value = DiagnosticState(running = true)
        viewModelScope.launch {
            runCatching { GeminiClient(settings.geminiKey).listUsableModels() }
                .onSuccess { models ->
                    if (models.isEmpty()) {
                        _state.value = DiagnosticState(
                            report = "La clé est acceptée, mais aucun modèle de génération n'est " +
                                "disponible pour ce compte. Vérifie sur aistudio.google.com que " +
                                "l'API Gemini est bien activée pour ce projet."
                        )
                        return@onSuccess
                    }
                    val chosen = models.first()
                    settings.aiModel = chosen
                    _state.value = DiagnosticState(
                        report = "Clé valide. Modèle retenu : $chosen.\n\n" +
                            "Autres disponibles : " + models.drop(1).take(5).joinToString(", ") +
                            if (models.size > 6) "…" else ""
                    )
                }
                .onFailure { e ->
                    _state.value = DiagnosticState(
                        report = "Échec : " + (e.message ?: "raison inconnue") +
                            "\n\nSi le message parle de clé refusée, deux causes fréquentes : " +
                            "la clé a été restreinte à certaines applications Android dans Google " +
                            "Cloud (il faut alors la laisser sans restriction), ou l'API Gemini " +
                            "n'est pas activée sur le projet."
                    )
                }
        }
    }
}
