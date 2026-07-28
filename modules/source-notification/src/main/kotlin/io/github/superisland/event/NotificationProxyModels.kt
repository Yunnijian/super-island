package io.github.superisland.event

data class NotificationProxyRule(
    val packageName: String,
    val channelId: String,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(channelId.isNotBlank()) { "channelId must not be blank" }
    }
}

enum class NotificationProxyPrivacyMode {
    INHERIT_SOURCE,
    HIDE_CONTENT,
    HIDE_ON_LOCK_SCREEN,
    ;

    companion object {
        fun fromStored(value: String?): NotificationProxyPrivacyMode =
            entries.firstOrNull { it.name == value } ?: INHERIT_SOURCE
    }
}

data class NotificationProxyPrivacyPolicy(
    val hideSourceContent: Boolean,
    val forceSecretVisibility: Boolean,
)

val NotificationProxyPrivacyMode.policy: NotificationProxyPrivacyPolicy
    get() =
        when (this) {
            NotificationProxyPrivacyMode.INHERIT_SOURCE ->
                NotificationProxyPrivacyPolicy(
                    hideSourceContent = false,
                    forceSecretVisibility = false,
                )
            NotificationProxyPrivacyMode.HIDE_CONTENT ->
                NotificationProxyPrivacyPolicy(
                    hideSourceContent = true,
                    forceSecretVisibility = false,
                )
            NotificationProxyPrivacyMode.HIDE_ON_LOCK_SCREEN ->
                NotificationProxyPrivacyPolicy(
                    hideSourceContent = true,
                    forceSecretVisibility = true,
                )
        }

data class NotificationCandidate(
    val eventId: String,
    val sourceInstance: Long,
    val packageName: String,
    val channelId: String,
    val title: String,
    val text: String,
    val shortStatus: String?,
    val ongoing: Boolean,
    val progress: Int,
    val progressMax: Int,
    val progressIndeterminate: Boolean,
    val systemApp: Boolean,
    val groupSummary: Boolean,
    val bubble: Boolean,
    val fullScreenIntent: Boolean,
    val customViews: Boolean,
    val remoteInput: Boolean,
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(channelId.isNotBlank()) { "channelId must not be blank" }
        require(progress >= 0) { "progress must not be negative" }
        require(progressMax >= 0) { "progressMax must not be negative" }
    }

    val hasProgress: Boolean
        get() = progressMax > 0 || progressIndeterminate
}

enum class NotificationRejectionReason {
    RULE_DISABLED,
    SELF_NOTIFICATION,
    SYSTEM_APP,
    PACKAGE_NOT_ALLOWED,
    CHANNEL_NOT_ALLOWED,
    NOT_ONGOING,
    GROUP_SUMMARY,
    BUBBLE,
    FULL_SCREEN_INTENT,
    CUSTOM_VIEWS,
    REMOTE_INPUT,
}

sealed interface NotificationRuleDecision {
    data object Accepted : NotificationRuleDecision

    data class Rejected(val reason: NotificationRejectionReason) : NotificationRuleDecision
}

object NotificationRuleEvaluator {
    fun evaluate(
        rule: NotificationProxyRule?,
        candidate: NotificationCandidate,
        selfPackageName: String,
    ): NotificationRuleDecision =
        evaluate(
            rules = rule?.let(::listOf).orEmpty(),
            candidate = candidate,
            selfPackageName = selfPackageName,
        )

    fun evaluate(
        rules: Collection<NotificationProxyRule>,
        candidate: NotificationCandidate,
        selfPackageName: String,
    ): NotificationRuleDecision {
        val structuralReason = structuralRejection(candidate, selfPackageName)
        if (structuralReason != null) return NotificationRuleDecision.Rejected(structuralReason)
        if (rules.isEmpty()) return NotificationRuleDecision.Rejected(NotificationRejectionReason.RULE_DISABLED)
        val packageRules = rules.filter { it.packageName == candidate.packageName }
        if (packageRules.isEmpty()) {
            return NotificationRuleDecision.Rejected(NotificationRejectionReason.PACKAGE_NOT_ALLOWED)
        }
        if (packageRules.none { it.channelId == candidate.channelId }) {
            return NotificationRuleDecision.Rejected(NotificationRejectionReason.CHANNEL_NOT_ALLOWED)
        }
        return NotificationRuleDecision.Accepted
    }

    fun structuralRejection(
        candidate: NotificationCandidate,
        selfPackageName: String,
    ): NotificationRejectionReason? =
        when {
            candidate.packageName == selfPackageName -> NotificationRejectionReason.SELF_NOTIFICATION
            candidate.systemApp -> NotificationRejectionReason.SYSTEM_APP
            !candidate.ongoing && !candidate.hasProgress -> NotificationRejectionReason.NOT_ONGOING
            candidate.groupSummary -> NotificationRejectionReason.GROUP_SUMMARY
            candidate.bubble -> NotificationRejectionReason.BUBBLE
            candidate.fullScreenIntent -> NotificationRejectionReason.FULL_SCREEN_INTENT
            candidate.customViews -> NotificationRejectionReason.CUSTOM_VIEWS
            candidate.remoteInput -> NotificationRejectionReason.REMOTE_INPUT
            else -> null
        }
}

data class ProxySession(
    val eventId: String,
    val sourceInstance: Long,
    val generation: Long,
    val notificationId: Int,
)

sealed interface ProxyLifecycleCommand {
    data class Publish(
        val session: ProxySession,
        val update: Boolean,
    ) : ProxyLifecycleCommand

    data class Cancel(val session: ProxySession) : ProxyLifecycleCommand

    data object None : ProxyLifecycleCommand
}

class NotificationProxyLifecycle(
    private val idAllocator: NotificationIdAllocator = NotificationIdAllocator(),
) {
    private val sessions = mutableMapOf<String, ProxySession>()
    private val lastGeneration = mutableMapOf<String, Long>()

    @Synchronized
    fun onAccepted(eventId: String, sourceInstance: Long): ProxyLifecycleCommand.Publish {
        val current = sessions[eventId]
        if (current != null && current.sourceInstance == sourceInstance) {
            return ProxyLifecycleCommand.Publish(current, update = true)
        }

        val generation = (lastGeneration[eventId] ?: 0L) + 1L
        val session =
            ProxySession(
                eventId = eventId,
                sourceInstance = sourceInstance,
                generation = generation,
                notificationId = current?.notificationId ?: idAllocator.allocate(eventId),
            )
        lastGeneration[eventId] = generation
        sessions[eventId] = session
        return ProxyLifecycleCommand.Publish(session, update = current != null)
    }

    @Synchronized
    fun onRemoved(eventId: String, sourceInstance: Long): ProxyLifecycleCommand {
        val current = sessions[eventId] ?: return ProxyLifecycleCommand.None
        if (current.sourceInstance != sourceInstance) return ProxyLifecycleCommand.None

        sessions.remove(eventId)
        idAllocator.release(eventId)
        return ProxyLifecycleCommand.Cancel(current)
    }

    @Synchronized
    fun clear(): List<ProxySession> {
        val removed = sessions.values.toList()
        sessions.keys.forEach(idAllocator::release)
        sessions.clear()
        return removed
    }

    @Synchronized
    fun activeCount(): Int = sessions.size
}

class NotificationCallbackDeduplicator {
    private val sourceInstances = mutableMapOf<String, Long>()

    @Synchronized
    fun shouldProcess(eventId: String, sourceInstance: Long): Boolean {
        if (sourceInstances[eventId] == sourceInstance) return false
        sourceInstances[eventId] = sourceInstance
        return true
    }

    @Synchronized
    fun onRemoved(eventId: String, sourceInstance: Long) {
        if (sourceInstances[eventId] == sourceInstance) {
            sourceInstances.remove(eventId)
        }
    }

    @Synchronized
    fun clear() {
        sourceInstances.clear()
    }
}

class NotificationIdAllocator(
    private val hash: (String) -> Int = String::hashCode,
    private val idPrefix: Int = DEFAULT_ID_PREFIX,
) {
    private val idsByEvent = mutableMapOf<String, Int>()
    private val eventsById = mutableMapOf<Int, String>()

    init {
        require(idPrefix and ID_MASK == 0) { "idPrefix must only use prefix bits" }
    }

    @Synchronized
    fun allocate(eventId: String): Int {
        idsByEvent[eventId]?.let { return it }

        var candidate = idPrefix or (hash(eventId) and ID_MASK)
        while (eventsById[candidate]?.let { it != eventId } == true) {
            candidate = idPrefix or ((candidate + 1) and ID_MASK)
        }
        idsByEvent[eventId] = candidate
        eventsById[candidate] = eventId
        return candidate
    }

    @Synchronized
    fun release(eventId: String) {
        idsByEvent.remove(eventId)?.let(eventsById::remove)
    }

    companion object {
        const val DEFAULT_ID_PREFIX = 0x20000000
        const val MEDIA_ID_PREFIX = 0x30000000

        private const val ID_MASK = 0x0fffffff
    }
}
