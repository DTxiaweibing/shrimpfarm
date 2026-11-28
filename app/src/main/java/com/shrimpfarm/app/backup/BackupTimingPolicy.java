package com.shrimpfarm.app.backup;

/** 纯策略：自动备份的双时间窗。成功后等一个长周期，失败/跳过后只等一个短冷却。 */
public final class BackupTimingPolicy {

    private BackupTimingPolicy() {
    }

    public static boolean shouldRun(long now, long lastSuccess, long lastAttempt,
                                    long successIntervalMs, long retryCooldownMs) {
        if (now - lastSuccess < successIntervalMs) return false;
        return now - lastAttempt >= retryCooldownMs;
    }
}
