package com.shrimpfarm.app.feedtable;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import com.shrimpfarm.app.table.FlingMath;

/**
 * 记录表的手势与惯性状态机：单指拖动、松手惯性、边界即停、点击 / 长按回调。
 *
 * <p>状态全在这里，{@link RecordTableView} 只做委托，绘制代码不受影响。</p>
 *
 * <p><b>符号约定</b>：{@code dx = lastX - ev.getX()}，手指左移 → dx 为正 → scrollX 增大 →
 * 内容左移。{@link VelocityTracker} 给的是手指速度，手指右移 vx 为正；惯性每帧把位移
 * 累加进 {@code flingAccX} 时用减号（{@code flingAccX -= moveX}），把「手指方向」换成
 * 「内容位移」，所以 vx 为正时 scrollX 减小。</p>
 *
 * <p><b>边界</b>：每次写滚动都先过 {@link RecordTableMetrics#clampScrollX} /
 * {@link RecordTableMetrics#clampScrollY}，越界值绝不存进请求值；撞边那一轴的速度与累加器
 * 立刻清零，不回弹、不出 EdgeEffect（视图已设 {@code OVER_SCROLL_NEVER}）。</p>
 */
public final class RecordTableGesture {

    /** 起惯性阈值（dp）：两轴都低于它就不起惯性 */
    private static final float FLING_START_DP = 60f;
    /** 停止阈值（dp/s）：两轴都低于它就停惯性 */
    private static final float FLING_STOP_DP = 15f;
    /** 测速时间窗（ms） */
    private static final int VELOCITY_UNITS = 1000;

    private final RecordTableView view;
    private final int touchSlop;
    private final float startVelocityPx;
    private final float stopVelocityPx;
    private final int longPressTimeout;

    private OnRecordCellTapListener listener;
    private VelocityTracker velocityTracker;

    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private boolean dragging;
    private boolean longPressFired;
    /** 拖动的小数余量：慢速拖动时不足 1px 的部分不丢 */
    private float dragAccX;
    private float dragAccY;

    private boolean flinging;
    private float flingVx;
    private float flingVy;
    /** 每轴自己的衰减系数，由「初速度 + 剩余跑道」反解 */
    private float flingKx;
    private float flingKy;
    private float flingAccX;
    private float flingAccY;
    private long flingLastTime;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (dragging || longPressFired) {
                return;
            }
            longPressFired = true;
            dispatchHit(downX, downY, true);
        }
    };

    private final Runnable flingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!flinging) {
                return;
            }
            stepFling();
        }
    };

    public RecordTableGesture(RecordTableView view) {
        this.view = view;
        ViewConfiguration config = ViewConfiguration.get(view.getContext());
        this.touchSlop = config.getScaledTouchSlop();
        float density = view.getResources().getDisplayMetrics().density;
        this.startVelocityPx = FLING_START_DP * density;
        this.stopVelocityPx = FLING_STOP_DP * density;
        this.longPressTimeout = ViewConfiguration.getLongPressTimeout();
    }

    public void setListener(OnRecordCellTapListener listener) {
        this.listener = listener;
    }

    public boolean isFlinging() {
        return flinging;
    }

    // ==================== 触摸 ====================

    public boolean onTouchEvent(MotionEvent ev) {
        // 没 setSizing 就没有列模型，也没法命中；不吞事件
        if (view.getColumns() == null) {
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return handleDown(ev);
            case MotionEvent.ACTION_MOVE:
                return handleMove(ev);
            case MotionEvent.ACTION_UP:
                return handleUp(ev);
            case MotionEvent.ACTION_CANCEL:
                return handleCancel();
            default:
                return true;
        }
    }

    private boolean handleDown(MotionEvent ev) {
        stopFling();
        downX = ev.getX();
        downY = ev.getY();
        lastX = downX;
        lastY = downY;
        dragging = false;
        longPressFired = false;
        dragAccX = 0f;
        dragAccY = 0f;
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        } else {
            velocityTracker.clear();
        }
        velocityTracker.addMovement(ev);
        view.removeCallbacks(longPressRunnable);
        view.postDelayed(longPressRunnable, longPressTimeout);
        requestDisallowIntercept(true);
        return true;
    }

    private boolean handleMove(MotionEvent ev) {
        if (velocityTracker != null) {
            velocityTracker.addMovement(ev);
        }
        float x = ev.getX();
        float y = ev.getY();
        if (!dragging) {
            if (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop) {
                dragging = true;
                view.removeCallbacks(longPressRunnable);
                lastX = x;
                lastY = y;
            }
            return true;
        }
        dragAccX += lastX - x;
        dragAccY += lastY - y;
        // 即使这一步被夹成 0 位移也要更新，否则手指停在边界再往回拉会跳
        lastX = x;
        lastY = y;
        int stepX = (int) dragAccX;
        int stepY = (int) dragAccY;
        dragAccX -= stepX;
        dragAccY -= stepY;
        scrollBy(stepX, stepY);
        return true;
    }

    private boolean handleUp(MotionEvent ev) {
        view.removeCallbacks(longPressRunnable);
        if (dragging) {
            startFlingFromTracker(ev);
        } else if (!longPressFired) {
            view.performClick();
            dispatchHit(ev.getX(), ev.getY(), false);
        }
        dragging = false;
        longPressFired = false;
        releaseVelocityTracker();
        return true;
    }

    /** CANCEL 只清拖动状态；惯性由下一次 DOWN 停（与计算器一致） */
    private boolean handleCancel() {
        view.removeCallbacks(longPressRunnable);
        dragging = false;
        longPressFired = false;
        releaseVelocityTracker();
        return true;
    }

    private void startFlingFromTracker(MotionEvent ev) {
        if (velocityTracker == null) {
            return;
        }
        velocityTracker.addMovement(ev);
        // 不用系统默认的 maxFlingVelocity（约 8000px/s），猛甩会被截断
        velocityTracker.computeCurrentVelocity(VELOCITY_UNITS, FlingMath.MAX_VELOCITY);
        startFling(velocityTracker.getXVelocity(), velocityTracker.getYVelocity());
    }

    private void requestDisallowIntercept(boolean disallow) {
        ViewParent parent = view.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private void releaseVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    // ==================== 点击 / 长按 ====================

    /** 命中一律走 metrics.cellAt；表头 / 日期列 / 空白返回 null 就不回调 */
    private void dispatchHit(float x, float y, boolean longPress) {
        if (listener == null) {
            return;
        }
        RecordTableColumns columns = view.getColumns();
        if (columns == null) {
            return;
        }
        RecordTableMetrics.Hit hit = view.getMetrics().cellAt(
                x, y, view.getTableScrollX(), view.getTableScrollY());
        if (hit == null || hit.column < 0 || hit.column >= columns.count()) {
            return;
        }
        String columnId = columns.ids[hit.column];
        if (longPress) {
            listener.onCellLongPress(hit.day, columnId);
        } else {
            listener.onCellTap(hit.day, columnId);
        }
    }

    // ==================== 惯性 ====================

    private void startFling(float vx, float vy) {
        stopFling();
        if (!FlingMath.exceeds(vx, startVelocityPx) && !FlingMath.exceeds(vy, startVelocityPx)) {
            return;
        }
        RecordTableMetrics metrics = view.getMetrics();
        int scrollX = view.getTableScrollX();
        int scrollY = view.getTableScrollY();
        // vx > 0（手指右移）→ 内容右移 → scrollX 减小 → 跑道就是已经滚掉的距离
        int runwayX = vx > 0f ? scrollX : metrics.maxScrollX - scrollX;
        int runwayY = vy > 0f ? scrollY : metrics.maxScrollY - scrollY;

        flingVx = vx;
        flingVy = vy;
        flingKx = FlingMath.computeK(vx, runwayX);
        flingKy = FlingMath.computeK(vy, runwayY);
        if (flingKx <= 0f) {
            flingVx = 0f;
        }
        if (flingKy <= 0f) {
            flingVy = 0f;
        }
        if (flingVx == 0f && flingVy == 0f) {
            return;
        }
        flinging = true;
        flingAccX = 0f;
        flingAccY = 0f;
        flingLastTime = SystemClock.uptimeMillis();
        view.postOnAnimation(flingRunnable);
    }

    /** 一帧：解析积分算位移 → 累加器取整 → 滚动 → 贴边清零 → 判停 */
    private void stepFling() {
        long now = SystemClock.uptimeMillis();
        float dt = FlingMath.clampDt((now - flingLastTime) / 1000f);
        flingLastTime = now;
        if (dt <= 0f) {
            view.postOnAnimation(flingRunnable);
            return;
        }
        float moveX = FlingMath.distance(flingVx, flingKx, dt);
        float moveY = FlingMath.distance(flingVy, flingKy, dt);
        flingVx = FlingMath.decayedVelocity(flingVx, flingKx, dt);
        flingVy = FlingMath.decayedVelocity(flingVy, flingKy, dt);
        // 累加器保留小数，取整后减回去；不许每帧直接 (int) moveX
        flingAccX -= moveX;
        flingAccY -= moveY;
        int stepX = (int) flingAccX;
        int stepY = (int) flingAccY;
        flingAccX -= stepX;
        flingAccY -= stepY;

        boolean moved = scrollBy(stepX, stepY);
        clearAxisAtEdge();
        if (!moved
                || (Math.abs(flingVx) < stopVelocityPx && Math.abs(flingVy) < stopVelocityPx)) {
            stopFling();
            return;
        }
        view.postOnAnimation(flingRunnable);
    }

    /** 贴边就把那一轴的速度与累加器清掉，撞边即停、不回弹 */
    private void clearAxisAtEdge() {
        RecordTableMetrics metrics = view.getMetrics();
        int scrollX = view.getTableScrollX();
        if (scrollX <= 0 || scrollX >= metrics.maxScrollX) {
            flingVx = 0f;
            flingAccX = 0f;
        }
        int scrollY = view.getTableScrollY();
        if (scrollY <= 0 || scrollY >= metrics.maxScrollY) {
            flingVy = 0f;
            flingAccY = 0f;
        }
    }

    /** 停惯性并清理帧回调；可重复调用 */
    public void stopFling() {
        flinging = false;
        flingVx = 0f;
        flingVy = 0f;
        flingKx = 0f;
        flingKy = 0f;
        flingAccX = 0f;
        flingAccY = 0f;
        view.removeCallbacks(flingRunnable);
        // detach / 外部重置时别让挂着的长按在事后才回调
        view.removeCallbacks(longPressRunnable);
    }

    // ==================== 滚动 ====================

    /**
     * 按增量滚动：先夹再写，越界值不存进请求值。
     *
     * @return 是否真的动了
     */
    private boolean scrollBy(int dx, int dy) {
        RecordTableMetrics metrics = view.getMetrics();
        int oldX = view.getTableScrollX();
        int oldY = view.getTableScrollY();
        int newX = metrics.clampScrollX(oldX + dx);
        int newY = metrics.clampScrollY(oldY + dy);
        if (newX == oldX && newY == oldY) {
            return false;
        }
        view.setTableScroll(newX, newY);
        return true;
    }
}
