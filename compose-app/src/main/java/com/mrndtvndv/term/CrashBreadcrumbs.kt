package com.mrndtvndv.term

/**
 * Tiny in-memory breadcrumb trail for crash reports.
 *
 * The layout/draw-phase crashes Compose can throw (e.g. "LayoutNode should be
 * attached to an owner") carry no app state, so the report alone cannot tell
 * which screen or tab configuration triggered them. Composables record their
 * last known state here; [CrashReporter] snapshots it into the report.
 *
 * Plain `@Volatile` fields only — safe to write from any thread and to read
 * on the crashing thread inside the uncaught-exception handler.
 */
object CrashBreadcrumbs {

    @Volatile
    private var workspaceTab: String = "unknown"

    @Volatile
    private var workspacePage: Int = -1

    @Volatile
    private var workspaceTabCount: Int = -1

    fun setWorkspace(tab: String, page: Int, tabCount: Int) {
        workspaceTab = tab
        workspacePage = page
        workspaceTabCount = tabCount
    }

    fun snapshot(): String =
        "workspaceTab=$workspaceTab workspacePage=$workspacePage workspaceTabCount=$workspaceTabCount"
}
