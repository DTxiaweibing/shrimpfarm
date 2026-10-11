package com.shrimpfarm.app.finance;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shrimpfarm.app.R;

/**
 * 单据明细行：品类 / 品名 / 规格 / 历史价 / 当前价 / 涨跌幅 / 数量 / 金额
 * 列为固定宽度，保证与表头逐列对齐。
 */
public class PurchaseItemRowView {

    private final LinearLayout row;
    private final TextView tvCategory;
    private final TextView tvName;
    private final TextView tvSpec;
    private final TextView tvHistory;
    private final TextView tvCurrent;
    private final TextView tvChange;
    private final TextView tvQty;
    private final TextView tvAmount;

    public PurchaseItemRowView(Context ctx) {
        row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 6), dp(ctx, 9), dp(ctx, 6), dp(ctx, 9));
        row.setBackgroundResource(R.drawable.bg_po_row);

        tvCategory = cell(ctx, R.dimen.po_col_cat, Gravity.CENTER);
        tvName = cell(ctx, R.dimen.po_col_name, Gravity.CENTER);
        tvSpec = cell(ctx, R.dimen.po_col_spec, Gravity.CENTER);
        tvHistory = cell(ctx, R.dimen.po_col_price, Gravity.CENTER);
        tvCurrent = cell(ctx, R.dimen.po_col_price, Gravity.CENTER);
        tvChange = cell(ctx, R.dimen.po_col_change, Gravity.CENTER);
        tvQty = cell(ctx, R.dimen.po_col_qty, Gravity.CENTER);
        tvAmount = cell(ctx, R.dimen.po_col_amount, Gravity.END);

        row.addView(tvCategory);
        row.addView(tvName);
        row.addView(tvSpec);
        row.addView(tvHistory);
        row.addView(tvCurrent);
        row.addView(tvChange);
        row.addView(tvQty);
        row.addView(tvAmount);
    }

    public LinearLayout getRow() {
        return row;
    }

    public void bind(FinanceDb.FinPurchaseItem it) {
        tvCategory.setText(it.categoryName);
        tvName.setText(it.productName);

        // 规格为空时用单位兜底，避免出现无信息空列
        String spec = it.spec;
        tvSpec.setText(spec.isEmpty() ? it.unit : spec);

        tvHistory.setText(it.historyPrice > 0 ? fmt(it.historyPrice) : "-");
        tvCurrent.setText(it.hasPrice() ? fmt(it.unitPrice) : "-");

        if (it.historyPrice > 0 && it.hasPrice()) {
            double pct = it.priceChangePct();
            boolean up = pct > 0;
            tvChange.setText((up ? "+" : "") + fmt(pct) + "%");
            tvChange.setTextColor(up ? 0xFFE53935 : 0xFF43A047);
        } else {
            tvChange.setText("-");
            tvChange.setTextColor(0xFFAAAAAA);
        }

        String qty = trimNum(it.quantity);
        tvQty.setText(it.unit.isEmpty() ? qty : qty + it.unit);

        // 估算金额加 * 标记，提示该行用的是历史价
        if (!it.hasPrice()) {
            tvAmount.setText(fmt(it.amount) + "*");
            tvAmount.setTextColor(0xFFFB8C00);
        } else {
            tvAmount.setText(fmt(it.amount));
            tvAmount.setTextColor(0xFF222222);
        }
    }

    private static TextView cell(Context ctx, int dimenRes, int gravity) {
        TextView tv = new TextView(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ctx.getResources().getDimensionPixelSize(dimenRes), LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        tv.setGravity(gravity);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTextColor(0xFF333333);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return tv;
    }

    private static int dp(Context ctx, int value) {
        return (int) (value * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 两位小数，去掉多余的 .0 */
    static String fmt(double v) {
        String s = String.format(java.util.Locale.CHINA, "%.2f", v);
        if (s.endsWith(".00")) return s.substring(0, s.length() - 3);
        if (s.endsWith(".0")) return s.substring(0, s.length() - 2);
        return s;
    }

    /** 数量：整数不带小数 */
    static String trimNum(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return fmt(v);
    }

    /** 供表头/明细同步横向滚动使用 */
    public static void syncScroll(final HorizontalScrollView from, HorizontalScrollView to) {
        if (from == null || to == null) return;
        from.setOnScrollChangeListener(new android.view.View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(android.view.View v, int scrollX, int scrollY,
                                      int oldScrollX, int oldScrollY) {
                if (to.getScrollX() != scrollX) to.scrollTo(scrollX, 0);
            }
        });
    }
}
