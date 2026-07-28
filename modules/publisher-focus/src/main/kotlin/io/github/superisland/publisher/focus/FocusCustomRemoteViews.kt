package io.github.superisland.publisher.focus

import android.widget.RemoteViews

/** Module-owned layouts consumed by Xiaomi's custom focus-notification path. */
data class FocusCustomRemoteViews(
    val day: RemoteViews,
    val night: RemoteViews,
    val islandExpanded: RemoteViews,
)
