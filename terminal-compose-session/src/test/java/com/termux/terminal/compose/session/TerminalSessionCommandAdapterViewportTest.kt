package com.termux.terminal.compose.session

import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionIO
import com.termux.terminal.compose.TerminalCommand
import com.termux.terminal.compose.TerminalCommandResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Viewport ownership is a per-host concern.
 *
 * A session can be hosted by more than one canvas at a time (a session tab and a bubble) while the
 * session itself holds a single worker-owned viewport, so following output has to be toggled per
 * host rather than per session.
 */
class TerminalSessionCommandAdapterViewportTest {

    @Test
    fun toggleAutoScrollReachesTheHostThatSubmittedIt() {
        var toggles = 0
        val adapter = adapterFor(onAutoScrollToggle = { toggles++ })

        val result = adapter.submit(TerminalCommand.ToggleAutoScroll)

        assertEquals(TerminalCommandResult.Success, result)
        assertEquals(1, toggles)
    }

    @Test
    fun toggleAutoScrollDoesNotTouchAnotherHost() {
        var tabToggles = 0
        var bubbleToggles = 0
        val tab = adapterFor(onAutoScrollToggle = { tabToggles++ })
        adapterFor(onAutoScrollToggle = { bubbleToggles++ })

        tab.submit(TerminalCommand.ToggleAutoScroll)

        assertEquals(1, tabToggles)
        assertEquals(0, bubbleToggles)
    }

    private fun adapterFor(
        onAutoScrollToggle: () -> Unit = {},
        onViewportUpdate: () -> Unit = {}
    ) = TerminalSessionCommandAdapter(
        session = TerminalSession(4096, null, NoOpIo()),
        updateTopRow = { onViewportUpdate() },
        toggleAutoScroll = onAutoScrollToggle,
        submitScrollEvent = {}
    )

    private class NoOpIo : TerminalSessionIO {
        override fun write(data: ByteArray?, offset: Int, count: Int) = Unit

        override fun onResize(columns: Int, rows: Int, cellWidth: Int, cellHeight: Int) = Unit

        override fun onClose() = Unit
    }
}
