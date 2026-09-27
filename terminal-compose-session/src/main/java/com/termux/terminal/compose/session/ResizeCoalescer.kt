package com.termux.terminal.compose.session

/**
 * Leading-edge coalescing policy for terminal viewport resizes.
 *
 * Soft-keyboard show/hide animations deliver dozens of viewport sizes within a few
 * hundred milliseconds; reflowing the terminal backend for every frame stalls
 * composition while the pager and insets are also placing. The first resize while
 * idle applies immediately so attach and rotation stay synchronous; bursts arriving
 * within [debounceMillis] collapse to a single trailing apply. A zero window
 * disables coalescing (every resize applies immediately).
 *
 * Pure decision policy: the backend owns Handler scheduling, this class only
 * decides now-vs-later against an injectable clock so the policy is unit-testable
 * without Android.
 */
internal class ResizeCoalescer(
    debounceMillis: Long,
    private val clock: () -> Long,
) {
    internal var debounceMillis: Long = debounceMillis.coerceAtLeast(0L)
        private set

    private var lastApplyMillis: Long? = null

    fun setDebounceMillis(millis: Long) {
        debounceMillis = millis.coerceAtLeast(0L)
    }

    /** Whether a resize arriving now may apply immediately. */
    fun shouldApplyNow(): Boolean {
        if (debounceMillis == 0L) return true
        val last = lastApplyMillis ?: return true
        return clock() - last >= debounceMillis
    }

    /** Records that a resize was applied; starts the suppression window. */
    fun onApplied() {
        lastApplyMillis = clock()
    }
}
