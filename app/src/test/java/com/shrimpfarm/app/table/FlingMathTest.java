package com.shrimpfarm.app.table;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 惯性公式回归：与计算器表同一套手感（自然 K=4、速度上限 25000、单帧 dt 上限 0.25s）。
 *
 * <p>重点是「由初速度 + 剩余跑道反解 k」和「解析积分不丢距离」两条，
 * 它们决定了猛甩会不会滑不动、掉帧会不会滑不远。</p>
 */
public class FlingMathTest {

    private static final float EPS = 1e-4f;

    @Test
    public void computeKUsesNaturalKWhenRunwayIsRoom() {
        // 4000/4 = 1000px 自然距离，跑道 10000 不构成限制 → k 就是自然 K
        assertEquals(FlingMath.NATURAL_K, FlingMath.computeK(4000f, 10000), EPS);
    }

    @Test
    public void computeKIsVelocityOverRunwayWhenRunwayLimits() {
        // 自然要滑 1000px，但只剩 500px → k = 4000/500 = 8，末速度正好为 0
        assertEquals(8.0f, FlingMath.computeK(4000f, 500), EPS);
    }

    @Test
    public void computeKUsesAbsoluteVelocity() {
        assertEquals(8.0f, FlingMath.computeK(-4000f, 500), EPS);
    }

    @Test
    public void computeKReturnsZeroForDegenerateInput() {
        assertEquals(0f, FlingMath.computeK(0f, 1000), 0f);
        assertEquals(0f, FlingMath.computeK(4000f, 0), 0f);
        assertEquals(0f, FlingMath.computeK(4000f, -5), 0f);
        // 只剩 1px 不值得起惯性
        assertEquals(0f, FlingMath.computeK(4000f, 1), 0f);
    }

    @Test
    public void clampDtCapsAndRejectsBadInput() {
        assertEquals(FlingMath.MAX_FRAME_DT_SECONDS, FlingMath.clampDt(0.5f), 0f);
        assertEquals(0.1f, FlingMath.clampDt(0.1f), 0f);
        assertEquals(0f, FlingMath.clampDt(-1f), 0f);
        assertEquals(0f, FlingMath.clampDt(Float.NaN), 0f);
    }

    @Test
    public void analyticDistanceMatchesTotalOverDecay() {
        float v = 4000f;
        float k = 4f;
        float dt = 2f / 1000f;
        float sum = 0f;
        for (int i = 0; i < 1000; i++) {
            sum += FlingMath.distance(v, k, dt);
            v = FlingMath.decayedVelocity(v, k, dt);
        }
        // 解析值 v/k = 1000px，1000 步累加误差应在 1% 内
        assertEquals(1000f, sum, 10f);
    }

    @Test
    public void velocityDecaysToNearlyZeroAfterTwoSeconds() {
        float v = 4000f;
        float k = 4f;
        float dt = 2f / 1000f;
        for (int i = 0; i < 1000; i++) {
            v = FlingMath.decayedVelocity(v, k, dt);
        }
        assertTrue("2s 后速度应低于 2px/s，实际 " + v, v < 2.0f);
    }

    @Test
    public void zeroKProducesNoMotion() {
        assertEquals(0f, FlingMath.distance(4000f, 0f, 0.016f), 0f);
        assertEquals(0f, FlingMath.decayedVelocity(4000f, 0f, 0.016f), 0f);
        assertEquals(0f, FlingMath.distance(4000f, -1f, 0.016f), 0f);
        assertEquals(0f, FlingMath.decayedVelocity(4000f, -1f, 0.016f), 0f);
    }

    @Test
    public void distanceKeepsSignOfVelocity() {
        assertTrue(FlingMath.distance(-4000f, 4f, 0.016f) < 0f);
        assertTrue(FlingMath.distance(4000f, 4f, 0.016f) > 0f);
    }

    @Test
    public void distanceIsPositiveForPositiveDt() {
        // 一帧 16ms、v=4000、k=4 → (1000)(1-e^-0.064) ≈ 62px
        assertEquals(62f, FlingMath.distance(4000f, 4f, 0.016f), 1f);
    }

    @Test
    public void exceedsComparesAbsoluteVelocity() {
        assertTrue(FlingMath.exceeds(60f, 60f));
        assertFalse(FlingMath.exceeds(59.9f, 60f));
        assertTrue(FlingMath.exceeds(-61f, 60f));
    }
}
