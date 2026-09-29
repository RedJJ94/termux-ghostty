package com.termux.app.terminal;

import org.junit.Assert;
import org.junit.Test;

/**
 * When the displayed session leaves the service, focus has to land on the session
 * that took its place. Picking the wrong index makes the terminal show a different
 * session than the one the user dismissed.
 */
public class TermuxTerminalSessionActivityClientRemovalTest {

    @Test
    public void replacementIsTheSessionThatShiftedIntoTheRemovedSlot() {
        // First of three removed: the old second session is now first.
        Assert.assertEquals(0, TermuxTerminalSessionActivityClient.resolveReplacementIndex(0, 2));
        // Middle removed: the old third session is now at the same index.
        Assert.assertEquals(1, TermuxTerminalSessionActivityClient.resolveReplacementIndex(1, 2));
    }

    @Test
    public void replacementClampsToTheLastSession() {
        // Last removed: the index is now out of range.
        Assert.assertEquals(1, TermuxTerminalSessionActivityClient.resolveReplacementIndex(2, 2));
    }

    @Test
    public void nothingToDisplayWhenTheListIsEmpty() {
        Assert.assertEquals(-1, TermuxTerminalSessionActivityClient.resolveReplacementIndex(0, 0));
    }

    @Test
    public void noReplacementWhenTheServiceNeverOwnedTheSession() {
        Assert.assertEquals(-1, TermuxTerminalSessionActivityClient.resolveReplacementIndex(-1, 3));
    }
}
