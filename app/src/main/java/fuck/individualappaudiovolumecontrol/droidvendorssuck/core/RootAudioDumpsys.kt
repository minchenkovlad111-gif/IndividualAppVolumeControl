package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.os.SystemClock

object RootAudioDumpsys {
    data class RootActiveEntry(
        val packageName: String,
        val uid: Int,
        val sessionIds: Set<Int>,
    )

    fun getActiveEntries(
        diagnosticsStore: DiagnosticsStore? = null,
        force: Boolean = false,
    ): List<RootActiveEntry> {
        if (!RootShell.isAvailable()) return emptyList()
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastFetchAt < CACHE_TTL_MS) {
            return lastResult
        }
        val policy = RootShell.run("dumpsys media.audio_policy", timeoutMs = 2500L)
        val flinger = RootShell.run("dumpsys media.audio_flinger", timeoutMs = 2500L)
        if (policy.exitCode != 0 && flinger.exitCode != 0) {
            diagnosticsStore?.recordLastError(
                "Root dumpsys failed: policy=${policy.exitCode} flinger=${flinger.exitCode}"
            )
            return lastResult
        }

        val activeSessions = mutableListOf<ActiveSession>()
        if (policy.stdout.isNotBlank()) {
            activeSessions.addAll(parsePolicyActiveSessions(policy.stdout))
        }
        if (flinger.stdout.isNotBlank()) {
            activeSessions.addAll(parseFlingerActiveSessions(flinger.stdout))
        }

        val uidToPackage = mutableMapOf<Int, String>()
        if (policy.stdout.isNotBlank()) {
            uidToPackage.putAll(parsePolicyUidPackages(policy.stdout))
        }
        if (uidToPackage.isEmpty() && flinger.stdout.isNotBlank()) {
            uidToPackage.putAll(parseFlingerUidPackages(flinger.stdout))
        }

        val grouped = activeSessions.groupBy { it.uid }
        val results = grouped.mapNotNull { (uid, sessions) ->
            val packageName = uidToPackage[uid] ?: return@mapNotNull null
            RootActiveEntry(
                packageName = packageName,
                uid = uid,
                sessionIds = sessions.map { it.sessionId }.toSet(),
            )
        }
        lastFetchAt = now
        lastResult = results
        return results
    }

    private data class ActiveSession(
        val uid: Int,
        val sessionId: Int,
    )

    private fun parsePolicyActiveSessions(text: String): List<ActiveSession> {
        val results = mutableListOf<ActiveSession>()
        for (match in POLICY_ACTIVE_REGEX.findAll(text)) {
            val sessionId = match.groupValues[1].toIntOrNull() ?: continue
            val uid = match.groupValues[2].toIntOrNull() ?: continue
            val state = match.groupValues[3]
            if (!state.equals("active", ignoreCase = true)) continue
            results.add(ActiveSession(uid = uid, sessionId = sessionId))
        }
        return results
    }

    private fun parsePolicyUidPackages(text: String): Map<Int, String> {
        val results = mutableMapOf<Int, String>()
        for (match in POLICY_UID_REGEX.findAll(text)) {
            val uid = match.groupValues[1].toIntOrNull() ?: continue
            val packageName = match.groupValues[2]
            results[uid] = packageName
        }
        return results
    }

    private fun parseFlingerUidPackages(text: String): Map<Int, String> {
        val results = mutableMapOf<Int, String>()
        for (match in FLINGER_UID_REGEX.findAll(text)) {
            val uid = match.groupValues[1].toIntOrNull() ?: continue
            val packageName = match.groupValues[2]
            results[uid] = packageName
        }
        return results
    }

    private fun parseFlingerActiveSessions(text: String): List<ActiveSession> {
        val results = mutableListOf<ActiveSession>()
        for (match in FLINGER_ACTIVE_REGEX.findAll(text)) {
            val uid = match.groupValues[2].toIntOrNull() ?: continue
            val sessionId = match.groupValues[3].toIntOrNull() ?: continue
            results.add(ActiveSession(uid = uid, sessionId = sessionId))
        }
        return results
    }

    // Root dumpsys is expensive enough that we should reuse recent results
    // across UI refreshes and short overlay auto-refresh windows.
    private const val CACHE_TTL_MS = 5000L
    private var lastFetchAt: Long = 0L
    private var lastResult: List<RootActiveEntry> = emptyList()

    private val POLICY_ACTIVE_REGEX = Regex(
        "Session ID:\\s*(\\d+);\\s*uid\\s*(\\d+);\\s*State:\\s*(\\w+)",
        RegexOption.IGNORE_CASE
    )
    private val POLICY_UID_REGEX = Regex(
        "uid=(\\d+).*?packageName=([\\w\\._]+)",
        RegexOption.IGNORE_CASE
    )
    private val FLINGER_UID_REGEX = Regex(
        "^\\s*\\d+\\s+(\\d+)\\s+([\\w\\._]+)\\s*$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    private val FLINGER_ACTIVE_REGEX = Regex(
        "\\s(\\d+)/(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+A\\b",
        RegexOption.IGNORE_CASE
    )
}
