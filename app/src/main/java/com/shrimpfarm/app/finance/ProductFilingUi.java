package com.shrimpfarm.app.finance;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.shrimpfarm.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * UI wiring for A2 filing: quick-add category, supplier picker and unit spinners.
 * Duplicate names resolve to the existing record; blank names are rejected.
 */
public final class ProductFilingUi {

    public interface IdPicked {
        void onPicked(long id, String name);
    }

    public interface CategoryPicked {
        void onPicked(long id);
    }

    public interface NamePicked {
        void onPicked(String name);
    }

    private ProductFilingUi() {
    }

    /** Category spinner with a trailing quick-add item; caller reloads and rebinds on pick. */
    public static void bindCategorySpinner(final Activity activity, final Spinner spinner,
                                           final List<FinanceDb.FinCategory> categories,
                                           long selectedId, final FinanceDb db,
                                           final CategoryPicked callback) {
        String empty = activity.getString(R.string.finance_choose_category);
        String quick = activity.getString(R.string.finance_quick_add_category);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item,
                ProductFilingOptions.categoryLabels(categories, empty, quick));
        spinner.setAdapter(adapter);
        spinner.setSelection(ProductFilingOptions.categoryPosition(categories, selectedId));
        final int size = categories.size();
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < size) return;
                quickAddCategory(activity, db, callback);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /** Reusable named-input dialog; keeps itself open on invalid input. */
    public static void promptName(final Activity activity, int titleRes, final NamePicked callback) {
        final EditText input = new EditText(activity);
        input.setHint(R.string.finance_input_name);
        input.setSingleLine(true);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(titleRes).setView(input)
                .setPositiveButton(R.string.btn_ok, null)
                .setNegativeButton(R.string.btn_cancel, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = input.getText().toString().trim();
                    if (!ProductFilingOptions.validName(name)) {
                        Toast.makeText(activity, R.string.finance_name_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    callback.onPicked(name);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    public static void quickAddCategory(final Activity activity, final FinanceDb db,
                                        final CategoryPicked callback) {
        promptName(activity, R.string.finance_quick_add_category, name -> {
            long existing = ProductFilingOptions.existingCategoryId(db.getCategories(), name);
            long id = existing > 0 ? existing : db.insertCategory(name, true);
            if (id <= 0) {
                Toast.makeText(activity, R.string.finance_save_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            callback.onPicked(id);
        });
    }

    /** Supplier picker with a trailing quick-add item (A1 kind required for new suppliers). */
    public static void pickSupplier(final Activity activity, final FinanceDb db,
                                    final IdPicked callback) {
        final List<FinanceDb.FinSupplier> suppliers =
                db.getSuppliersByType(FinanceDb.PERSON_SUPPLIER);
        String quick = activity.getString(R.string.finance_quick_add_supplier);
        String[] labels = ProductFilingOptions.supplierLabels(suppliers, quick).toArray(new String[0]);
        new AlertDialog.Builder(activity).setTitle(R.string.finance_supplier_pick)
                .setItems(labels, (d, which) -> {
                    if (which >= suppliers.size()) {
                        quickAddSupplier(activity, db, callback);
                    } else {
                        FinanceDb.FinSupplier s = suppliers.get(which);
                        callback.onPicked(s.id, s.name);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null).show();
    }

    private static void quickAddSupplier(final Activity activity, final FinanceDb db,
                                         final IdPicked callback) {
        promptName(activity, R.string.finance_quick_add_supplier, name -> {
            List<FinanceDb.FinSupplier> all = new ArrayList<>();
            for (FinanceDb.FinSupplier s : db.getSuppliers()) {
                if (s.personType == FinanceDb.PERSON_SUPPLIER) all.add(s);
            }
            long existing = ProductFilingOptions.existingSupplierId(all, name);
            if (existing > 0) {
                callback.onPicked(existing, name);
                return;
            }
            String[] kinds = {activity.getString(R.string.finance_supplier_kind_med),
                    activity.getString(R.string.finance_supplier_kind_seed)};
            new AlertDialog.Builder(activity).setTitle(R.string.finance_supplier_kind_required)
                    .setItems(kinds, (kd, which) -> {
                        int kind = which == 1 ? SupplierKindRules.KIND_SEED
                                : SupplierKindRules.KIND_MED;
                        long id = db.insertSupplier(FinanceDb.PERSON_SUPPLIER, name,
                                "", "", "", 0, "", kind);
                        if (id <= 0) {
                            Toast.makeText(activity, R.string.finance_save_failed,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        callback.onPicked(id, name);
                    })
                    .setNegativeButton(R.string.btn_cancel, null).show();
        });
    }

    /** Row detail text for a filed supplier product (category, spec, price, pack). */
    public static String productDetail(FinanceDb.FinSupplierProduct sp) {
        StringBuilder detail = new StringBuilder();
        if (!sp.categoryName.isEmpty()) detail.append(sp.categoryName);
        if (!sp.spec.isEmpty()) {
            if (detail.length() > 0) detail.append(" 路 ");
            detail.append(sp.spec);
        }
        if (!sp.unit.isEmpty()) {
            if (detail.length() > 0) detail.append(" 路 ");
            detail.append("楼").append(FinanceDb.stripTrailingZeros(sp.price))
                    .append("/").append(sp.unit);
        }
        if (!sp.packUnit.isEmpty()) detail.append(" (").append(sp.packSpec()).append(")");
        return detail.toString();
    }

    public static ArrayAdapter<String> unitAdapter(Activity activity, String prefer) {
        List<String> units = new ArrayList<>();
        if (prefer != null && !prefer.isEmpty()) units.add(prefer);
        for (String u : FinanceDb.getBuiltinUnits()) if (!units.contains(u)) units.add(u);
        return new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, units);
    }

    /** Pack-unit spinner: index 0 is "no pack unit"; updates the derived spec label. */
    public static void bindPackUnit(final Activity activity, final Spinner spinner,
                                    final View parent, final EditText etQty,
                                    final TextView tvSpec, String current) {
        List<String> units = new ArrayList<>();
        units.add(activity.getString(R.string.finance_purchase_no_pack_unit));
        for (String u : FinanceDb.getBuiltinUnits()) units.add(u);
        spinner.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, units));
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).equals(current)) {
                spinner.setSelection(i);
                break;
            }
        }
        refreshPackSpec(activity, parent, spinner, etQty, tvSpec);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                refreshPackSpec(activity, parent, spinner, etQty, tvSpec);
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
    }

    private static void refreshPackSpec(Activity activity, View parent, Spinner spPackUnit,
                                        EditText etQty, TextView tvSpec) {
        Spinner spUnit = parent.findViewById(R.id.sp_unit);
        String unit = selectedText(activity, spUnit);
        String packUnit = selectedText(activity, spPackUnit);
        double packQty = parseAmount(etQty.getText().toString());
        if (packUnit.isEmpty() || packQty <= 1) {
            tvSpec.setVisibility(View.GONE);
            return;
        }
        tvSpec.setVisibility(View.VISIBLE);
        tvSpec.setText(activity.getString(R.string.finance_purchase_pack_spec_fmt,
                FinanceDb.stripTrailingZeros(packQty) + unit, packUnit));
    }

    private static String selectedText(Activity activity, Spinner spinner) {
        Object selected = spinner.getSelectedItem();
        if (selected == null) return "";
        String text = selected.toString().trim();
        return text.equals(activity.getString(R.string.finance_purchase_no_pack_unit)) ? "" : text;
    }

    public static double parseAmount(String s) {
        if (s == null) return 0;
        String text = s.trim();
        if (!text.matches("\\d+(\\.\\d+)?")) return 0;
        return Double.parseDouble(text);
    }
}
