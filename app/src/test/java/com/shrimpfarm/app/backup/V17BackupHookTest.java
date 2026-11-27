package com.shrimpfarm.app.backup;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

public class V17BackupHookTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

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

    @Test public void backupIfNeededCopiesPreV17AndCleansExpired() throws Exception {
        File db = tmp.newFile("FeedingRecord.db");
        try (FileOutputStream out = new FileOutputStream(db)) { out.write(new byte[]{1, 2, 3}); }
        File dir = tmp.newFolder("pre_v17");
        File old = new File(dir, "v16_old.db");
        try (FileOutputStream out = new FileOutputStream(old)) { out.write(new byte[]{9}); }
        old.setLastModified(0L);
        long now = 8L * 24 * 3600 * 1000;

        File backup = V17BackupHook.backupIfNeeded(db, 16, dir, now);

        assertNotNull(backup);
        assertTrue(backup.getName().matches("v16_\\d{8}_\\d{6}\\.db"));
        assertTrue(backup.exists());
        assertEquals(3L, backup.length());
        assertFalse(old.exists());
    }

    @Test public void backupIfNeededSkipsCurrentVersion() throws Exception {
        File db = tmp.newFile("FeedingRecord.db");
        File dir = tmp.newFolder("pre_v17");

        File backup = V17BackupHook.backupIfNeeded(db, 17, dir, System.currentTimeMillis());

        assertNull(backup);
        assertEquals(0, dir.listFiles().length);
    }

    @Test public void safeBackupIfNeededSwallowsCopyFailure() throws Exception {
        File missingDb = new File(tmp.getRoot(), "missing.db");
        File dir = tmp.newFolder("pre_v17");

        boolean backedUp = V17BackupHook.safeBackupIfNeeded(missingDb, 16, dir, 8L * 24 * 3600 * 1000);

        assertFalse(backedUp);
        assertEquals(0, dir.listFiles().length);
    }
}
