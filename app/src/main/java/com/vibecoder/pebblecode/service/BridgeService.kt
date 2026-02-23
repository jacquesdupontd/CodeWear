package com.vibecoder.pebblecode.service

import android.util.Log
import com.vibecoder.pebblecode.data.CleanData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject

class BridgeService {
    companion object {
        private const val TAG = "BridgeService"
        private const val DEFAULT_HOST = "192.168.1.118"
        private const val PORT = 8080
        private const val RECONNECT_DELAY = 3000L
        private const val TAILNET_SUFFIX = ".taildd7ed4.ts.net"
    }

    private var host: String = DEFAULT_HOST
    private var ws: WebSocket? = null
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // State flows observed by UI
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _sessions = MutableStateFlow<List<String>>(emptyList())
    val sessions: StateFlow<List<String>> = _sessions

    private val _activeSession = MutableStateFlow("")
    val activeSession: StateFlow<String> = _activeSession

    private val _cleanData = MutableStateFlow(CleanData())
    val cleanData: StateFlow<CleanData> = _cleanData

    private val _prompt = MutableStateFlow<com.vibecoder.pebblecode.data.PromptData?>(null)
    val prompt: StateFlow<com.vibecoder.pebblecode.data.PromptData?> = _prompt

    // History
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history
    private val historyList = mutableListOf<String>()

    // Bridge-side history (sent on join, persists across reconnects)
    private val _bridgeHistory = MutableStateFlow<List<String>>(emptyList())
    val bridgeHistory: StateFlow<List<String>> = _bridgeHistory

    fun updateHost(newHost: String) {
        if (newHost == host) return
        host = newHost
        ws?.close(1000, "Host changed")
        ws = null
        _connected.value = false
        connect()
    }

    private fun buildWsUrl(): String {
        // IP address (contains digit + dot) = local ws://
        val isIp = host.any { it.isDigit() } && host.contains('.')
        if (isIp) return "ws://$host:$PORT"

        // Already full .ts.net = wss://
        if (host.contains(".ts.net")) return "wss://$host"

        // Just a hostname like "vnc" or "macbook-pro" = auto-complete with tailnet
        return "wss://$host$TAILNET_SUFFIX"
    }

    fun connect() {
        if (_connected.value) return // already connected
        val url = buildWsUrl()
        Log.i(TAG, "Connecting to $url")
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Bridge connected")
                _connected.value = true
                // Auto-rejoin session if we had one (reconnect after drop)
                val session = _activeSession.value
                if (session.isNotEmpty()) {
                    Log.i(TAG, "Auto-rejoining session: $session")
                    send("join", mapOf("name" to session))
                } else {
                    send("list")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Bridge closed: $reason")
                _connected.value = false
                ws = null
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Bridge error: ${t.message}")
                _connected.value = false
                ws = null
                scheduleReconnect()
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "menu" -> {
                    val arr = json.optJSONArray("sessions") ?: return
                    val list = (0 until arr.length()).map { arr.getString(it) }
                    _sessions.value = list
                    // Only clear activeSession if we explicitly requested list (not on reconnect)
                    // Don't clear if we're in a session - prevents kick to menu on reconnect
                }
                "session_joined", "session_created" -> {
                    _activeSession.value = json.optString("name", "")
                    _cleanData.value = CleanData()
                    historyList.clear()
                    _history.value = emptyList()
                    _bridgeHistory.value = emptyList()
                }
                "history" -> {
                    val arr = json.optJSONArray("lines")
                    if (arr != null) {
                        val lines = (0 until arr.length()).map { arr.getString(it) }
                        _bridgeHistory.value = lines
                        Log.i(TAG, "Received bridge history: ${lines.size} lines")
                    }
                }
                "output" -> {
                    // Parse cleanData if present
                    val cd = json.optJSONObject("cleanData")
                    if (cd != null) {
                        val clean = CleanData.fromJson(cd)
                        _cleanData.value = clean

                        // Add to history if summary changed
                        if (clean.summary.isNotEmpty()) {
                            if (historyList.isEmpty() || historyList.last() != clean.summary) {
                                historyList.add(clean.summary)
                                if (historyList.size > 50) historyList.removeAt(0)
                                _history.value = historyList.toList()
                            }
                        }
                    }

                    // Parse prompt
                    val promptJson = json.optJSONObject("prompt")
                    if (promptJson != null) {
                        val opts = promptJson.optJSONArray("options")
                        if (opts != null) {
                            val options = (0 until opts.length()).map { i ->
                                val o = opts.getJSONObject(i)
                                com.vibecoder.pebblecode.data.PromptOption(
                                    num = o.optInt("num", i + 1),
                                    label = o.optString("label", "")
                                )
                            }
                            _prompt.value = com.vibecoder.pebblecode.data.PromptData(
                                options = options,
                                isAskUser = promptJson.optBoolean("isAskUser", false),
                                question = promptJson.optString("question", "")
                            )
                        }
                    } else {
                        _prompt.value = null
                    }

                    // Fallback: parse CLEAN string from content
                    val content = json.optString("content", "")
                    if (cd == null && content.startsWith("CLEAN:")) {
                        _cleanData.value = CleanData.fromCleanString(content)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }

    fun send(type: String, extra: Map<String, String> = emptyMap()) {
        val json = JSONObject().apply {
            put("type", type)
            extra.forEach { (k, v) -> put(k, v) }
        }
        ws?.send(json.toString())
    }

    fun sendKey(num: Int) {
        if (num <= 0) return
        send("key", mapOf("content" to num.toString()))
    }

    fun sendDictation(text: String) {
        send("dictation", mapOf("content" to text))
    }

    fun joinSession(name: String) = send("join", mapOf("name" to name))
    fun createSession() = send("create")
    fun leaveSession() {
        _activeSession.value = ""
        _cleanData.value = CleanData()
        send("leave")
    }
    fun requestList() = send("list")
    fun pause() = send("pause")
    fun resume() = send("resume")
    fun accept() = send("accept")

    private fun scheduleReconnect() {
        scope.launch {
            delay(RECONNECT_DELAY)
            if (!_connected.value) connect()
        }
    }

    fun disconnect() {
        ws?.close(1000, "App closing")
        scope.cancel()
    }
}

/** Singleton bridge + foreground state flag */
object BridgeHolder {
    val instance = BridgeService()
    @Volatile var isForeground = false
}
