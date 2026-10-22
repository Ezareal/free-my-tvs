package io.freemytv;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The menu that opens when OK is held on an app (on the shelf or in All Apps): a rounded panel with the app's name and
 * a list of things to do with it. It is a plain dialog, so the arrows and OK work like anywhere on the TV.
 */
final class AppMenu {
    private final Activity activity;
    private final String title;
    private final boolean night;
    private final List<String> labels = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final List<Boolean> dim = new ArrayList<>();

    AppMenu(Activity activity, String title, boolean night) {
        this.activity = activity;
        this.title = title;
        this.night = night;
    }

    /** An entry. `dimmed` entries are shown grayed out (they explain themselves when chosen). */
    AppMenu add(String label, boolean dimmed, Runnable action) {
        labels.add(label);
        dim.add(dimmed);
        actions.add(action);
        return this;
    }

    void show() {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
        }
        float unit = activity.getResources().getDisplayMetrics().widthPixels / 1920f;
        int panelW = Math.round(520 * unit), pad = Math.round(22 * unit), rowH = Math.round(70 * unit);
        int panelColor = night ? 0xF2232326 : 0xF7F7F7FA, ink = night ? 0xFFFFFFFF : 0xFF1C1C1E, inverse = night ? 0xFF1C1C1E : 0xFFFFFFFF;
        int dimInk = night ? 0xFF8E8E93 : 0xFF8E8E93;

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(panelColor);
        bg.setCornerRadius(34 * unit);
        bg.setStroke(Math.max(1, Math.round(2 * unit)), night ? 0x44FFFFFF : 0x22000000);
        panel.setBackground(bg);

        TextView head = new TextView(activity);
        head.setText(title);
        head.setTextColor(dimInk);
        head.setTypeface(Ui.semibold);
        head.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26 * unit);
        head.setSingleLine();
        head.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.setPadding(Math.round(18 * unit), Math.round(6 * unit), Math.round(18 * unit), Math.round(14 * unit));
        panel.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView first = null;
        for (int i = 0; i < labels.size(); i++) {
            final int index = i;
            TextView row = new TextView(activity);
            row.setText(labels.get(i));
            row.setTypeface(Ui.medium);
            row.setTextSize(TypedValue.COMPLEX_UNIT_PX, 29 * unit);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Math.round(26 * unit), 0, Math.round(26 * unit), 0);
            row.setFocusable(true);
            row.setClickable(true);
            boolean dimmed = dim.get(i);
            row.setTextColor(new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}}, new int[] {inverse, dimmed ? dimInk : ink}));
            GradientDrawable on = new GradientDrawable();
            on.setColor(ink);
            on.setCornerRadius(rowH / 2f);
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[] {android.R.attr.state_focused}, on);
            states.addState(new int[] {}, new ColorDrawable(Color.TRANSPARENT));
            row.setBackground(states);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                actions.get(index).run();
            });
            panel.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH));
            if (first == null) first = row;
        }
        dialog.setContentView(panel, new ViewGroup.LayoutParams(panelW, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.show();
        if (first != null) first.requestFocus();
    }
}
