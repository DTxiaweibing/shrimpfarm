package com.shrimpfarm.app.backup;

import static org.junit.Assert.*;
import org.junit.Test;

public class V17BackupHookTest {
    @Test public void onlyPreV17NeedsBackup() {
        assertTrue(V17BackupHook.needsBackup(15));
        assertTrue(V17BackupHook.needsBackup(16));
        assertFalse(V17BackupHook.needsBackup(17));
    }
    @Test public void sevenDayRetentionBoundary() {
        long day = 24L * 3600 * 1000;
        assertTrue(V17BackupHook.isExpired(0, 8 * day, 7 * day));
        assertFalse(V17BackupHook.isExpired(2 * day, 8 * day, 7 * day));
    }
}
