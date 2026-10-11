package com.shrimpfarm.app.finance;

import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 单据式进货单详情：抬头 + 明细表 + 单脚合计/结账/账户
 */
public class PurchaseOrderActivity extends BaseActivity {

    @Override
    protected int getCurrentNavId() {
        return R.id.nav_home;
    }

    private FinanceDb financeDb;
    private long orderId;
    private FinanceDb.FinPurchaseOrder order;
    private List<FinanceDb.FinPurchaseItem> items;

    private TextView tvCompany;
    private TextView tvDate;
    private TextView tvOrderNo;
    private TextView tvSupplier;
    private TextView tvTotal;
    private TextView tvPaid;
    private TextView tvAccount;
    private TextView tvRemark;
    private LinearLayout llItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_purchase_order);

        financeDb = new FinanceDb(this);
        orderId = getIntent().getLongExtra("order_id", 0);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.finance_po_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvCompany = findViewById(R.id.tv_company);
        tvDate = findViewById(R.id.tv_date);
        tvOrderNo = findViewById(R.id.tv_order_no);
        tvSupplier = findViewById(R.id.tv_supplier);
        tvTotal = findViewById(R.id.tv_total);
        tvPaid = findViewById(R.id.tv_paid);
        tvAccount = findViewById(R.id.tv_account);
        tvRemark = findViewById(R.id.tv_remark);
        llItems = findViewById(R.id.ll_items);

        PurchaseItemRowView.syncScroll(
                (HorizontalScrollView) findViewById(R.id.hsv_header),
                (HorizontalScrollView) findViewById(R.id.hsv_items));

        findViewById(R.id.btn_edit).setOnClickListener(v -> {
            Intent i = new Intent(this, PurchaseEditActivity.class);
            i.putExtra("order_id", orderId);
            startActivity(i);
        });
        findViewById(R.id.btn_settle).setOnClickListener(v -> onSettleClick());
        findViewById(R.id.btn_print).setOnClickListener(v -> printOrder());
        findViewById(R.id.btn_export).setOnClickListener(v -> exportOrder());
        findViewById(R.id.btn_share).setOnClickListener(v -> shareOrder());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        order = financeDb.getPurchaseOrder(orderId);
        if (order == null) {
            finish();
            return;
        }
        items = financeDb.getPurchaseItems(orderId);

        tvCompany.setText(getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString("company_name", getString(R.string.finance_po_company)));
        tvDate.setText(getString(R.string.finance_po_date, order.date));
        tvOrderNo.setText(getString(R.string.finance_po_no, order.orderNo));
        tvSupplier.setText(getString(R.string.finance_po_supplier, order.supplierName));

        llItems.removeAllViews();
        for (FinanceDb.FinPurchaseItem it : items) {
            PurchaseItemRowView rv = new PurchaseItemRowView(this);
            rv.bind(it);
            llItems.addView(rv.getRow());
        }

        // 合计含估算行，行金额带 * 提示，合计后补一个星号说明
        boolean hasEstimate = false;
        for (FinanceDb.FinPurchaseItem it : items) {
            if (!it.hasPrice()) {
                hasEstimate = true;
                break;
            }
        }
        tvTotal.setText("¥" + PurchaseItemRowView.fmt(order.totalAmount)
                + (hasEstimate ? "*" : ""));

        tvPaid.setText(getString(order.isPaid()
                ? R.string.finance_po_paid_yes : R.string.finance_po_paid_no));
        tvPaid.setTextColor(order.isPaid() ? 0xFF43A047 : 0xFFFB8C00);

        tvAccount.setText(order.isPaid() && !order.accountName.isEmpty()
                ? order.accountName : getString(R.string.finance_po_account_none));

        if (order.remark.isEmpty()) {
            tvRemark.setVisibility(View.GONE);
        } else {
            tvRemark.setVisibility(View.VISIBLE);
            tvRemark.setText(order.remark);
        }

        Button btnSettle = findViewById(R.id.btn_settle);
        btnSettle.setText(order.isPaid()
                ? R.string.finance_purchase_unsettle : R.string.finance_settle_confirm);
    }

    private void onSettleClick() {
        if (order == null) return;
        if (order.isPaid()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.finance_purchase_unsettle)
                    .setPositiveButton(R.string.btn_ok, (d, w) -> {
                        financeDb.setOrderPaid(orderId, false, 0);
                        loadData();
                    })
                    .setNegativeButton(android.R.string.cancel, null).show();
            return;
        }

        if (order.totalAmount <= 0) {
            Toast.makeText(this, R.string.finance_purchase_settle_no_amount, Toast.LENGTH_SHORT).show();
            return;
        }
        final List<FinanceDb.FinAccount> accounts = financeDb.getPaymentAccounts();
        if (accounts.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.finance_purchase_no_account)
                    .setPositiveButton(R.string.btn_ok, null).show();
            return;
        }
        final String[] names = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) names[i] = accounts.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.finance_account_pay)
                        + " ¥" + PurchaseItemRowView.fmt(order.totalAmount))
                .setItems(names, (d, which) -> {
                    financeDb.setOrderPaid(orderId, true, accounts.get(which).id);
                    loadData();
                }).show();
    }

    // ==================== 导出 / 打印 ====================

    /** 生成 #316 样式的收据 HTML，导出与打印共用 */
    private String buildReceiptHtml() {
        String company = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString("company_name", getString(R.string.finance_po_company));
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
                .append("<style>")
                .append("*{box-sizing:border-box}")
                .append("body{font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;")
                .append("margin:16px;color:#1E2A3A;font-size:12px}")
                .append("h1{font-size:20px;text-align:center;margin:0 0 4px}")
                .append(".sub{text-align:center;color:#8A93A0;font-size:12px;margin-bottom:14px}")
                .append("table{width:100%;border-collapse:collapse;table-layout:fixed}")
                .append("th,td{border:1px solid #C9D2DC;padding:6px 4px;}")
                .append("th{background:#EEF2F6;font-weight:bold;font-size:11px}")
                .append("td{text-align:center;font-size:11px}")
                .append("td.l{text-align:left}")
                .append(".num{text-align:right;padding-right:8px}")
                .append(".foot{margin-top:12px;font-size:12px}")
                .append(".foot div{margin:3px 0}")
                .append(".est{color:#FB8C00}")
                .append("</style></head><body>");

        sb.append("<h1>").append(esc(company)).append("</h1>")
                .append("<div class=\"sub\">")
                .append(esc(getString(R.string.finance_po_date, order.date)))
                .append(" &nbsp;|&nbsp; ")
                .append(esc(getString(R.string.finance_po_no, order.orderNo)))
                .append("</div>");

        sb.append("<table><thead><tr>");
        int[] widths = {11, 15, 14, 10, 10, 10, 10, 20};
        String[] heads = {
                getString(R.string.finance_po_col_category),
                getString(R.string.finance_po_col_name),
                getString(R.string.finance_po_col_spec),
                getString(R.string.finance_po_col_history),
                getString(R.string.finance_po_col_current),
                getString(R.string.finance_po_col_change),
                getString(R.string.finance_po_col_qty),
                getString(R.string.finance_po_col_amount),
        };
        for (int i = 0; i < heads.length; i++) {
            sb.append("<th style=\"width:").append(widths[i]).append("%\">")
                    .append(esc(heads[i])).append("</th>");
        }
        sb.append("</tr></thead><tbody>");

        for (FinanceDb.FinPurchaseItem it : items) {
            String cur = it.hasPrice() ? PurchaseItemRowView.fmt(it.unitPrice) : "—";
            String chg = "—";
            String cls = "";
            if (it.hasPrice() && it.historyPrice > 0) {
                double pct = it.priceChangePct();
                chg = (pct > 0 ? "+" : "") + PurchaseItemRowView.fmt(pct) + "%";
                if (pct > 0) cls = " style=\"color:#E53935\"";
                else if (pct < 0) cls = " style=\"color:#43A047\"";
            }
            sb.append("<tr>")
                    .append("<td class=\"l\">").append(esc(it.categoryName)).append("</td>")
                    .append("<td class=\"l\">").append(esc(it.productName)).append("</td>")
                    .append("<td>").append(esc(it.spec)).append("</td>")
                    .append("<td>").append(it.historyPrice > 0
                            ? esc(PurchaseItemRowView.fmt(it.historyPrice)) : "—").append("</td>")
                    .append("<td>").append(esc(cur)).append("</td>")
                    .append("<td").append(cls).append(">").append(esc(chg)).append("</td>")
                    .append("<td>").append(esc(PurchaseItemRowView.trimNum(it.quantity)))
                    .append(it.unit.isEmpty() ? "" : esc(it.unit)).append("</td>")
                    .append("<td class=\"num\">").append(esc(PurchaseItemRowView.fmt(it.amount)))
                    .append(it.hasPrice() ? "" : "<span class=\"est\">*</span>")
                    .append("</td>")
                    .append("</tr>");
        }
        sb.append("</tbody></table>");

        sb.append("<div class=\"foot\">")
                .append("<div>").append(esc(getString(R.string.finance_po_col_amount)))
                .append("：<b>¥").append(esc(PurchaseItemRowView.fmt(order.totalAmount)))
                .append("</b>")
                .append(hasEstimate() ? "<span class=\"est\">*</span>" : "").append("</div>")
                .append("<div>").append(esc(getString(R.string.finance_po_paid_label)))
                .append("：").append(esc(getString(order.isPaid()
                        ? R.string.finance_po_paid_yes : R.string.finance_po_paid_no))).append("</div>")
                .append("<div>").append(esc(getString(R.string.finance_po_account_label)))
                .append("：").append(esc(order.isPaid() && !order.accountName.isEmpty()
                        ? order.accountName : getString(R.string.finance_po_account_none)))
                .append("</div>");
        if (!order.remark.isEmpty()) {
            sb.append("<div>").append(esc(getString(R.string.finance_remark)))
                    .append("：").append(esc(order.remark)).append("</div>");
        }
        if (hasEstimate()) {
            sb.append("<div class=\"est\">").append(esc(getString(R.string.finance_po_estimate_note)))
                    .append("</div>");
        }
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private boolean hasEstimate() {
        for (FinanceDb.FinPurchaseItem it : items) {
            if (!it.hasPrice()) return true;
        }
        return false;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String exportFileName() {
        return order.orderNo + ".html";
    }

    /** 打印：走系统打印框架，A4 横向以容纳 8 列 */
    private void printOrder() {
        if (order == null) return;
        final WebView web = new WebView(this);
        final FrameLayout holder = new FrameLayout(this);
        holder.addView(web, new FrameLayout.LayoutParams(1, 1));
        ((FrameLayout) findViewById(android.R.id.content))
                .addView(holder, new FrameLayout.LayoutParams(1, 1));

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                try {
                    PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    if (pm == null) throw new IllegalStateException("no print service");
                    String job = getString(R.string.finance_po_title) + " " + order.orderNo;
                    PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(job);
                    pm.print(job, adapter, new PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build());
                } catch (Exception e) {
                    Toast.makeText(PurchaseOrderActivity.this,
                            R.string.finance_po_print_failed, Toast.LENGTH_SHORT).show();
                } finally {
                    holder.setVisibility(View.GONE);
                }
            }
        });
        web.loadDataWithBaseURL(null, buildReceiptHtml(), "text/html", "UTF-8", null);
    }

    /** 导出 HTML 到「下载」目录（Android 10+ 走 MediaStore，无需权限） */
    private void exportOrder() {
        if (order == null) return;
        byte[] data = buildReceiptHtml().getBytes(StandardCharsets.UTF_8);
        String name = exportFileName();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, "text/html");
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new IllegalStateException("insert failed");
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new IllegalStateException("no stream");
                    os.write(data);
                }
                Toast.makeText(this, getString(R.string.finance_po_export_ok,
                        Environment.DIRECTORY_DOWNLOADS + "/" + name),
                        Toast.LENGTH_LONG).show();
            } else {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) throw new IllegalStateException("no dir");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdirs");
                File out = new File(dir, name);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(data);
                }
                Toast.makeText(this, getString(R.string.finance_po_export_ok,
                        out.getAbsolutePath()), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.finance_po_export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** 分享：临时文件 + FileProvider */
    private void shareOrder() {
        if (order == null) return;
        try {
            File dir = new File(getCacheDir(), "po_export");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdirs");
            File out = new File(dir, exportFileName());
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(buildReceiptHtml().getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", out);

            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/html");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, order.orderNo);
            send.setClipData(ClipData.newRawUri(out.getName(), uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(send,
                    getString(R.string.finance_po_share));
            if (send.resolveActivity(getPackageManager()) == null) {
                Toast.makeText(this, R.string.finance_po_share_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(this, R.string.finance_po_share_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
