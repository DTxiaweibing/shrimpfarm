package com.shrimpfarm.app.backup;

/** 纯策略：WAL checkpoint 的结果是否允许复制主库文件。 */
public final class DatabaseSnapshotGate {

    private DatabaseSnapshotGate() {
    }

    /**
     * @param busy         PRAGMA wal_checkpoint 第 1 列，1 表示仍有活跃连接/事务
     * @param log          第 2 列，WAL 中的帧数；非 WAL 模式为 -1
     * @param checkpointed 第 3 列，已落盘帧数；非 WAL 模式为 -1
     */
    public static boolean allowSnapshot(int busy, int log, int checkpointed) {
        if (busy != 0) return false;
        // 非 WAL 模式下 checkpoint 是 no-op，主库文件本身就是权威版本。
        if (log < 0 || checkpointed < 0) return true;
        return checkpointed == log;
    }
}
