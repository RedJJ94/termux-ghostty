package com.mrndtvndv.term.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.mrndtvndv.term.server.TerminalProgress
import com.termux.terminal.TerminalSession

private val TerminalProgressHeight = 4.dp

/**
 * Renders terminal progress above, never over, terminal cells.
 *
 * The strip always reserves its height so progress appearing or clearing never
 * resizes the terminal column. Resizing the adjacent terminal surface while the
 * workspace HorizontalPager is placing pages replaces layout nodes mid-placement
 * ("LayoutNode should be attached to an owner" during dispatchDraw); keeping the
 * layout stable reduces progress updates to inner draw changes only.
 */
@Composable
fun TerminalProgressStrip(
    progress: TerminalProgress?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TerminalProgressHeight),
    ) {
        if (progress != null) {
            TerminalProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TerminalProgressIndicator(
    progress: TerminalProgress,
    modifier: Modifier = Modifier,
) {

    val isError = progress.state == TerminalSession.GHOSTTY_PROGRESS_STATE_ERROR
    val isPaused = progress.state == TerminalSession.GHOSTTY_PROGRESS_STATE_PAUSE
    val baseModifier = modifier
        .fillMaxWidth()
        .height(TerminalProgressHeight)
    val progressModifier = when {
        isError -> baseModifier.semantics { stateDescription = "Error" }
        isPaused -> baseModifier.semantics { stateDescription = "Paused" }
        else -> baseModifier
    }
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    when {
        isPaused -> LinearProgressIndicator(
            progress = { (progress.value ?: 0).toFloat() / 100f },
            modifier = progressModifier,
            color = color,
        )

        progress.value != null -> LinearProgressIndicator(
            progress = { progress.value.toFloat() / 100f },
            modifier = progressModifier,
            color = color,
        )

        else -> LinearProgressIndicator(
            modifier = progressModifier,
            color = color,
        )
    }
}
