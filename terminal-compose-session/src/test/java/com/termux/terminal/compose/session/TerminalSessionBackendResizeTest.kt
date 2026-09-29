package com.termux.terminal.compose.session

import android.os.Looper
import com.termux.terminal.TerminalSession
import com.termux.terminal.compose.TerminalBackendError
import com.termux.terminal.compose.TerminalBackendListener
import com.termux.terminal.compose.TerminalSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/**
 * A host that stopped owning a backend must not keep resizing the session.
 *
 * A debounced resize queued by an outgoing canvas would otherwise race the resize
 * of whichever host owns the session next and send a SIGWINCH carrying the
 * previous host's geometry.
 */
@RunWith(RobolectricTestRunner::class)
class TerminalSessionBackendResizeTest {

    private val noOpListener = object : TerminalBackendListener {
        override fun onFrameInvalidated() = Unit
        override fun onBackendError(error: TerminalBackendError) = Unit
    }

    @Test
    fun detachDropsPendingDebouncedResize() {
        val backend = TerminalSessionBackend(closedSession(), resizeDebounceMillis = DebounceMillis)

        // The leading edge applies immediately and opens the suppression window,
        // so the second resize is the debounced one.
        backend.resize(size(columns = 80))
        backend.resize(size(columns = 100))
        assertTrue("expected a debounced resize to be queued", backend.hasPendingResize)

        backend.detach()

        assertFalse("a detached host must not keep a queued resize", backend.hasPendingResize)
    }

    @Test
    fun queuedResizeIsNotAppliedAfterDetach() {
        val backend = TerminalSessionBackend(closedSession(), resizeDebounceMillis = DebounceMillis)

        backend.resize(size(columns = 80))
        backend.resize(size(columns = 100))
        backend.detach()

        shadowOf(Looper.getMainLooper()).idleFor(DebounceMillis * 2, TimeUnit.MILLISECONDS)

        assertFalse(backend.hasPendingResize)
    }

    @Test
    fun reattachedHostCanSizeTheSessionAgain() {
        val backend = TerminalSessionBackend(closedSession(), resizeDebounceMillis = DebounceMillis)

        backend.attach(noOpListener)
        backend.detach()
        backend.attach(noOpListener)

        backend.resize(size(columns = 80))
        backend.resize(size(columns = 100))

        assertTrue("a re-attached host must be able to size the session again", backend.hasPendingResize)
        backend.release()
    }

    private fun size(columns: Int) = TerminalSize(
        widthPx = columns * 10,
        heightPx = 480,
        columns = columns,
        rows = 24,
        cellWidthPx = 10,
        cellHeightPx = 20,
        contentTopPx = 0
    )

    /**
     * A closed session ignores [TerminalSession.updateSize], so these tests observe
     * queue bookkeeping instead of native side effects.
     */
    private fun closedSession(): TerminalSession {
        val session = TerminalSession("/bin/sh", "/", arrayOf("sh"), emptyArray(), null, null)
        session.close()
        return session
    }

    private companion object {
        const val DebounceMillis = 10_000L
    }
}
