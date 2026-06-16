package fr.buildtool.apkforge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Etat global de l'ecran unique. */
data class UiState(
    val serverReachable: Boolean? = null,   // null = pas encore teste
    val chainReady: Boolean = false,
    val nativeReady: Boolean = false,
    val prootReady: Boolean = false,
    val url: String = "",
    val phase: Phase = Phase.IDLE,
    val logLines: List<String> = emptyList(),
    val currentJob: String? = null,
    val buildStatus: String? = null,        // running | success | failed
    val apkReadyUrl: String? = null,
    val memMb: Int = 0,                      // heap Gradle en Mo ; 0 = defaut serveur
) {
    enum class Phase { IDLE, CONNECTING, SETUP, BUILDING, DONE }
}

class BuildViewModel : ViewModel() {

    private val client = BuildClient()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init { checkServer() }

    fun onUrlChange(v: String) { _state.value = _state.value.copy(url = v) }

    /** Regle l'allocation memoire (heap Gradle, en Mo ; 0 = defaut serveur). */
    fun setMem(mb: Int) { _state.value = _state.value.copy(memMb = mb) }

    /** Teste la connexion au serveur local et l'etat de la chaine. */
    fun checkServer() {
        viewModelScope.launch {
            _state.value = _state.value.copy(phase = UiState.Phase.CONNECTING)
            val st = client.status()
            _state.value = if (st == null) {
                _state.value.copy(serverReachable = false, phase = UiState.Phase.IDLE)
            } else {
                _state.value.copy(
                    serverReachable = true,
                    chainReady = st.chainReady,
                    nativeReady = st.nativeReady,
                    prootReady = st.prootReady,
                    phase = UiState.Phase.IDLE,
                )
            }
        }
    }

    /** Lance l'installation de la chaine (premier demarrage). */
    fun runSetup() {
        viewModelScope.launch {
            val jid = client.startSetup() ?: return@launch
            _state.value = _state.value.copy(
                phase = UiState.Phase.SETUP, currentJob = jid,
                logLines = emptyList(), buildStatus = "running", apkReadyUrl = null,
            )
            pollLogs(jid, isSetup = true)
        }
    }

    /** Lance un build a partir de l'URL saisie. */
    fun startBuild() {
        val url = _state.value.url.trim()
        if (url.isEmpty()) return
        viewModelScope.launch {
            val jid = client.startBuild(url, memMb = _state.value.memMb) ?: run {
                _state.value = _state.value.copy(
                    logLines = listOf("Impossible de joindre le serveur de build."),
                    buildStatus = "failed", phase = UiState.Phase.DONE,
                )
                return@launch
            }
            _state.value = _state.value.copy(
                phase = UiState.Phase.BUILDING, currentJob = jid,
                logLines = emptyList(), buildStatus = "running", apkReadyUrl = null,
            )
            pollLogs(jid, isSetup = false)
        }
    }

    /** Boucle de polling des logs jusqu'a la fin du job. */
    private fun pollLogs(jobId: String, isSetup: Boolean) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var cursor = 0
            while (true) {
                val chunk = client.logs(jobId, cursor)
                if (chunk != null) {
                    if (chunk.lines.isNotEmpty()) {
                        _state.value = _state.value.copy(
                            logLines = _state.value.logLines + chunk.lines,
                        )
                    }
                    cursor = chunk.next
                    if (chunk.status != "running") {
                        val apk = if (!isSetup && chunk.status == "success")
                            client.apkUrl(jobId) else null
                        _state.value = _state.value.copy(
                            phase = UiState.Phase.DONE,
                            buildStatus = chunk.status,
                            apkReadyUrl = apk,
                        )
                        if (isSetup && chunk.status == "success") checkServer()
                        break
                    }
                }
                delay(1000)
            }
        }
    }

    /** Repart sur un nouvel ecran de build vierge (garde l'URL). */
    fun reset() {
        pollJob?.cancel()
        _state.value = _state.value.copy(
            phase = UiState.Phase.IDLE, logLines = emptyList(),
            currentJob = null, buildStatus = null, apkReadyUrl = null,
        )
    }
}
