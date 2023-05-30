package mega.privacy.android.domain.entity.analytics

/**
 * Tab selected event
 *
 * @property identifier
 * @property viewId
 * @constructor Create empty Tab selected event
 */
class TabSelectedEvent(
    private val identifier: TabSelectedEventIdentifier,
    override val viewId: String,
) : AnalyticsEvent() {
    override val eventTypeIdentifier = 1000
    override val uniqueEventIdentifier: Int
        get() = identifier.uniqueIdentifier

    override fun data(): Map<String, Any?> {
        return mapOf(
            "screen_name" to identifier.screenName,
            "tab_name" to identifier.tabName
        )
    }
}