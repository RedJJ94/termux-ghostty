package com.mrndtvndv.term

/**
 * Tiny in-memory breadcrumb trail for crash reports.
 *
 * The layout/draw-phase crashes Compose can throw (e.g. "LayoutNode should be
 * attached to an owner") carry no app state, so the report alone cannot tell
 * which screen or tab configuration triggered them. Composables and ViewModels
 * record their last known state here; [CrashReporter] snapshots it into the report.
 *
 * Besides the pager position, the trail records screen transitions
 * (workspace vs. server list) and workspace-sync outcomes, so a report can
 * discriminate a crash during navigation or an SFTP directory overwrite from
 * one inside a settled tab.
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
    private var workspaceCurrentPage: Int = -1

    @Volatile
    private var workspaceTabCount: Int = -1

    @Volatile
    private var screen: String = "unknown"

    @Volatile
    private var workEvent: String = "none"

    fun setWorkspace(tab: String, page: Int, currentPage: Int, tabCount: Int) {
        workspaceTab = tab
        workspacePage = page
        workspaceCurrentPage = currentPage
        workspaceTabCount = tabCount
    }

    fun setScreen(screen: String) {
        this.screen = screen
    }

    fun setWorkEvent(event: String) {
        workEvent = event
    }

    fun snapshot(): String =
        "screen=$screen workspaceTab=$workspaceTab workspacePage=$workspacePage " +
            "currentPage=$workspaceCurrentPage tabCount=$workspaceTabCount work=$workEvent"
}
