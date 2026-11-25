package com.shrimpfarm.app.table;

/**
 * 惯性滑动的纯数学：指数衰减 + 解析积分。
 *
 * <p>与计算器表（{@code MixCalcTableView}）同一套手感，只是把公式抽出来单测：</p>
 * <ul>
 *   <li>自然减速曲线总距离 = 初速度 / {@link #NATURAL_K}；</li>
 *   <li>按「初速度 + 该方向剩余距离」反解衰减系数 k，让末速度正好为 0，滑到边界刚好停；</li>
 *   <li>每帧位移用解析积分 {@code (v/k)(1-e^(-k·dt))}，掉帧也不会少走距离；</li>
 *   <li>测速上限 {@link #MAX_VELOCITY}，别被系统默认的 8000px/s 截断（猛甩会丢惯性）。</li>
 * </ul>
 *
 * <p>纯逻辑，不引用任何 Android 类型，可在普通 JVM 单测里直接调用。</p>
 */
public final class FlingMath {

    /** 自然减速曲线：总距离 = 初速度 / K */
    public static final float NATURAL_K = 4.0f;
    /** 测速上限，别被系统默认 8000px/s 截断 */
    public static final float MAX_VELOCITY = 25000f;
    /** 单帧时间上限：卡顿 / 切后台回来别一步跳太远 */
    public static final float MAX_FRAME_DT_SECONDS = 0.25f;

    private FlingMath() {
    }

    /**
     * 由「初速度 + 该方向剩余距离」反解衰减系数，末速度正好为 0。
     *
     * <p>自然距离 = {@code |v| / NATURAL_K}；实际距离 = {@code min(自然距离, runway)}；
     * {@code k = |v| / 实际距离}。</p>
     *
     * @return 速度为 0、runway &lt;= 0、或实际距离 &lt;= 1px（不值得起惯性）时返回 0，调用方据此放弃该轴
     */
    public static float computeK(float velocity, int runwayPx) {
        float absV = Math.abs(velocity);
        if (absV <= 0f || runwayPx <= 0) {
            return 0f;
        }
        float natural = absV / NATURAL_K;
        float distance = Math.min(natural, (float) runwayPx);
        if (distance <= 1f) {
            return 0f;
        }
        return absV / distance;
    }

    /**
     * 这一帧该走多远（带符号，方向同 velocity）：{@code (v/k)(1-e^(-k·dt))}。
     *
     * @return {@code k <= 0} 返回 0
     */
    public static float distance(float velocity, float k, float dtSeconds) {
        if (k <= 0f) {
            return 0f;
        }
        return (velocity / k) * (1f - (float) Math.exp(-k * dtSeconds));
    }

    /**
     * 这一帧之后的速度：{@code v·e^(-k·dt)}。
     *
     * @return {@code k <= 0} 返回 0
     */
    public static float decayedVelocity(float velocity, float k, float dtSeconds) {
        if (k <= 0f) {
            return 0f;
        }
        return velocity * (float) Math.exp(-k * dtSeconds);
    }

    /**
     * dt 夹到 {@code [0, MAX_FRAME_DT_SECONDS]}；负值 / NaN 返回 0。
     */
    public static float clampDt(float dtSeconds) {
        if (Float.isNaN(dtSeconds) || dtSeconds <= 0f) {
            return 0f;
        }
        return Math.min(dtSeconds, MAX_FRAME_DT_SECONDS);
    }

    /**
     * 是否够快、值得起惯性（用绝对值；两轴分别判断，调用方用 {@code ||} 组合）。
     */
    public static boolean exceeds(float velocity, float thresholdPx) {
        return Math.abs(velocity) >= thresholdPx;
    }
}
