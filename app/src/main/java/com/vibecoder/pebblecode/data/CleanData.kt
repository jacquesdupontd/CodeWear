package com.vibecoder.pebblecode.data

data class CleanData(
    val userCmd: String = "",
    val summary: String = "",
    val status: String = "Ready",
    val lastTool: String = "",
    val suggestion: String = "",
    val activeTask: String = "",
    val diff: String = ""
) {
    val isQuestion: Boolean get() = status == "QUESTION"
    val isReady: Boolean get() = status == "Ready"
    val isDone: Boolean get() = status.equals("Done", ignoreCase = true)
    /** True when Claude is NOT actively working */
    val isIdle: Boolean get() = isReady || isDone || status.isEmpty()

    val questionText: String get() {
        if (!isQuestion || !summary.startsWith("?")) return summary
        val nl = summary.indexOf('\n')
        return if (nl > 0) summary.substring(1, nl) else summary.substring(1)
    }

    val questionOptions: List<String> get() {
        if (!isQuestion) return emptyList()
        return summary.split('\n')
            .filter { it.startsWith("OPT:") }
            .map { it.removePrefix("OPT:") }
    }

    companion object {
        /** Parse CLEAN:field1|field2|... pipe-delimited string (fallback) */
        fun fromCleanString(raw: String): CleanData {
            if (!raw.startsWith("CLEAN:")) return CleanData()
            val parts = raw.removePrefix("CLEAN:").split("|", limit = 7)
            return CleanData(
                userCmd = parts.getOrElse(0) { "" },
                summary = parts.getOrElse(1) { "" },
                status = parts.getOrElse(2) { "Ready" },
                lastTool = parts.getOrElse(3) { "" },
                suggestion = parts.getOrElse(4) { "" },
                activeTask = parts.getOrElse(5) { "" },
                diff = parts.getOrElse(6) { "" }
            )
        }

        /** Parse JSON cleanData from bridge directly */
        fun fromJson(json: org.json.JSONObject): CleanData {
            return CleanData(
                userCmd = json.optString("userCmd", ""),
                summary = json.optString("summary", ""),
                status = json.optString("status", "Ready"),
                lastTool = json.optString("lastTool", ""),
                suggestion = json.optString("suggestion", ""),
                activeTask = json.optString("activeTask", ""),
                diff = json.optString("diff", "")
            )
        }
    }
}
