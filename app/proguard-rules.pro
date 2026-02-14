# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep WebSocket listener
-keep class com.vibecoder.pebblecode.service.BridgeService { *; }

# JSON
-keep class org.json.** { *; }
