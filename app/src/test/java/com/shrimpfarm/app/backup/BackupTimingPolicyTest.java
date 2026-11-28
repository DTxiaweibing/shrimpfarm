package com.shrimpfarm.app.backup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackupTimingPolicyTest {

    private static final long TWO_HOURS = 2 * 60 * 60 * 1000L;
    private static final long TEN_MINUTES = 10 * 60 * 1000L;
    private static final long NOW = 1_000_000_000L;

    @Test
    public void firstRunOnFreshInstallIsAllowed() {
        assertTrue(BackupTimingPolicy.shouldRun(NOW, 0, 0, TWO_HOURS, TEN_MINUTES));
    }

    @Test
    public void twoHoursAfterSuccessIsAllowed() {
        assertTrue(BackupTimingPolicy.shouldRun(NOW, NOW - TWO_HOURS, NOW - TWO_HOURS, TWO_HOURS, TEN_MINUTES));
    }

    @Test
    public void lessThanTwoHoursAfterSuccessIsRejected() {
        assertFalse(BackupTimingPolicy.shouldRun(NOW, NOW - TWO_HOURS + 1, 0, TWO_HOURS, TEN_MINUTES));
    }

    @Test
    public void retryCooldownAfterFailedAttemptIsRejected() {
        assertFalse(BackupTimingPolicy.shouldRun(NOW, 0, NOW - TEN_MINUTES + 1, TWO_HOURS, TEN_MINUTES));
    }

    @Test
    public void retryCooldownElapsedIsAllowed() {
        assertTrue(BackupTimingPolicy.shouldRun(NOW, 0, NOW - TEN_MINUTES, TWO_HOURS, TEN_MINUTES));
    }
}
