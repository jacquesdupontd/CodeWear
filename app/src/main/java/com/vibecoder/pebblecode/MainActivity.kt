package com.vibecoder.pebblecode

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.vibecoder.pebblecode.data.AppState
import com.vibecoder.pebblecode.haptics.HapticsManager
import com.vibecoder.pebblecode.service.BridgeHolder
import com.vibecoder.pebblecode.service.SessionForegroundService
import com.vibecoder.pebblecode.ui.screens.MenuScreen
import com.vibecoder.pebblecode.ui.screens.QuestionScreen
import com.vibecoder.pebblecode.ui.screens.SessionScreen
import com.vibecoder.pebblecode.ui.screens.SettingsScreen
import com.vibecoder.pebblecode.ui.theme.PebbleCodeTheme
import com.vibecoder.pebblecode.ui.theme.getThemeByKey
import com.vibecoder.pebblecode.ui.theme.loadThemeKey
import com.vibecoder.pebblecode.ui.theme.saveThemeKey
import com.vibecoder.pebblecode.voice.VoiceInput
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "PebbleCode"
    }

    private val bridge = BridgeHolder.instance
    private lateinit var haptics: HapticsManager
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var voiceLauncher: ActivityResultLauncher<android.content.Intent>
    private var currentAppState = AppState.MENU

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        haptics = HapticsManager(this)

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PebbleCode::Bridge")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        voiceLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val text = VoiceInput.extractText(result.resultCode, result.data)
            if (text != null) {
                Log.i(TAG, "Voice: $text")
                bridge.sendDictation(text)
                haptics.shortPulse()
            }
            bridge.resume()
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        // Start/stop foreground service based on active session
        lifecycleScope.launch {
            bridge.activeSession.collect { session ->
                val intent = Intent(this@MainActivity, SessionForegroundService::class.java)
                if (session.isNotEmpty()) {
                    startForegroundService(intent)
                } else {
                    stopService(intent)
                }
            }
        }

        setContent {
            // Theme state — triggers full recomposition on change
            var themeKey by remember { mutableStateOf(loadThemeKey(this@MainActivity)) }
            val currentTheme = remember(themeKey) { getThemeByKey(themeKey) }

            PebbleCodeTheme(theme = currentTheme) {
                PebbleCodeApp(
                    themeKey = themeKey,
                    onThemeChanged = { newKey ->
                        saveThemeKey(this@MainActivity, newKey)
                        themeKey = newKey
                    }
                )
            }
        }
    }

    private fun launchVoice() {
        bridge.pause()
        try {
            voiceLauncher.launch(VoiceInput.createIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Voice launch failed: ${e.message}")
            bridge.resume()
        }
    }

    @Composable
    private fun PebbleCodeApp(
        themeKey: String,
        onThemeChanged: (String) -> Unit
    ) {
        val navController = rememberSwipeDismissableNavController()

        val connected by bridge.connected.collectAsStateWithLifecycle()
        val sessions by bridge.sessions.collectAsStateWithLifecycle()
        val activeSession by bridge.activeSession.collectAsStateWithLifecycle()
        val cleanData by bridge.cleanData.collectAsStateWithLifecycle()
        val prompt by bridge.prompt.collectAsStateWithLifecycle()

        val appState by remember(activeSession, cleanData) {
            derivedStateOf {
                when {
                    activeSession.isEmpty() -> AppState.MENU
                    cleanData.isQuestion -> AppState.QUESTION
                    else -> AppState.SESSION
                }
            }
        }

        LaunchedEffect(appState) { currentAppState = appState }

        // Navigate reactively
        LaunchedEffect(appState) {
            val current = navController.currentDestination?.route
            when (appState) {
                AppState.MENU -> if (current != "menu" && current != "settings") {
                    navController.navigate("menu") { popUpTo("menu") { inclusive = true } }
                }
                AppState.SESSION -> if (current != "session") {
                    navController.navigate("session") { popUpTo("menu") }
                }
                AppState.QUESTION -> if (current != "question") {
                    navController.navigate("question") { popUpTo("session") }
                }
            }
        }

        // Haptics — only on QUESTION or truly finished (working -> ready)
        var wasWorking by remember { mutableStateOf(false) }
        LaunchedEffect(cleanData.status) {
            val isWorking = !cleanData.isIdle && !cleanData.isQuestion
            if (wasWorking && cleanData.isReady) {
                haptics.doublePulse()
            }
            wasWorking = isWorking
        }
        LaunchedEffect(appState) {
            if (appState == AppState.QUESTION) haptics.doublePulse()
        }

        // Question state
        var questionSelected by remember { mutableIntStateOf(0) }
        LaunchedEffect(appState) {
            if (appState == AppState.QUESTION) questionSelected = 0
        }

        // Wake lock
        @SuppressLint("WakelockTimeout")
        fun acquireWakeLock() { wakeLock?.acquire() }
        LaunchedEffect(activeSession) {
            if (activeSession.isNotEmpty()) acquireWakeLock()
            else if (wakeLock?.isHeld == true) wakeLock?.release()
        }

        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "menu"
        ) {
            composable("menu") {
                MenuScreen(
                    sessions = sessions,
                    connected = connected,
                    onJoin = { bridge.joinSession(it) },
                    onCreate = { bridge.createSession() },
                    onRefresh = { bridge.requestList() },
                    onSettings = { navController.navigate("settings") }
                )
            }

            composable("settings") {
                SettingsScreen(
                    currentThemeKey = themeKey,
                    onThemeSelected = { newKey ->
                        onThemeChanged(newKey)
                        navController.popBackStack()
                    }
                )
            }

            composable("session") {
                val hasPrompt = prompt != null && prompt?.isAskUser == false
                val promptText = prompt?.options?.firstOrNull()?.label ?: ""

                DisposableEffect(Unit) {
                    onDispose {
                        if (bridge.activeSession.value.isNotEmpty()) {
                            bridge.leaveSession()
                        }
                    }
                }

                val bridgeHistory by bridge.bridgeHistory.collectAsState()
                SessionScreen(
                    cleanData = cleanData,
                    hasPrompt = hasPrompt,
                    promptText = promptText,
                    bridgeHistory = bridgeHistory,
                    onLaunchVoice = { launchVoice() }
                )
            }

            composable("question") {
                val options = cleanData.questionOptions
                QuestionScreen(
                    questionText = cleanData.questionText,
                    options = options,
                    selectedIndex = questionSelected,
                    onNavigate = { index ->
                        questionSelected = index
                    },
                    onConfirm = { index ->
                        questionSelected = index
                        haptics.shortPulse()
                        val promptOpts = prompt?.options ?: emptyList()
                        if (index < promptOpts.size) {
                            bridge.sendKey(promptOpts[index].num)
                        }
                    },
                    onOther = { launchVoice() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        BridgeHolder.isForeground = true
        bridge.connect()
    }

    override fun onPause() {
        super.onPause()
        BridgeHolder.isForeground = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bridge.activeSession.value.isEmpty()) {
            bridge.disconnect()
        }
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }
}
