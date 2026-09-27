package com.mrndtvndv.term.server

internal object HerdrNotificationMatcher {
    private val ParenSessionPattern = Regex("""\(([0-9a-fA-F-]{6,64})\)""")
    private val TokenSessionPattern = Regex("""\b[0-9a-fA-F-]{6,64}\b""")
    private val AgentKeywords = listOf(
        "agent", "agy", "codex", "hermes", "pi", "claude",
        "task completed", "finished task", "needs attention",
    )

    fun looksLikeHerdrNotification(body: String?, title: String?): Boolean {
        val text = listOfNotNull(title, body).joinToString(" ")
        if (text.isBlank()) return false
        if (text.contains(" · ") || ParenSessionPattern.containsMatchIn(text)) return true

        val lower = text.lowercase()
        return AgentKeywords.any { lower.contains(it) }
    }

    fun extractSessionCandidates(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val candidates = mutableListOf<String>()

        ParenSessionPattern.findAll(text).forEach { match ->
            val inside = match.groupValues[1].trim()
            if (inside.isNotBlank()) candidates.add(inside)
        }

        TokenSessionPattern.findAll(text).forEach { match ->
            val token = match.value.trim()
            if (token.isNotBlank() && !candidates.contains(token)) {
                candidates.add(token)
            }
        }

        return candidates
    }

    fun findAgentBySessionToken(
        agents: List<HerdrWorkspaceResolver.HerdrAgentInfo>,
        body: String?,
        title: String?,
    ): HerdrWorkspaceResolver.HerdrAgentInfo? {
        val candidates = extractSessionCandidates(body) + extractSessionCandidates(title)
        if (agents.isEmpty() || candidates.isEmpty()) return null

        return candidates.firstNotNullOfOrNull { candidate ->
            agents.firstOrNull { agent -> agentMatchesSessionCandidate(agent, candidate) }
        }
    }

    private fun agentMatchesSessionCandidate(
        agent: HerdrWorkspaceResolver.HerdrAgentInfo,
        candidate: String,
    ): Boolean {
        val sessionVal = agent.agentSession?.value
        if (sessionVal != null && sessionMatches(sessionVal, candidate)) {
            return true
        }
        return agent.terminalTitle?.contains(candidate, ignoreCase = true) == true ||
            agent.name?.equals(candidate, ignoreCase = true) == true
    }

    private fun sessionMatches(sessionVal: String, candidate: String): Boolean = when {
        sessionVal.equals(candidate, ignoreCase = true) -> true
        sessionVal.startsWith(candidate, ignoreCase = true) -> true
        candidate.startsWith(sessionVal, ignoreCase = true) -> true
        else -> sessionVal.contains(candidate, ignoreCase = true)
    }

    fun findWorkspaceMatch(
        workspaces: List<HerdrWorkspaceResolver.WorkspaceEntry>,
        workspaceLabels: Map<String, String>,
        body: String?,
        title: String?,
    ): HerdrWorkspaceResolver.WorkspaceEntry? = workspaces.firstOrNull { ws ->
        val label = workspaceLabels[ws.workspaceId] ?: return@firstOrNull false
        (body?.contains(label, ignoreCase = true) == true) ||
            (title?.contains(label, ignoreCase = true) == true)
    }

    fun isAgentCompletion(title: String?, body: String?): Boolean {
        val text = listOfNotNull(title, body).joinToString(" ").lowercase()
        return text.contains("agent done") ||
            text.contains("task completed") ||
            text.contains("finished task") ||
            text.contains("needs attention")
    }
}
