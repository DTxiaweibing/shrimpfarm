package com.shrimpfarm.app.backup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DatabaseSnapshotGateTest {

    @Test
    public void busyCheckpointMustNotCopyDatabaseFile() {
        assertFalse(DatabaseSnapshotGate.allowSnapshot(1, 100, 0));
    }

    @Test
    public void completeCheckpointAllowsCopy() {
        assertTrue(DatabaseSnapshotGate.allowSnapshot(0, 100, 100));
    }

    @Test
    public void partialCheckpointMustNotCopyDatabaseFile() {
        assertFalse(DatabaseSnapshotGate.allowSnapshot(0, 100, 70));
    }

    @Test
    public void nonWalDatabaseHasNothingToCheckpointButIsSafeToCopy() {
        assertTrue(DatabaseSnapshotGate.allowSnapshot(0, -1, -1));
    }

    @Test
    public void unknownBusyStateIsRejected() {
        assertFalse(DatabaseSnapshotGate.allowSnapshot(-1, -1, -1));
    }
}
