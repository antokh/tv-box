package com.mihir.remap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** On-TV settings: list mappings, add one by pressing a remote button, pick the app it opens. */
public class MainActivity extends Activity {
    private LinearLayout list;
    private TextView status;
    private AlertDialog learnDialog;

    static final int BG = 0xFF101418, CARD = 0xFF1E252C, FOCUS = 0xFFE8EAED, TEXT = 0xFFE8EAED, DIM = 0xFF9AA4AE;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Remaps.seed(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(96), dp(56), dp(96), dp(56));
        scroll.addView(root);

        root.addView(text("Remote Remap", 40, TEXT, true));
        TextView sub = text("Make any remote button open the app you choose.", 18, DIM, false);
        sub.setPadding(0, dp(4), 0, dp(20));
        root.addView(sub);

        status = text("", 16, DIM, false);
        status.setPadding(0, 0, 0, dp(20));
        root.addView(status);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        Button add = button("+  Add a button", null);
        add.setOnClickListener(v -> startLearning());
        root.addView(add);

        TextView hint = text("Select a mapping to change or remove it. Arrows, OK, Back, Home and Power can't be remapped.",
                15, DIM, false);
        hint.setPadding(0, dp(24), 0, 0);
        root.addView(hint);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLearning();
    }

    private void refresh() {
        boolean on = Remaps.serviceEnabled(this);
        status.setText(on ? "●  Remapping is on" : "●  Remapping is off. Turn on \"Remote Remap\" in Settings > Accessibility.");
        status.setTextColor(on ? 0xFF6DD58C : 0xFFF28B82);
        status.setFocusable(!on);
        status.setOnClickListener(on ? null : v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Exception ignored) { }
        });

        list.removeAllViews();
        Map<Integer, String> all = Remaps.all(this);
        if (all.isEmpty()) {
            TextView none = text("No buttons remapped yet.", 18, DIM, false);
            none.setPadding(0, 0, 0, dp(12));
            list.addView(none);
        }
        // One row per physical button: some buttons send one of two key codes (e.g. Mi TV+), so group by label.
        Map<String, List<Integer>> groups = new java.util.LinkedHashMap<>();
        for (Map.Entry<Integer, String> e : all.entrySet()) {
            String k = Remaps.keyLabel(e.getKey()) + "\n" + e.getValue();
            List<Integer> codes = groups.get(k);
            if (codes == null) groups.put(k, codes = new ArrayList<>());
            codes.add(e.getKey());
        }
        View first = null;
        for (Map.Entry<String, List<Integer>> g : groups.entrySet()) {
            List<Integer> codes = g.getValue();
            String pkg = all.get(codes.get(0));
            Button row = button(Remaps.keyLabel(codes.get(0)) + "   →   " + Remaps.appLabel(this, pkg), icon(pkg));
            row.setOnClickListener(v -> editMapping(codes));
            list.addView(row);
            if (first == null) first = row;
        }
        if (first != null && getCurrentFocus() == null) first.requestFocus();
    }

    private void editMapping(List<Integer> codes) {
        new AlertDialog.Builder(this)
                .setTitle(Remaps.keyLabel(codes.get(0)))
                .setItems(new String[]{"Change app", "Remove mapping", "Cancel"}, (d, which) -> {
                    if (which == 0) pickApp(codes);
                    else if (which == 1) { for (int c : codes) Remaps.remove(this, c); refresh(); }
                })
                .show();
    }

    // ---- "Press the button you want to remap" ----

    private void startLearning() {
        if (!Remaps.serviceEnabled(this)) {
            Toast.makeText(this, "Turn on Remote Remap in Accessibility settings first", Toast.LENGTH_LONG).show();
            return;
        }
        learnDialog = new AlertDialog.Builder(this)
                .setTitle("Press the remote button you want to remap")
                .setMessage("Press Back or Home to cancel.")
                .setOnDismissListener(d -> RemapService.learner = null)
                .show();
        RemapService.learner = code -> {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_HOME) { stopLearning(); return; }
            if (Remaps.isBlocked(code)) {
                Toast.makeText(this, "That button is needed to navigate, try another one", Toast.LENGTH_SHORT).show();
                return;
            }
            stopLearning();
            pickApp(Collections.singletonList(code));
        };
    }

    private void stopLearning() {
        RemapService.learner = null;
        if (learnDialog != null) { learnDialog.dismiss(); learnDialog = null; }
    }

    // ---- App picker ----

    static final class AppItem {
        final String pkg, label; final Drawable icon;
        AppItem(String pkg, String label, Drawable icon) { this.pkg = pkg; this.label = label; this.icon = icon; }
    }

    private void pickApp(List<Integer> codes) {
        int code = codes.get(0);
        List<AppItem> apps = launchableApps();
        ArrayAdapter<AppItem> adapter = new ArrayAdapter<AppItem>(this, 0, apps) {
            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                TextView tv = convert instanceof TextView ? (TextView) convert : new TextView(getContext());
                AppItem a = getItem(pos);
                tv.setText(a.label);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                tv.setTextColor(TEXT);
                tv.setGravity(Gravity.CENTER_VERTICAL);
                tv.setPadding(dp(24), dp(12), dp(24), dp(12));
                tv.setCompoundDrawablePadding(dp(20));
                Drawable d = a.icon.getConstantState() != null ? a.icon.getConstantState().newDrawable().mutate() : a.icon;
                d.setBounds(0, 0, dp(40), dp(40));
                tv.setCompoundDrawables(d, null, null, null);
                return tv;
            }
        };
        new AlertDialog.Builder(this)
                .setTitle(Remaps.keyLabel(code) + " opens…")
                .setAdapter(adapter, (d, which) -> {
                    AppItem a = apps.get(which);
                    for (int c : codes) Remaps.put(this, c, a.pkg);
                    Toast.makeText(this, Remaps.keyLabel(code) + " now opens " + a.label, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private List<AppItem> launchableApps() {
        PackageManager pm = getPackageManager();
        Set<String> seen = new HashSet<>();
        List<AppItem> out = new ArrayList<>();
        seen.add(Remaps.HOME_PKG); // the home screen itself isn't useful as a target...
        Drawable homeIcon = icon(Remaps.HOME_PKG);
        if (homeIcon != null) {    // ...but its All Apps grid is
            out.add(new AppItem(Remaps.ALL_APPS, Remaps.appLabel(this, Remaps.ALL_APPS), homeIcon));
        }
        List<AppItem> apps = new ArrayList<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                apps.add(new AppItem(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm)));
            }
        }
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        out.addAll(apps);
        return out;
    }

    // ---- Small view helpers ----

    private Drawable icon(String pkg) {
        if (Remaps.ALL_APPS.equals(pkg)) pkg = Remaps.HOME_PKG;
        try { return getPackageManager().getApplicationIcon(pkg); } catch (PackageManager.NameNotFoundException e) { return null; }
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        return t;
    }

    private Button button(String label, Drawable icon) {
        Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setText(label);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        btn.setPadding(dp(24), dp(14), dp(24), dp(14));
        btn.setTextColor(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}}, new int[]{Color.BLACK, TEXT}));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, rounded(FOCUS));
        bg.addState(new int[]{}, rounded(CARD));
        btn.setBackground(bg);
        btn.setStateListAnimator(null);
        if (icon != null) {
            icon.setBounds(0, 0, dp(40), dp(40));
            btn.setCompoundDrawables(icon, null, null, null);
            btn.setCompoundDrawablePadding(dp(20));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        btn.setLayoutParams(lp);
        return btn;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
        return g;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
