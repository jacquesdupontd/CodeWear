# PebbleCode -> WearOS Port: Comprehensive Roadmap

## Table of Contents
1. [Architecture Overview](#1-architecture-overview)
2. [Project Structure](#2-project-structure)
3. [Phase-by-Phase Implementation Plan](#3-phase-by-phase-implementation-plan)
4. [Screen-by-Screen Mapping](#4-screen-by-screen-mapping)
5. [Button/Gesture Mapping](#5-buttongesture-mapping)
6. [CLEAN Protocol Parsing](#6-clean-protocol-parsing)
7. [Voice Input](#7-voice-input)
8. [Vibration/Haptics Mapping](#8-vibrationhaptics-mapping)
9. [Power Optimization](#9-power-optimization)
10. [Bridge Changes](#10-bridge-changes)

---

## 1. Architecture Overview

### Current Pebble Architecture (3-hop)
```
Mac (bridge/server.js)
    |
    | WebSocket (ws://192.168.1.118:8080)
    v
Phone (pkjs/index.js - PebbleKit JS relay)
    |
    | BLE AppMessage (2048 byte inbox limit)
    v
Pebble Watch (watchapp.c - C, 144x168px, 3 buttons)
```

### New WearOS Architecture (2-hop, direct)
```
Mac (bridge/server.js)
    |
    | WebSocket (ws://192.168.1.118:8080) - direct WiFi
    v
WearOS Watch (Kotlin + Jetpack Compose, 320-450px round/square, touch + crown)
```

### What Gets Eliminated
- **pkjs/index.js** - The entire PebbleKit JS phone relay is gone. WearOS connects directly to the bridge over WiFi.
- **AppMessage serialization** - No more 2048-byte inbox limit, no send queue, no backoff logic, no `trySend()` retry dance.
- **Emoji/accent stripping** - WearOS supports full Unicode. The `cleanEmojis()`, `sanitizePreserveNewlines()`, `stripAccents()`, `sanitize_ascii()`, `deaccent()` functions are all unnecessary.
- **Hyphenation** - The `hyphenate()` function exists because Pebble's 144px screen fits 23 chars per line. WearOS text wrapping is handled by the layout engine.
- **Color code prefixes** - The `W`, `B`, `C`, `R`, `G`, `Y`, `O`, `L` single-char color codes in the VERBOSE buffer are a Pebble hack. WearOS uses real styled text (SpannableString or Compose annotated strings).

### What Stays The Same
- **bridge/server.js** - The Mac-side bridge is almost unchanged. It still polls tmux, parses JSONL transcripts, detects prompts/suggestions/tasks, and sends structured data over WebSocket.
- **CLEAN protocol format** - `CLEAN:userCmd|summary|status|lastTool|suggestion|activeTask|diff` is the data contract. The watch just parses it differently (Kotlin instead of C string ops).
- **WebSocket message types** - `list`, `join`, `create`, `leave`, `accept`, `dictation:text`, `pause`, `resume`, `key` -- all identical.
- **All bridge-side detection logic** - `detectPrompt()`, `extractCleanData()`, `extractRealtimeStatus()`, `extractActiveTask()`, `extractRealtimeTool()`, `detectSuggestionFromTmux()`, `extractDiffBackdrop()` stay on the bridge.

### Key Advantages of WearOS
| Aspect | Pebble | WearOS |
|--------|--------|--------|
| Screen | 144x168px, 64 colors | 320-450px, full color |
| RAM | 24KB app heap | 512MB+ system |
| CPU | ARM Cortex-M3, 100MHz | ARM Cortex-A53+, 1.2GHz+ |
| Connectivity | BLE only (via phone) | WiFi + BLE direct |
| Input | 3 physical buttons | Touch + 1-2 buttons + rotary crown |
| Text | Fixed bitmap fonts, 23 chars/line | Scalable fonts, 30-40+ chars/line |
| Voice | Pebble dictation (phone-proxied) | Google SpeechRecognizer (on-device or cloud) |
| Message size | 2048 bytes max | Unlimited (WebSocket frames) |
| Language | C | Kotlin |
| UI Framework | Layers/TextLayers, manual drawing | Jetpack Compose for Wear OS |

---

## 2. Project Structure

### Gradle Module Layout
```
wearos-port/
  app/
    build.gradle.kts
    src/
      main/
        AndroidManifest.xml
        java/com/vibecoder/pebblecode/
          PebbleCodeApp.kt              # Application class
          MainActivity.kt               # Single-activity Compose entry

          data/
            CleanMessage.kt             # CLEAN protocol data class
            CleanParser.kt              # CLEAN: string parser
            WebSocketClient.kt          # OkHttp WebSocket to bridge
            BridgeRepository.kt         # State management, reconnection
            SessionInfo.kt              # Session data class

          ui/
            theme/
              Theme.kt                  # Dark theme, terminal colors
              Colors.kt                 # GColor equivalents (Malachite, Cyan, etc.)
              Typography.kt             # Monospace + sans-serif fonts

            navigation/
              AppNavigation.kt          # NavHost: Menu -> Session -> Question

            screens/
              MenuScreen.kt             # Session list (replaces draw_menu)
              SessionScreen.kt          # CLEAN mode main display
              QuestionScreen.kt         # AskUserQuestion full-screen
              VerboseScreen.kt          # Streaming colored text (optional)

            components/
              ClaudeTextZone.kt         # Scrolling summary text (top area)
              CommandRow.kt             # Last tool, horizontal marquee
              PromptRow.kt              # Suggestion (yellow) or user cmd (white)
              StatusBar.kt              # Colored status bar (bottom)
              GlitchOverlay.kt          # Visual glitch effects
              DiffBackdrop.kt           # Diff stream background

          service/
            BridgeConnectionService.kt  # Foreground service for persistent connection

          voice/
            VoiceInputManager.kt        # SpeechRecognizer wrapper

          haptics/
            HapticsManager.kt           # Vibration patterns

        res/
          values/
            strings.xml
            colors.xml                  # Terminal color palette
          drawable/
            ic_launcher.xml
          mipmap-*/
            ic_launcher.webp

  build.gradle.kts                      # Root build file
  settings.gradle.kts
  gradle.properties
  gradle/
    libs.versions.toml                  # Version catalog
```

### Key Dependencies (libs.versions.toml)
```toml
[versions]
kotlin = "2.0.21"
compose-bom = "2024.12.01"
wear-compose = "1.4.1"
okhttp = "4.12.0"
lifecycle = "2.8.7"
navigation-compose = "2.8.5"
horologist = "0.6.22"

[libraries]
# Wear OS Compose
wear-compose-material = { module = "androidx.wear.compose:compose-material", version.ref = "wear-compose" }
wear-compose-foundation = { module = "androidx.wear.compose:compose-foundation", version.ref = "wear-compose" }
wear-compose-navigation = { module = "androidx.wear.compose:compose-navigation", version.ref = "wear-compose" }

# WebSocket
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }

# Lifecycle
lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }

# Horologist (Wear OS utilities)
horologist-compose-layout = { module = "com.google.android.horologist:horologist-compose-layout", version.ref = "horologist" }
```

### Minimum SDK Requirements
- **compileSdk**: 35
- **minSdk**: 30 (Wear OS 3.0+, required for Compose)
- **targetSdk**: 35

---

## 3. Phase-by-Phase Implementation Plan

### Phase 0: Project Scaffolding (Day 1)
**Goal**: Buildable empty WearOS app that runs on emulator.

**Files to create**:
- `settings.gradle.kts` - Project settings
- `build.gradle.kts` (root) - Top-level Gradle config
- `app/build.gradle.kts` - App module with Wear Compose dependencies
- `gradle.properties` - Kotlin/Android properties
- `gradle/libs.versions.toml` - Version catalog
- `app/src/main/AndroidManifest.xml` - Manifest with `uses-feature android.hardware.type.watch`
- `app/src/main/java/com/vibecoder/pebblecode/PebbleCodeApp.kt` - Application class
- `app/src/main/java/com/vibecoder/pebblecode/MainActivity.kt` - Entry point with empty Compose surface
- `app/src/main/java/com/vibecoder/pebblecode/ui/theme/Theme.kt` - Dark theme
- `app/src/main/java/com/vibecoder/pebblecode/ui/theme/Colors.kt` - Terminal color palette
- `app/src/main/res/values/strings.xml` - App name
- `app/src/main/res/drawable/ic_launcher.xml` - Launcher icon

**Verification**: `./gradlew assembleDebug` succeeds. App launches on Wear OS emulator showing "PebbleCode" text on black background.

### Phase 1: WebSocket Connection (Day 2)
**Goal**: Watch connects directly to bridge, receives session list.

**Files to create**:
- `app/src/main/java/com/vibecoder/pebblecode/data/WebSocketClient.kt`
- `app/src/main/java/com/vibecoder/pebblecode/data/BridgeRepository.kt`
- `app/src/main/java/com/vibecoder/pebblecode/data/SessionInfo.kt`

**WebSocketClient.kt** - Core implementation:
```kotlin
class WebSocketClient(private val url: String = "ws://192.168.1.118:8080") {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .build()

    private var webSocket: WebSocket? = null

    // StateFlows exposed to UI
    val connectionState: StateFlow<ConnectionState>
    val messages: SharedFlow<BridgeMessage>

    fun connect()
    fun disconnect()
    fun send(message: String)  // JSON string

    // Auto-reconnect with exponential backoff (3s, 6s, 12s, max 30s)
    private fun scheduleReconnect()
}
```

**BridgeRepository.kt** - State management:
```kotlin
class BridgeRepository(private val wsClient: WebSocketClient) {
    val sessions: StateFlow<List<String>>
    val activeSession: StateFlow<String?>
    val cleanMessage: StateFlow<CleanMessage?>
    val connectionState: StateFlow<ConnectionState>

    fun requestSessionList()    // sends { type: "list" }
    fun joinSession(name: String)  // sends { type: "join", name: "..." }
    fun createSession()         // sends { type: "create" }
    fun leaveSession()          // sends { type: "leave" }
    fun sendAccept()            // sends { type: "accept" }
    fun sendKey(num: Int)       // sends { type: "key", content: "N" }
    fun sendDictation(text: String)  // sends { type: "dictation", content: "..." }
    fun sendPause()             // sends { type: "pause" }
    fun sendResume()            // sends { type: "resume" }
}
```

**Key difference from Pebble**: No queue, no backoff, no `trySend()`. WebSocket over WiFi is reliable -- just send.

**Verification**: Watch connects to bridge. Log shows `[BRIDGE] Connected`. Session list appears in Logcat.

### Phase 2: Menu Screen (Day 3)
**Goal**: Display tmux sessions, navigate, join.

**Files to create**:
- `app/src/main/java/com/vibecoder/pebblecode/ui/screens/MenuScreen.kt`
- `app/src/main/java/com/vibecoder/pebblecode/ui/navigation/AppNavigation.kt`

**MenuScreen.kt** maps directly from `draw_menu()` in watchapp.c:

| Pebble (C) | WearOS (Compose) |
|-------------|-------------------|
| `GColorCyan` title "VibeCoder" at y=5 | `Text("PebbleCode", color = PebbleCyan, fontSize = 18.sp)` centered at top |
| `GColorDarkGray` hint text | `Text("Swipe to navigate", color = Color.DarkGray, fontSize = 12.sp)` |
| `s_selected_idx` highlight with `GColorDarkGray` fill | `ScalingLazyColumn` with `itemsIndexed`, selected item gets `Chip` with highlight |
| Bottom hints "Long UP = New" / "Long DN = Refresh" | Floating action buttons or bottom sheet hints |

**Interaction mapping** (details in Section 5):
- Pebble UP/DOWN = WearOS scroll (touch drag or rotary crown)
- Pebble SELECT = WearOS tap on session item
- Pebble long UP (create) = WearOS FAB "+" button or long-press empty area
- Pebble long DOWN (refresh) = WearOS pull-to-refresh or swipe-down gesture

**Verification**: Menu shows sessions from bridge. Tapping a session navigates to Session screen.

### Phase 3: Session Screen - CLEAN Mode (Days 4-6)
**Goal**: Full CLEAN mode display with all four zones.

This is the biggest phase. The Pebble CLEAN mode layout is defined in `create_clean_layers()` at lines 2024-2101 of watchapp.c.

**Files to create**:
- `app/src/main/java/com/vibecoder/pebblecode/data/CleanMessage.kt`
- `app/src/main/java/com/vibecoder/pebblecode/data/CleanParser.kt`
- `app/src/main/java/com/vibecoder/pebblecode/ui/screens/SessionScreen.kt`
- `app/src/main/java/com/vibecoder/pebblecode/ui/components/ClaudeTextZone.kt`
- `app/src/main/java/com/vibecoder/pebblecode/ui/components/CommandRow.kt`
- `app/src/main/java/com/vibecoder/pebblecode/ui/components/PromptRow.kt`
- `app/src/main/java/com/vibecoder/pebblecode/ui/components/StatusBar.kt`

**CleanMessage.kt**:
```kotlin
data class CleanMessage(
    val userCmd: String = "",
    val summary: String = "",
    val status: String = "Ready",
    val lastTool: String = "",
    val suggestion: String = "",
    val activeTask: String = "",
    val diff: String = "",
    // Prompt data (from JSON, not from CLEAN string)
    val prompt: PromptData? = null,
    val isQuestion: Boolean = false
)

data class PromptData(
    val options: List<PromptOption>,
    val isAskUser: Boolean = false,
    val question: String = ""
)

data class PromptOption(
    val num: Int,
    val label: String
)
```

**SessionScreen.kt layout** (Column, top to bottom):
```
+----------------------------------+
|  Claude Text Zone                | ~65% of screen height
|  (scrolling summary text,        |  Replaces s_clean_claude_layer
|   auto-scroll or manual)         |  Purple/Magenta text on black
|                                  |
+----------------------------------+
|  Command Row (cyan)              | ~10%
|  $ git commit -m "fix"... ->     |  Replaces s_clean_command_layer
+---- separator line -------- -----+
|  Prompt Row (white or yellow)    | ~10%
|  User cmd or suggestion ->       |  Replaces s_clean_prompt_layer
+----------------------------------+
|  Status Bar (colored bg)         | ~15%
|  Ready / Working... / Done       |  Replaces s_clean_status_layer
+----------------------------------+
```

**Sub-phase 3a**: Static layout with dummy data
**Sub-phase 3b**: Wire up to WebSocket, parse CLEAN messages
**Sub-phase 3c**: Auto-scrolling Claude text zone (LazyColumn or animated offset)
**Sub-phase 3d**: Horizontal marquee for CommandRow and PromptRow
**Sub-phase 3e**: Status bar color logic (maps from `update_clean_ui_state()`)

**Verification**: Join a session where Claude is active. See live CLEAN data updating on watch.

### Phase 4: Prompt Bar / Interactive Prompts (Day 7)
**Goal**: Yes/No/Always quick answers when Claude asks permission.

**Files to modify**:
- `SessionScreen.kt` - overlay prompt bar
- `BridgeRepository.kt` - handle prompt data from bridge messages

**Pebble behavior** (from `inbox_received_callback`, lines 1582-1632):
- When `PROMPT_FLAG` is present, a bottom bar appears with "^ Yes  o Always  v No"
- UP sends `s_prompt_keys[0]`, SELECT sends `s_prompt_keys[1]`, DOWN sends `s_prompt_keys[2]`
- Status bar turns orange with prompt text

**WearOS equivalent**:
- Bottom sheet or overlay with 2-3 `Chip` buttons: "Yes", "Always", "No"
- Tapping sends the key number via `sendKey(num)`
- Status bar background changes to `PebbleOrange`
- Haptic feedback on tap (same as `vibes_short_pulse()`)

### Phase 5: AskUserQuestion Screen (Day 8)
**Goal**: Full-screen question display with option selection.

**Files to create**:
- `app/src/main/java/com/vibecoder/pebblecode/ui/screens/QuestionScreen.kt`

**Pebble behavior** (from `draw_question()` and `parse_question_from_clean()`, lines 607-707):
- Cyan question text at top (wrapping, max 42px)
- Separator line
- Options listed vertically, 20px each, selected one highlighted in DarkGreen
- Last option is always "Other (speak)" in yellow
- UP/DOWN navigate, SELECT confirms or triggers dictation for "Other"

**WearOS equivalent**:
```kotlin
@Composable
fun QuestionScreen(question: String, options: List<PromptOption>, onSelect: (Int) -> Unit) {
    ScalingLazyColumn {
        item {
            Text(question, color = PebbleCyan, fontSize = 14.sp, maxLines = 3)
        }
        item { Divider(color = Color.DarkGray) }
        itemsIndexed(options) { index, option ->
            Chip(
                label = { Text(option.label) },
                onClick = { onSelect(option.num) },
                colors = if (option.num == 0) /* "Other" */
                    ChipDefaults.secondaryChipColors()
                else
                    ChipDefaults.primaryChipColors()
            )
        }
    }
}
```

**Special case**: When "Other (speak)" is tapped, launch voice input (Phase 7).

### Phase 6: History Navigation (Day 9)
**Goal**: Swipe/scroll through past CLEAN summaries.

**Pebble behavior** (from lines 84-89, and `up_click_handler`/`down_click_handler`):
- `s_clean_history[CLEAN_HISTORY_MAX]` stores last 4 summaries
- UP/DOWN in CLEAN mode cycles through history
- `s_clean_hist_index` tracks current position

**WearOS equivalent**:
- Store history in `BridgeRepository` as `List<CleanMessage>` (can store more than 4, WearOS has plenty of RAM -- store 20+)
- Horizontal pager (HorizontalPager from Compose) or vertical swipe gestures
- Current = latest, swipe left = older, swipe right = newer
- Or: use the rotary crown to scroll through history entries
- Visual indicator: dots at bottom showing position in history

### Phase 7: Voice Input (Day 10-11)
**Goal**: Replace Pebble dictation with Android SpeechRecognizer.

**Files to create**:
- `app/src/main/java/com/vibecoder/pebblecode/voice/VoiceInputManager.kt`

**Pebble dictation flow** (from lines 1700-1838):
1. Long press SELECT
2. `s_in_dictation = true` -- block all incoming messages
3. Send `pause` to bridge (with retry up to 10x, 100ms intervals)
4. Wait 1.5s for BLE to quiet down
5. Kill ALL timers and animations
6. Call `dictation_session_start()` -- Pebble native dictation UI
7. On callback: `sanitize_ascii()` to strip non-ASCII
8. Send `dictation:text` to bridge
9. Wait 1s, then send `resume` (with retry up to 10x, 200ms intervals)
10. Restart blink timer

**WearOS simplified flow**:
1. Long press hardware button, or tap mic icon, or gesture
2. Send `pause` to bridge (one WebSocket send, no retry needed)
3. Launch `SpeechRecognizer` intent or `RemoteInput` for voice
4. On result: send `dictation:text` to bridge (no ASCII sanitization needed -- bridge handles it)
5. Send `resume` to bridge
6. No timer killing needed -- Compose handles lifecycle automatically

**VoiceInputManager.kt**:
```kotlin
class VoiceInputManager(private val context: Context) {

    fun startVoiceInput(onResult: (String) -> Unit, onError: () -> Unit) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                     RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        // Launch via ActivityResultLauncher
    }
}
```

**Key difference**: No need to pause/kill timers. WearOS voice input runs in a separate activity. The WebSocket connection stays alive. We only send `pause`/`resume` to tell the bridge to stop/start sending CLEAN updates (to avoid the watch processing data while the user is speaking -- although on WearOS this is not strictly necessary since there are no BLE conflicts).

**Decision point**: On WearOS, the `pause`/`resume` protocol may be entirely optional. The watch can keep receiving data while voice input is active -- there is no BLE contention. Consider keeping it for consistency, or removing it to simplify.

### Phase 8: Glitch Effects (Day 12)
**Goal**: Visual glitch overlay on new data and actions.

**Files to create**:
- `app/src/main/java/com/vibecoder/pebblecode/ui/components/GlitchOverlay.kt`

**Pebble behavior** (from `fx_update()` and `trigger_glitch_short/text()`, lines 1141-1195):
- `s_glitch_frames`: random scanlines + white shards drawn over screen
- `s_flash_frames`: full white flash
- `trigger_glitch_short()`: 3 glitch frames + 1 flash (on button press, new cmd)
- `trigger_glitch_text()`: 10 glitch + 10 flash (on new summary text)
- 60ms per frame tick

**WearOS equivalent**:
```kotlin
@Composable
fun GlitchOverlay(active: Boolean, intensity: GlitchIntensity) {
    if (!active) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Random horizontal bars (scanlines)
        repeat(8) {
            val y = Random.nextFloat() * size.height
            val x = Random.nextFloat() * size.width * 0.6f
            val w = size.width * (0.2f + Random.nextFloat() * 0.6f)
            drawRect(Color.LightGray.copy(alpha = 0.3f),
                     topLeft = Offset(x, y), size = Size(w, 2.dp.toPx()))
        }
        // White flash bars
        if (intensity == GlitchIntensity.HEAVY) {
            repeat(3) {
                val y = Random.nextFloat() * size.height
                val h = (4 + Random.nextInt(6)).dp.toPx()
                drawRect(Color.White.copy(alpha = 0.5f),
                         topLeft = Offset(0f, y), size = Size(size.width, h))
            }
        }
    }

    // Auto-dismiss after duration
    LaunchedEffect(active) {
        delay(if (intensity == GlitchIntensity.HEAVY) 600L else 180L)
        // Signal dismiss
    }
}
```

### Phase 9: Verbose Mode (Day 13 - Optional)
**Goal**: Port the streaming colored text display (VERBOSE mode).

**Files to create**:
- `app/src/main/java/com/vibecoder/pebblecode/ui/screens/VerboseScreen.kt`

**Pebble behavior** (from `canvas_update()` in MODE_VERBOSE and `flow_pass()`, lines 318-487):
- Color-coded text with single-char prefix: C=cyan, R=red, G=green, etc.
- Character-by-character streaming animation (`s_chars_shown` incrementing)
- Blinking cursor at end of text
- Auto-scroll to bottom, manual scroll with UP/DOWN
- Fade-in on last few characters

**WearOS equivalent**:
- `AnnotatedString` with `SpanStyle` for colored text segments
- `LazyColumn` with auto-scroll via `rememberLazyListState().animateScrollToItem()`
- Streaming effect via `LaunchedEffect` incrementing visible char count
- This mode is lower priority since CLEAN mode is the primary display

**Decision point**: VERBOSE mode may not be worth porting initially. On WearOS, the larger screen makes CLEAN mode sufficient. Consider making this a Phase 2 feature after initial release.

### Phase 10: Foreground Service + Always-On (Day 14)
**Goal**: Keep WebSocket alive when screen is off, support ambient mode.

**Files to create**:
- `app/src/main/java/com/vibecoder/pebblecode/service/BridgeConnectionService.kt`

**Details**:
- Foreground service with persistent notification ("PebbleCode connected to: sessionName")
- WebSocket client lives in the service, survives activity lifecycle
- Ambient mode: simplified display (no animations, no color, minimal updates to save OLED burn-in)
- Wake lock for keeping WiFi alive (important: `PowerManager.PARTIAL_WAKE_LOCK` + `WifiManager.WifiLock`)

**AndroidManifest.xml additions**:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

<service
    android:name=".service.BridgeConnectionService"
    android:foregroundServiceType="connectedDevice"
    android:exported="false" />
```

### Phase 11: Polish and Edge Cases (Days 15-17)
**Goal**: Handle real-world usage patterns.

**Items**:
- WiFi disconnection and reconnection (watch going out of range, WiFi sleep)
- Bridge server restart detection (WebSocket close code 1006)
- Screen rotation handling (some WearOS watches rotate)
- Font size accessibility settings
- Round vs square screen adaptation (test on both form factors)
- Battery level display in status bar
- Connection status indicator (WiFi icon or dot)
- Crash reporting setup
- Settings screen: bridge IP address configuration (currently hardcoded to 192.168.1.118:8080)

---

## 4. Screen-by-Screen Mapping

### 4.1 Menu Screen

**Pebble** (`draw_menu()`, lines 549-605):
```
+--144px--+
|VibeCoder | cyan, 18pt bold, centered
|UP/DN nav | dark gray, 14pt, centered
|          |
| session1 | light gray, 14pt (selected: white on dark gray bg)
|>session2 | <-- highlighted
| session3 |
|          |
|Long UP=  | mint green, 14pt
|  New     |
|Long DN=  | yellow, 14pt
|  Refresh |
+----------+
```

**WearOS** (`MenuScreen.kt`):
```
+--390px round--+
|                |
|  PebbleCode    | cyan, 20sp, centered
|  ----------    |
|                |
| [session1    ] | Chip, full width
| [session2    ] | Chip, highlighted
| [session3    ] | Chip, full width
|                |
|  [+] Create    | FAB or bottom chip
|                |
+----------------+
```

**Implementation**:
```kotlin
@Composable
fun MenuScreen(
    sessions: List<String>,
    onJoin: (String) -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit
) {
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(state = listState) {
            item {
                Text("PebbleCode",
                     color = PebbleCyan,
                     fontSize = 20.sp,
                     fontWeight = FontWeight.Bold)
            }

            if (sessions.isEmpty()) {
                item {
                    Text("No sessions", color = Color.DarkGray)
                }
            }

            items(sessions) { session ->
                Chip(
                    onClick = { onJoin(session) },
                    label = { Text(session) },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = Color(0xFF333333)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Chip(
                    onClick = onCreate,
                    label = { Text("New Session") },
                    icon = { Icon(Icons.Default.Add, "Create") },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }

    // Pull-to-refresh or swipe-down for refresh
    // Replaces Pebble's "long DOWN = refresh"
}
```

### 4.2 Session Screen (CLEAN Mode)

**Pebble** (`create_clean_layers()` + `canvas_update()`, lines 2024-2101, 709-781):
```
+--144px--+  y=0
|Claude   | s_clean_claude_layer (0-111px, magenta, 14pt, word-wrap)
|text     | PropertyAnimation vertical scroll for long text
|zone     | Clipped to 111px height
|         |
|         |
|         |
|         |
+---111---+
|$ cmd    | s_clean_command_layer (111-127, cyan, 14pt, marquee)
+- - - - -+ separator line at y=127 (dark gray)
|prompt   | s_clean_prompt_layer (129-145, white or yellow, 14pt, marquee)
+---150---+
| STATUS  | s_clean_status_layer (150-168, colored bg, 14pt, center/marquee)
+----------+
```

**WearOS** (`SessionScreen.kt`):
```
+--390px round--+
|                |
| Claude         | ~65% height
| summary        | Scrollable text, magenta/purple
| text zone      | Auto-scroll to bottom
| (multiple      | Manual scroll with touch/crown
|  paragraphs)   |
|                |
+----------------+
| $ git commit   | Cyan, single line, marquee if overflow
+--- thin line --+
| fix: update... | White (cmd) or Yellow (suggestion)
+----------------+
|    Ready       | Green bg, centered
+----------------+
```

**Key differences**:
- WearOS has 2.5x more vertical space -- Claude text zone can show much more
- No pixel-level Y coordinate math needed -- Compose `Column` with `weight()` handles layout
- Marquee: use `Modifier.horizontalScroll` with animated offset or `MarqueeText` composable
- Round screen: content area is narrower at top/bottom edges -- use `ScalingLazyColumn` or padding adjustments

**Status bar color logic** (from `update_clean_ui_state()`, lines 490-547):
```kotlin
fun statusBarColors(cleanMsg: CleanMessage): Pair<Color, Color> {
    return when {
        cleanMsg.prompt != null -> PebbleOrange to Color.Black
        cleanMsg.status.startsWith("QUESTION:") -> PebbleOrange to Color.Black
        cleanMsg.suggestion.isNotEmpty() -> PebbleCobaltBlue to Color.White
        cleanMsg.activeTask.isNotEmpty() -> PebblePurple to Color.White
        "..." in cleanMsg.status || "working" in cleanMsg.status.lowercase() ->
            PebbleOrange to Color.Black
        "Done" in cleanMsg.status || "Ready" in cleanMsg.status ->
            PebbleIslamicGreen to Color.White
        "Error" in cleanMsg.status || "Failed" in cleanMsg.status ->
            Color.Red to Color.White
        else -> Color.DarkGray to Color.White
    }
}
```

### 4.3 Question Screen (AskUserQuestion)

**Pebble** (`draw_question()`, lines 660-707):
```
+--144px--+
|Question  | cyan, 14pt, word-wrap, max 42px height
|text here |
+- - - - -+
|> Option1 | selected: white on DarkGreen, bold
|  Option2 | light gray, bold
|  Option3 | light gray, bold
|  Other   | yellow (triggers dictation)
+----------+
```

**WearOS** (`QuestionScreen.kt`):
```
+--390px round--+
|                |
| What would you | cyan, 16sp, centered
| like to do?    |
|                |
| [Option 1    ] | Chip, selectable
| [Option 2    ] | Chip, selectable
| [Option 3    ] | Chip, selectable
| [  Other     ] | Chip, yellow accent, triggers voice
|                |
+----------------+
```

### 4.4 Verbose Screen (Optional)

**Pebble** (`flow_pass()` draw mode, lines 318-487):
- Custom word-by-word rendering with per-character color codes
- Streaming animation (chars revealed 5 per tick at 25ms)
- Blinking cursor block at end of text
- Suggestion text with typing animation

**WearOS**: Use `AnnotatedString` with colored spans. The streaming animation can be done with a `LaunchedEffect` that increments a `charCount` state variable. Display `text.take(charCount)` in a `BasicText` composable.

---

## 5. Button/Gesture Mapping

### Physical Input Comparison
| Pebble | WearOS |
|--------|--------|
| UP button (physical) | Touch scroll up / Rotary crown up |
| DOWN button (physical) | Touch scroll down / Rotary crown down |
| SELECT button (physical) | Tap / Hardware button 1 |
| Long UP (500ms) | Long press top area / Double-tap crown |
| Long DOWN (500ms) | Long press bottom area / Swipe gesture |
| Long SELECT (500ms) | Long press center / Hardware button long press |
| BACK button (exits app) | Swipe right to dismiss (WearOS system gesture) |

### Menu Screen Gestures
| Action | Pebble | WearOS |
|--------|--------|--------|
| Navigate sessions | UP/DOWN buttons | Scroll (touch or crown) |
| Join session | SELECT | Tap on session chip |
| Create session | Long UP | "+" FAB button tap |
| Refresh list | Long DOWN | Pull-to-refresh (swipe down from top) |
| Exit app | BACK | Swipe right (system) |

### Session Screen (CLEAN Mode) Gestures
| Action | Pebble | WearOS |
|--------|--------|--------|
| Scroll Claude text | Auto-scroll (manual: UP/DOWN in verbose) | Touch scroll or crown in Claude zone |
| History: prev | UP (when no prompt) | Swipe left or crown rotate left |
| History: next | DOWN (when no prompt) | Swipe right or crown rotate right |
| Accept suggestion | SELECT (when suggestion visible) | Tap suggestion chip / Tap "Accept" button |
| Answer prompt (Yes) | UP | Tap "Yes" chip |
| Answer prompt (middle) | SELECT | Tap middle option chip |
| Answer prompt (No) | DOWN | Tap "No" chip |
| Voice input | Long SELECT | Long press hardware button / Tap mic icon |
| Toggle backlight | Long UP | Not needed (WearOS manages display automatically) |
| Toggle VERBOSE/CLEAN | Long DOWN | Settings toggle / Swipe gesture |
| Return to menu | BACK | Swipe right |

### Question Screen Gestures
| Action | Pebble | WearOS |
|--------|--------|--------|
| Navigate options | UP/DOWN | Scroll (touch or crown) |
| Select option | SELECT | Tap on option chip |
| Voice "Other" | SELECT on "Other" option | Tap "Other" chip -> launches voice |

### Recommended WearOS Button Configuration
Most WearOS watches have 1-2 hardware buttons plus the crown:
- **Hardware Button 1 (top)**: Primary action context-dependent
  - Menu: no action
  - Session: toggle mic icon visibility
  - Prompt visible: "Yes"
- **Hardware Button 2 (bottom, if present)**: Secondary action
  - Menu: create session
  - Session: toggle settings
- **Crown rotation**: Scroll content (Claude text, history, options)
- **Crown press**: Accept suggestion or confirm selection
- **Long press hardware button**: Voice input (replaces Pebble long SELECT)

### Gesture Implementation
```kotlin
@Composable
fun SessionScreen(viewModel: SessionViewModel) {
    val focusRequester = rememberActiveFocusRequester()

    Box(modifier = Modifier
        .fillMaxSize()
        .onRotaryScrollEvent { event ->
            // Crown rotation -> scroll Claude text or navigate history
            viewModel.onCrownRotate(event.verticalScrollPixels)
            true
        }
        .focusRequester(focusRequester)
        .focusable()
    ) {
        // ... screen content
    }
}
```

---

## 6. CLEAN Protocol Parsing

### Protocol Format
The bridge sends messages as JSON. The `cleanData` field contains structured data that gets serialized on the Pebble side as:
```
CLEAN:userCmd|summary|status|lastTool|suggestion|activeTask|diff
```

**On WearOS, we skip the CLEAN string serialization entirely.** The bridge already sends structured JSON:
```json
{
    "type": "output",
    "content": "summary text...",
    "cleanData": {
        "userCmd": "fix the login bug",
        "summary": "I'll fix the login bug by...",
        "status": "Working...",
        "lastTool": "$ git diff",
        "suggestion": "pebble build",
        "activeTask": "Editing login.js",
        "diff": "+  fixed\n-  broken"
    },
    "prompt": {
        "options": [
            {"num": 1, "label": "Yes"},
            {"num": 2, "label": "Always"},
            {"num": 3, "label": "No"}
        ]
    },
    "suggestion": "pebble build"
}
```

### WearOS Parser
```kotlin
// CleanParser.kt
object CleanParser {

    fun parseMessage(json: JSONObject): BridgeMessage {
        return when (json.getString("type")) {
            "menu" -> {
                val sessions = json.getJSONArray("sessions")
                BridgeMessage.Menu(
                    sessions = (0 until sessions.length()).map { sessions.getString(it) }
                )
            }
            "session_joined", "session_created" -> {
                BridgeMessage.SessionJoined(json.getString("name"))
            }
            "output" -> {
                val cleanData = json.optJSONObject("cleanData")
                val prompt = json.optJSONObject("prompt")?.let { parsePrompt(it) }
                    ?: cleanData?.optJSONObject("prompt")?.let { parsePrompt(it) }

                BridgeMessage.Output(
                    cleanMessage = cleanData?.let { cd ->
                        CleanMessage(
                            userCmd = cd.optString("userCmd", ""),
                            summary = cd.optString("summary", ""),
                            status = cd.optString("status", "Ready"),
                            lastTool = cd.optString("lastTool", ""),
                            suggestion = cd.optString("suggestion",
                                json.optString("suggestion", "")),
                            activeTask = cd.optString("activeTask", ""),
                            diff = cd.optString("diff", ""),
                            prompt = prompt,
                            isQuestion = cd.optString("status") == "QUESTION"
                        )
                    }
                )
            }
            else -> BridgeMessage.Unknown
        }
    }

    private fun parsePrompt(json: JSONObject): PromptData {
        val options = json.getJSONArray("options")
        return PromptData(
            options = (0 until options.length()).map { i ->
                val opt = options.getJSONObject(i)
                PromptOption(
                    num = opt.getInt("num"),
                    label = opt.getString("label")
                )
            },
            isAskUser = json.optBoolean("isAskUser", false),
            question = json.optString("question", "")
        )
    }
}
```

### What Changes vs Pebble

**Pebble** (`inbox_received_callback`, lines 1321-1492):
- Receives CLEAN as a flat string via AppMessage `TERMINAL_DATA` key
- Manual C string parsing with `strchr(remainder, '|')` to split 7 fields
- Copies into fixed-size `char[]` buffers: `s_user_cmd[128]`, `s_claude_summary[1024]`, etc.
- 500ms throttling to prevent crash: drops CLEAN messages that arrive too fast
- Deferred processing via `s_clean_dirty` flag (heavy work postponed to blink_tick timer)

**WearOS**:
- Receives clean JSON directly from WebSocket (no CLEAN string serialization)
- Kotlin data class parsing with `JSONObject` or kotlinx.serialization
- No buffer size limits (just Kotlin strings)
- No throttling needed (WearOS has orders of magnitude more CPU/RAM)
- No deferred processing needed (Compose recomposition is efficient)

### Fallback: CLEAN String Parsing
If for some reason the bridge sends a pre-serialized CLEAN string (e.g., for backward compatibility), here is the Kotlin parser:

```kotlin
fun parseCleanString(raw: String): CleanMessage {
    // Input: "CLEAN:userCmd|summary|status|lastTool|suggestion|activeTask|diff"
    if (!raw.startsWith("CLEAN:")) return CleanMessage()

    val fields = raw.removePrefix("CLEAN:").split("|", limit = 7)
    return CleanMessage(
        userCmd = fields.getOrElse(0) { "" },
        summary = fields.getOrElse(1) { "" },
        status = fields.getOrElse(2) { "Ready" },
        lastTool = fields.getOrElse(3) { "" },
        suggestion = fields.getOrElse(4) { "" },
        activeTask = fields.getOrElse(5) { "" },
        diff = fields.getOrElse(6) { "" }
    )
}
```

---

## 7. Voice Input

### Pebble Dictation (Current)
The Pebble dictation system is complex because of BLE contention (see `watchapp.c` lines 1700-1838):

1. **Pre-dictation** (`select_long_handler` -> `dictation_send_pause`):
   - Set `s_in_dictation = true` (blocks inbox)
   - Send "pause" via AppMessage (with 10 retries at 100ms)
   - Wait 1500ms for BLE to settle

2. **Start dictation** (`start_dictation_deferred`):
   - Cancel ALL 5 timers: cursor, stream, diff_stream, diff_burst, fx
   - Stop ALL 4 PropertyAnimations: claude_scroll, command_marquee, prompt_marquee, status_marquee
   - Log heap bytes free
   - Call `dictation_session_start()` (Pebble native UI on phone)

3. **Dictation callback** (`dictation_callback`):
   - `sanitize_ascii()` - strip all non-ASCII, deaccent French chars
   - Store in `s_dictation_pending[200]`

4. **Post-dictation** (`post_dictation_send` -> `post_dictation_resume`):
   - Show instant feedback: set `s_user_cmd` and `s_claude_summary = "Sending..."`
   - Send `dictation:text` to bridge
   - Wait 1000ms
   - Send "resume" (with 10 retries at 200ms)
   - Set `s_in_dictation = false`
   - Restart blink timer

### WearOS Voice Input (Simplified)

```kotlin
// VoiceInputManager.kt
class VoiceInputManager {

    private val speechRecognizerLauncher: ActivityResultLauncher<Intent>

    fun startVoiceInput(
        onResult: (String) -> Unit,
        onCancelled: () -> Unit
    ) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                     RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Claude...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizerLauncher.launch(intent)
    }
}
```

**In SessionViewModel**:
```kotlin
fun onVoiceInput() {
    // Step 1: Pause bridge (optional on WearOS, but keeps behavior consistent)
    bridgeRepo.sendPause()

    // Step 2: Launch voice recognition
    voiceInputManager.startVoiceInput(
        onResult = { text ->
            // Step 3: Send dictation
            bridgeRepo.sendDictation(text)

            // Step 4: Resume bridge
            bridgeRepo.sendResume()

            // Step 5: Show immediate feedback
            _uiState.update { it.copy(
                cleanMessage = it.cleanMessage?.copy(
                    userCmd = text,
                    summary = "Sending to Claude...",
                    status = "Sending..."
                )
            )}
        },
        onCancelled = {
            bridgeRepo.sendResume()
        }
    )
}
```

### Key Simplifications
| Pebble Complexity | WearOS Equivalent |
|-------------------|-------------------|
| 10-retry pause send | Single WebSocket send (reliable) |
| 1500ms BLE settle wait | Not needed (WiFi is independent) |
| Kill 5 timers + 4 animations | Not needed (Compose lifecycle handles it) |
| `sanitize_ascii()` deaccent | Not needed (full Unicode over WebSocket) |
| 10-retry resume send | Single WebSocket send |
| `s_in_dictation` inbox block | Not needed (no BLE contention) |
| 200-byte transcription limit | No limit |

---

## 8. Vibration/Haptics Mapping

### Pebble Vibrations (from watchapp.c)
| Trigger | Pebble Call | Location |
|---------|-------------|----------|
| New suggestion arrives | `vibes_short_pulse()` | line 1469 |
| New question (STATE_QUESTION) | `vibes_double_pulse()` | line 1478 |
| Ready for 10+ seconds | `vibes_double_pulse()` | line 1256 |
| Button press feedback | `vibes_short_pulse()` | lines 1668, 1674, 1848, etc. |
| Join session | `vibes_double_pulse()` | line 1847 |
| Toggle display mode | `vibes_double_pulse()` | line 1978 |
| Dictation start | `vibes_short_pulse()` | line 2008 |

### WearOS Haptics
```kotlin
// HapticsManager.kt
class HapticsManager(private val context: Context) {
    private val vibrator = context.getSystemService(Vibrator::class.java)

    fun shortPulse() {
        vibrator.vibrate(
            VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    fun doublePulse() {
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 50, 100, 50),  // delay, vib, pause, vib
                intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE),
                -1  // no repeat
            )
        )
    }

    fun confirmPulse() {
        // WearOS-specific rich haptic
        vibrator.vibrate(
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        )
    }

    fun questionPulse() {
        vibrator.vibrate(
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        )
    }
}
```

### Haptic Mapping Table
| Pebble | WearOS | When |
|--------|--------|------|
| `vibes_short_pulse()` | `VibrationEffect.EFFECT_CLICK` | Button press, new suggestion |
| `vibes_double_pulse()` | `VibrationEffect.EFFECT_DOUBLE_CLICK` | New question, Ready 10s, join session |

### Ready-10s Vibration
Pebble uses `blink_tick` at 250ms intervals and counts 40 ticks (10 seconds) of "Ready" status.

WearOS equivalent:
```kotlin
// In SessionViewModel
private var readyTimer: Job? = null

fun onStatusChanged(status: String) {
    readyTimer?.cancel()
    if (status.contains("Ready")) {
        readyTimer = viewModelScope.launch {
            delay(10_000)
            hapticsManager.doublePulse()
        }
    }
}
```

---

## 9. Power Optimization

### Pebble Power Context
Pebble watches last 7 days on a tiny battery because:
- e-paper-like display (no power when static)
- Minimal CPU (sleep between events)
- BLE is low-power
- `light_enable_interaction()` only keeps backlight on briefly

### WearOS Power Challenges
WearOS watches have OLED screens (burn-in risk), WiFi (power-hungry), and full ARM CPUs.

### Optimization Strategies

**1. WiFi Management**
```kotlin
// Only hold WiFi lock when actively connected to a session
private var wifiLock: WifiManager.WifiLock? = null

fun onSessionJoined() {
    wifiLock = wifiManager.createWifiLock(
        WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PebbleCode:Session"
    )
    wifiLock?.acquire()
}

fun onSessionLeft() {
    wifiLock?.release()
    wifiLock = null
}
```

**2. Ambient Mode (Always-On Display)**
```kotlin
// In SessionScreen - ambient mode variant
@Composable
fun SessionScreenAmbient(cleanMessage: CleanMessage) {
    // Minimal display: status only, no animations, dark colors
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            cleanMessage.status,
            color = Color.DarkGray,  // Low brightness to prevent burn-in
            fontSize = 14.sp
        )
        if (cleanMessage.activeTask.isNotEmpty()) {
            Text(
                cleanMessage.activeTask,
                color = Color.DarkGray,
                fontSize = 12.sp
            )
        }
    }
}
```

**3. Update Throttling**
Even though WearOS can handle fast updates, throttle UI updates when the screen is off:
```kotlin
class BridgeRepository {
    private val isScreenOn = MutableStateFlow(true)

    fun processMessage(msg: BridgeMessage) {
        if (isScreenOn.value) {
            // Full update: all fields
            _cleanMessage.value = parseCleanData(msg)
        } else {
            // Screen off: only update status (for ambient mode)
            // Skip summary, diff, tool updates
            _cleanMessage.update { it?.copy(status = msg.status) }
        }
    }
}
```

**4. Animation Management**
- Stop all marquee animations when screen is off
- Disable glitch effects in ambient mode
- Use `repeatOnLifecycle(Lifecycle.State.STARTED)` for all animation coroutines

**5. WebSocket Heartbeat**
Reduce WebSocket keepalive frequency when idle:
```kotlin
val client = OkHttpClient.Builder()
    .pingInterval(30, TimeUnit.SECONDS)  // Reduce from default to save power
    .build()
```

**6. Burn-in Prevention**
- Shift ambient mode content by a few pixels periodically
- No static bright elements in ambient mode
- Use `Modifier.burnInProtection()` from Horologist library

### Expected Battery Impact
- Active session (screen on, WiFi, animations): ~4-6 hours
- Active session (screen mostly off, ambient): ~8-12 hours
- Menu idle (WiFi connected, no polling): ~18-24 hours
- Disconnected (app in background): negligible impact

---

## 10. Bridge Changes

### What Stays (No Changes Needed)
The bridge is 95% unchanged. These components work identically:
- `listSessions()` - tmux session enumeration
- `createSession()` - tmux + claude launch
- `findActiveJsonl()` / `parseRecentJsonl()` / `extractCleanData()` - JSONL parsing
- `extractRealtimeStatus()` / `extractRealtimeTool()` / `extractActiveTask()` - tmux status detection
- `detectPrompt()` - permission prompt detection
- `detectSuggestionFromTmux()` - ghost text detection
- `extractDiffBackdrop()` - diff extraction
- `startPolling()` at 400ms interval
- All message type handlers: `list`, `join`, `create`, `leave`, `accept`, `key`, `dictation`, `pause`, `resume`

### What Gets Removed
These functions exist solely because of Pebble's limitations:

**1. Emoji/Accent stripping in server.js** (lines 72-131):
```javascript
// REMOVE: removeEmojis(), stripAccents(), clean() (emoji parts)
// WearOS supports full Unicode - no stripping needed
```

**2. Hyphenation** (lines 15-67):
```javascript
// REMOVE: hyphenate() - WearOS handles text wrapping natively
```

**3. Color code prefixing** (lines 306-448):
```javascript
// REMOVE: extractClaude() - the entire color-coded VERBOSE format
// WearOS doesn't use single-char color prefixes
```

**4. LINE_W constant** (line 13):
```javascript
// REMOVE: const LINE_W = 23 - Pebble-specific chars-per-line
```

**5. Field truncation in pkjs/index.js** (lines 129-140):
```javascript
// REMOVE: All .substring() truncation for 2048-byte limit
// WearOS has no message size limit
```

### What Gets Modified

**1. cleanData field sizes** - Remove all clamping in server.js (lines 1332-1339):
```javascript
// BEFORE (Pebble):
cleanData.summary = clampTextEnd(cleanData.summary, 500);
cleanData.userCmd = firstLineWithEllipsis(cleanData.userCmd, 160);
cleanData.lastTool = clampText(cleanData.lastTool, 80);

// AFTER (WearOS): Remove all clampText calls
// Or keep them with much larger limits (e.g., 5000 chars for summary)
```

**2. Diff formatting** - Remove color code prefixes:
```javascript
// BEFORE:
const colored = diffLines.map(l => {
    let color = 'L';
    if (l.startsWith('+')) color = 'G';
    // ...
    return color + hyphenate(l, LINE_W);
});

// AFTER: Send raw diff lines, let watch handle styling
const formatted = diffLines.map(l => l);
```

**3. CLEAN string serialization** - Optional: send JSON cleanData directly instead of pipe-delimited string:
```javascript
// The bridge already sends cleanData as JSON in the 'output' message.
// The CLEAN: string serialization in pkjs/index.js is Pebble-specific.
// WearOS reads from msg.cleanData directly (JSON object).
// No changes needed in server.js for this.
```

### Bridge Compatibility Mode
To support both Pebble and WearOS simultaneously, add a client type detection:

```javascript
// server.js - in ws.on('connection')
let clientType = 'pebble'; // default

ws.on('message', (msg) => {
    const data = JSON.parse(msg.toString());

    // WearOS identifies itself on connect
    if (data.type === 'identify') {
        clientType = data.client; // 'wearos' or 'pebble'
        console.log('Client type:', clientType);
        sendMenu();
        return;
    }

    // ... rest of message handling unchanged
});

// In polling, adjust output based on client type
if (clientType === 'wearos') {
    // Send full untruncated data, no emoji stripping
    ws.send(JSON.stringify({
        type: 'output',
        cleanData: cleanData, // Full JSON, no CLEAN: string needed
        prompt: cleanData.prompt || null,
        suggestion: cleanData.suggestion || null
    }));
} else {
    // Pebble path: existing code with truncation + CLEAN string
    // ... existing code unchanged
}
```

### New WearOS Handshake
Add to the WebSocket connection protocol:
```javascript
// WearOS sends on connect:
{ "type": "identify", "client": "wearos", "version": 1 }

// Bridge responds with:
{ "type": "welcome", "bridgeVersion": 1, "features": ["clean", "diff", "question"] }
```

---

## Appendix A: Color Palette Translation

Exact mapping from Pebble GColor to Android Color:

```kotlin
// Colors.kt
object PebbleColors {
    // Pebble dark mode colors (from color_from_code(), watchapp.c lines 190-238)
    val Cyan = Color(0xFF00FFFF)         // GColorCyan (B code)
    val Celeste = Color(0xFFAAFFFF)      // GColorCeleste (C code)
    val Melon = Color(0xFFFF5555)        // GColorMelon (R code)
    val Green = Color(0xFF00FF00)        // GColorGreen (G code)
    val Yellow = Color(0xFFFFFF00)       // GColorYellow (Y code)
    val ChromeYellow = Color(0xFFFFAA00) // GColorChromeYellow (O code)
    val LightGray = Color(0xFFAAAAAA)    // GColorLightGray (L code)
    val Malachite = Color(0xFF00FF55)    // GColorMalachite (vibeface green)
    val Magenta = Color(0xFFFF00FF)      // GColorMagenta (Claude text)

    // Status bar colors (from update_clean_ui_state(), watchapp.c lines 490-530)
    val DarkGray = Color(0xFF555555)     // GColorDarkGray (default status bg)
    val Orange = Color(0xFFFF5500)       // GColorOrange (thinking/prompt)
    val WindsorTan = Color(0xFFAA5500)   // GColorWindsorTan (thinking alt)
    val CobaltBlue = Color(0xFF0055AA)   // GColorCobaltBlue (suggestion mode)
    val IslamicGreen = Color(0xFF00AA00) // GColorIslamicGreen (Ready/Done)
    val Purple = Color(0xFFAA00FF)       // GColorPurple (active task)
    val ImperialPurple = Color(0xFF550055) // GColorImperialPurple (task blink alt)
    val DarkGreen = Color(0xFF005500)    // GColorDarkGreen (question selected)
    val MintGreen = Color(0xFF00FF55)    // GColorMintGreen (menu hints)
    val Icterine = Color(0xFFFFFF55)     // GColorIcterine (menu hints)
}
```

## Appendix B: Message Types Reference

All WebSocket message types between bridge and watch:

### Watch -> Bridge
| Type | Payload | Description |
|------|---------|-------------|
| `identify` | `{ client: "wearos", version: 1 }` | NEW: WearOS handshake |
| `list` | (none) | Request session list |
| `create` | (none) | Create new tmux session |
| `join` | `{ name: "sessionName" }` | Join existing session |
| `leave` | (none) | Leave current session |
| `accept` | (none) | Accept suggestion (Tab + Enter) |
| `key` | `{ content: "1" }` | Send keystroke (prompt answer number) |
| `dictation` | `{ content: "user text" }` | Voice dictation text |
| `pause` | (none) | Pause bridge output (during voice input) |
| `resume` | (none) | Resume bridge output |

### Bridge -> Watch
| Type | Payload | Description |
|------|---------|-------------|
| `welcome` | `{ bridgeVersion, features }` | NEW: Handshake response |
| `menu` | `{ sessions: ["s1","s2"] }` | Session list |
| `session_joined` | `{ name: "sessionName" }` | Joined confirmation |
| `session_created` | `{ name: "sessionName" }` | Created confirmation |
| `output` | `{ content, cleanData, prompt, suggestion }` | Session data update |

## Appendix C: Testing Strategy

### Unit Tests
- `CleanParserTest.kt` - Parse all CLEAN message variants
- `BridgeRepositoryTest.kt` - State transitions (menu -> session -> question)
- `StatusBarColorsTest.kt` - Color logic matches Pebble behavior

### Integration Tests
- WebSocket connection/reconnection with real bridge
- Voice input round-trip (dictation -> bridge -> tmux -> response)
- Prompt detection and response

### Device Testing Matrix
| Device | Shape | Size | Crown |
|--------|-------|------|-------|
| Samsung Galaxy Watch 6 | Round | 396x396 | Yes |
| Samsung Galaxy Watch Ultra | Round | 480x480 | Yes |
| Google Pixel Watch 3 | Round | 384x384 | Yes |
| TicWatch Pro 5 | Round | 466x466 | Yes |

### Emulator Testing
```bash
# Create Wear OS AVD
sdkmanager "system-images;android-34;google_apis;x86_64"
avdmanager create avd -n WearOS34 -k "system-images;android-34;google_apis;x86_64" -d "wearos_large_round"

# Ensure emulator can reach bridge
# Bridge runs on host machine, emulator accesses via 10.0.2.2:8080
# OR: use adb reverse tcp:8080 tcp:8080
```

## Appendix D: Timeline Summary

| Phase | Days | Deliverable |
|-------|------|-------------|
| 0: Scaffolding | 1 | Empty app builds and runs |
| 1: WebSocket | 1 | Watch connects to bridge |
| 2: Menu | 1 | Session list, join/create |
| 3: Session (CLEAN) | 3 | Full CLEAN display with all zones |
| 4: Prompts | 1 | Yes/No/Always quick answers |
| 5: Questions | 1 | AskUserQuestion full screen |
| 6: History | 1 | Swipe through past summaries |
| 7: Voice | 2 | SpeechRecognizer integration |
| 8: Glitch FX | 1 | Visual glitch effects |
| 9: Verbose (optional) | 1 | Streaming colored text |
| 10: Service/AOD | 1 | Foreground service, ambient mode |
| 11: Polish | 3 | Edge cases, testing, settings |
| **Total** | **~17 days** | **Full feature parity** |

## Appendix E: File Mapping (Pebble -> WearOS)

| Pebble File | WearOS Equivalent | Notes |
|-------------|-------------------|-------|
| `watchapp/src/c/watchapp.c` (2218 lines) | Split into ~15 Kotlin files | Decomposed by concern |
| `watchapp/src/pkjs/index.js` (264 lines) | **DELETED** | Phone relay eliminated |
| `bridge/server.js` (1437 lines) | Minor modifications only | Add WearOS client detection |
| `bridge/parse-jsonl.js` (324 lines) | No changes | Bridge-side only |

### Detailed Function -> File Mapping

| watchapp.c Function | WearOS File | Notes |
|---------------------|-------------|-------|
| `draw_menu()` | `MenuScreen.kt` | Compose UI |
| `create_clean_layers()` | `SessionScreen.kt` | Compose Column layout |
| `canvas_update()` MODE_CLEAN | `SessionScreen.kt` | Compose recomposition |
| `canvas_update()` MODE_VERBOSE | `VerboseScreen.kt` | Optional |
| `draw_question()` | `QuestionScreen.kt` | Compose ScalingLazyColumn |
| `update_clean_ui_state()` | `StatusBar.kt` | Color logic in composable |
| `flow_pass()` | `VerboseScreen.kt` | AnnotatedString rendering |
| `start_claude_scroll()` | `ClaudeTextZone.kt` | LazyColumn auto-scroll |
| `start_command_marquee()` | `CommandRow.kt` | Animated horizontal scroll |
| `start_prompt_marquee()` | `PromptRow.kt` | Animated horizontal scroll |
| `start_status_marquee()` | `StatusBar.kt` | Animated horizontal scroll |
| `fx_update()` / `trigger_glitch_*()` | `GlitchOverlay.kt` | Canvas composable |
| `inbox_received_callback()` | `CleanParser.kt` + `BridgeRepository.kt` | JSON parsing + state |
| `send_msg()` / `send_key()` | `WebSocketClient.kt` | Direct WebSocket send |
| `dictation_callback()` + flow | `VoiceInputManager.kt` | SpeechRecognizer |
| `blink_tick()` | `LaunchedEffect` in composables | Coroutine-based timers |
| `click_config_provider()` | Gesture modifiers in composables | Touch + crown + buttons |
| `color_from_code()` | `Colors.kt` | Color palette object |
| `init()` / `deinit()` | `MainActivity.kt` | Compose lifecycle |
| `clean_show_history_index()` | `SessionViewModel.kt` | State management |
| `process_clean_deferred()` | `BridgeRepository.kt` | Reactive state updates |
