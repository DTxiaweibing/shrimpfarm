package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;
import org.junit.Test;

public class V17ReconcileCheckTest {
    @Test public void thresholdBoundary() {
        V17Reconcile.check("B1", "货款合计", 100, 100.005);  // 不抛
        try {
            V17Reconcile.check("B1", "货款合计", 100, 100.006);
            fail("差异 0.006 必须抛异常");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("货款合计"));
        }
        V17Reconcile.checkCount("B1", "明细行数", 3, 3);
        try {
            V17Reconcile.checkCount("B1", "明细行数", 3, 2);
            fail("数量必须完全相等");
        } catch (IllegalStateException expected) { }
    }
}
