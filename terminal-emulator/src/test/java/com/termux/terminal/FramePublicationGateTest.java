package com.termux.terminal;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The published transport snapshot aliases a mutable staging buffer, so the gate has to guarantee
 * that no build can start while a publication is in flight to the main thread. That guarantee is
 * what makes an in-window read safe, and it is what {@link GhosttySessionWorker#getPublishedFrameDelta()}
 * relies on to decide whether it may hand the buffer out at all.
 */
public class FramePublicationGateTest {

    private final FramePublicationGate gate = new FramePublicationGate();

    @Test
    public void aPendingUiUpdateBlocksTheNextSnapshotBuild() {
        assertTrue(gate.isSnapshotDirty());
        assertTrue(gate.tryStartSnapshotBuild());
        assertFalse("the dirty flag is consumed by the build", gate.isSnapshotDirty());

        assertTrue(gate.tryScheduleUIUpdate());

        assertFalse("a build must not start while the published buffer is in flight", gate.tryStartSnapshotBuild());
        assertTrue(gate.isUIUpdatePending());
    }

    @Test
    public void theUiUpdateFlagIsHeldAcrossTheWholeNotification() {
        gate.tryStartSnapshotBuild();
        assertTrue(gate.tryScheduleUIUpdate());

        // Stands in for the window between the worker posting and the main thread copying.
        assertTrue(gate.isUIUpdatePending());
        assertFalse(gate.tryStartSnapshotBuild());

        gate.completeUIUpdate();

        assertFalse("the buffer is recyclable again once the copy is done", gate.isUIUpdatePending());

        gate.markSnapshotDirty();
        assertTrue("a build may start again once the copy is done", gate.tryStartSnapshotBuild());
    }

    @Test
    public void onlyOneUiUpdateIsScheduledAtATime() {
        assertTrue(gate.tryScheduleUIUpdate());
        assertFalse("a replay must not queue a second notification", gate.tryScheduleUIUpdate());
    }

    @Test
    public void completingACleanUiUpdateReportsNoPendingWork() {
        gate.tryStartSnapshotBuild();
        gate.tryScheduleUIUpdate();

        assertFalse(gate.completeUIUpdate());
    }

    @Test
    public void workMarkedDirtyDuringTheWindowSurvivesTheUpdate() {
        gate.tryStartSnapshotBuild();
        gate.tryScheduleUIUpdate();

        // Output that arrived while the main thread was still copying.
        gate.markSnapshotDirty();

        assertTrue("dirty work must be coalesced into the next build", gate.completeUIUpdate());
    }
}
