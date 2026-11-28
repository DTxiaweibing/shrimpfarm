package com.shrimpfarm.app.finance;

import android.app.Activity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.shrimpfarm.app.R;

/**
 * Supplier-kind UI wiring (A1): filter row, editor selector and list tag.
 * Non-supplier person screens never call the supplier-only methods here.
 */
public final class SupplierKindUi {

    public interface KindFilterCallback {
        void onKindFilter(int filter);
    }

    private SupplierKindUi() {
    }

    /** Shows the filter row for suppliers only; callback filter: 0=all, 1=med, 2=seed. */
    public static void setupFilter(Activity activity, boolean supplier, KindFilterCallback callback) {
        View row = activity.findViewById(R.id.kind_filter_row);
        row.setVisibility(supplier ? View.VISIBLE : View.GONE);
        if (!supplier) return;
        Spinner spinner = activity.findViewById(R.id.sp_kind_filter);
        String[] options = {activity.getString(R.string.finance_filter_all),
                activity.getString(R.string.finance_supplier_kind_med),
                activity.getString(R.string.finance_supplier_kind_seed)};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                callback.onKindFilter(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                callback.onKindFilter(0);
            }
        });
    }

    /** Adds the required kind selector, defaulting to the existing kind or MED. */
    public static Spinner addEditorSelector(Activity activity, LinearLayout layout, int currentKind) {
        TextView label = new TextView(activity);
        label.setText(R.string.finance_supplier_kind);
        layout.addView(label);
        Spinner spinner = new Spinner(activity);
        String[] kinds = {activity.getString(R.string.finance_supplier_kind_med),
                activity.getString(R.string.finance_supplier_kind_seed)};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, kinds);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setPrompt(activity.getString(R.string.finance_supplier_kind_required));
        spinner.setSelection(SupplierKindRules.normalizeKind(currentKind)
                == SupplierKindRules.KIND_SEED ? 1 : 0);
        layout.addView(spinner);
        return spinner;
    }

    /** Non-supplier screens pass their existing kind as fallback so edits never reset it. */
    public static int selectedKind(Spinner spinner, int fallback) {
        if (spinner == null) return fallback;
        return spinner.getSelectedItemPosition() == 1
                ? SupplierKindRules.KIND_SEED : SupplierKindRules.KIND_MED;
    }

    /** Returns the bracketed kind tag for supplier rows, empty for other person types. */
    public static String kindTag(Activity activity, int kind, boolean supplier) {
        if (!supplier) return "";
        int resId = activity.getResources().getIdentifier(
                SupplierKindRules.labelResKey(kind), "string", activity.getPackageName());
        return "  [" + activity.getString(resId == 0 ? R.string.finance_supplier_kind_med : resId) + "]";
    }
}
