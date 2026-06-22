package com.shrimpfarm.app.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.shrimpfarm.app.R;

public class DialogHelper {

    public static void showStyledConfirmDialog(Activity activity, String title, String message,
        String[] buttonTexts, DialogInterface.OnClickListener[] listeners) {
        showStyledConfirmDialog(activity, title, message, buttonTexts, null, listeners, false);
    }

    public static void showStyledConfirmDialog(Activity activity, String title, String message,
        String[] buttonTexts, DialogInterface.OnClickListener[] listeners, boolean cancelable) {
        showStyledConfirmDialog(activity, title, message, buttonTexts, null, listeners, cancelable);
    }

    @android.annotation.SuppressLint("InflateParams")
    public static void showStyledConfirmDialog(Activity activity, String title, String message,
        String[] buttonTexts, int[] buttonColors, DialogInterface.OnClickListener[] listeners,
        boolean cancelable) {
        Dialog dialog = new Dialog(activity);
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_simple_confirm, null);
        dialog.setContentView(dialogView);

        dialog.setCanceledOnTouchOutside(false);
        if (!cancelable) {
            dialog.setCancelable(false);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(title);

        TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        tvMessage.setText(message);

        LinearLayout buttonLayout = dialogView.findViewById(R.id.layout_buttons);
        for (int i = 0; i < buttonTexts.length; i++) {
            Button btn = new Button(activity);
            btn.setText(buttonTexts[i]);
            btn.setTextSize(15);
            btn.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            btn.setTextColor(buttonColors != null && i < buttonColors.length ? buttonColors[i] : 0xFF333333);
            btn.setBackgroundResource(android.R.color.transparent);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i < buttonTexts.length - 1) {
                View divider = new View(activity);
                divider.setLayoutParams(new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
                divider.setBackgroundColor(0xFFE0E0E0);
                buttonLayout.addView(divider);
            }
            final int index = i;
            btn.setOnClickListener(v -> {
                if (listeners != null && index < listeners.length && listeners[index] != null) {
                    listeners[index].onClick(dialog, index);
                }
                dialog.dismiss();
            });
            buttonLayout.addView(btn, lp);
        }

        dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    @android.annotation.SuppressLint("InflateParams")
    public static EditText showStyledInputDialog(Activity activity, String title, String hint,
        String defaultText, String[] buttonTexts, DialogInterface.OnClickListener[] listeners) {
        Dialog dialog = new Dialog(activity);
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_input, null);
        dialog.setContentView(dialogView);
        dialog.setCanceledOnTouchOutside(false);

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(title);

        final EditText editText = dialogView.findViewById(R.id.et_input);
        if (hint != null) editText.setHint(hint);
        if (defaultText != null) editText.setText(defaultText);

        LinearLayout buttonLayout = dialogView.findViewById(R.id.layout_buttons);
        for (int i = 0; i < buttonTexts.length; i++) {
            Button btn = new Button(activity);
            btn.setText(buttonTexts[i]);
            btn.setTextSize(15);
            btn.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            btn.setTextColor(0xFF333333);
            btn.setBackgroundResource(android.R.color.transparent);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i < buttonTexts.length - 1) {
                View divider = new View(activity);
                divider.setLayoutParams(new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
                divider.setBackgroundColor(0xFFE0E0E0);
                buttonLayout.addView(divider);
            }
            final int index = i;
            btn.setOnClickListener(v -> {
                if (listeners != null && index < listeners.length && listeners[index] != null) {
                    listeners[index].onClick(dialog, index);
                }
                dialog.dismiss();
            });
            buttonLayout.addView(btn, lp);
        }

        dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
        return editText;
    }
}
