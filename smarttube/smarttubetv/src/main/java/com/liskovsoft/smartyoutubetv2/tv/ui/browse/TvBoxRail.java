package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * TV Box build: Prime Video-style side rail with just Profile (sign in), Search and Settings.
 * Closed: a column of small icons on the left edge. When it gets focus (Left from the first card),
 * it opens: labels slide in and the whole screen dims behind a soft dark fade from the left.
 * The focused item becomes a white rounded square with a dark icon.
 */
public class TvBoxRail {
    public interface Listener {
        void onProfile();

        void onSearch();

        void onSettings();

        /** Right / Back from the rail: return to the page. */
        void onLeaveRail();
    }

    private static final int ANIM_MS = 220;
    /** Rail icons end at 56dp, rows start at 72dp (focused card grows to ~64dp). */
    private static final int BACKDROP_SOLID_DP = 58;
    private static final int BACKDROP_FADE_DP = 8;
    private final Context mContext;
    private final float mDensity;
    private final View mScrim;
    private final LinearLayout mColumn;
    private final List<View> mItems = new ArrayList<>();
    private final List<TextView> mLabels = new ArrayList<>();
    private final List<ImageView> mIcons = new ArrayList<>();
    private final Listener mListener;
    private TextView mProfileLabel;
    private boolean mOpen;

    public TvBoxRail(ViewGroup root, Listener listener) {
        mContext = root.getContext();
        mDensity = mContext.getResources().getDisplayMetrics().density;
        mListener = listener;

        // Full-screen dim + fade from the left, only while the rail is open.
        int base = ContextCompat.getColor(mContext, R.color.tvbox_base) & 0x00FFFFFF;
        mScrim = new View(mContext);
        mScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {base | 0xFC000000, base | 0xEB000000, base | 0xA6000000, base | 0x8C000000}));
        mScrim.setAlpha(0f);
        mScrim.setFocusable(false);
        mScrim.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mColumn = new LinearLayout(mContext);
        mColumn.setOrientation(LinearLayout.VERTICAL);
        mColumn.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams columnLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        columnLp.leftMargin = dp(14);
        mColumn.setLayoutParams(columnLp);

        mProfileLabel = addItem(R.drawable.browse_title_account, mContext.getString(R.string.dialog_account_none), mListener::onProfile);
        addItem(R.drawable.action_search, mContext.getString(R.string.tvbox_search), mListener::onSearch);
        addItem(R.drawable.icon_settings, mContext.getString(R.string.header_settings), mListener::onSettings);

        // Vertical chain stays inside the rail.
        for (int i = 0; i < mItems.size(); i++) {
            View item = mItems.get(i);
            item.setNextFocusUpId(mItems.get(Math.max(0, i - 1)).getId());
            item.setNextFocusDownId(mItems.get(Math.min(mItems.size() - 1, i + 1)).getId());
            item.setNextFocusLeftId(item.getId());
        }

        root.addView(createBackdrop(base));
        root.addView(mScrim);
        root.addView(mColumn);
        updateProfile();
        applyOpen(false, false);
    }

    /**
     * Always-on dark strip behind the closed rail (Prime Video-like): solid under the icons, fading out
     * just before the first card. Cards that scroll left slide under it instead of under the icons.
     */
    private View createBackdrop(int base) {
        LinearLayout strip = new LinearLayout(mContext);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setFocusable(false);
        strip.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View solid = new View(mContext);
        solid.setBackgroundColor(base | 0xFF000000);
        solid.setLayoutParams(new LinearLayout.LayoutParams(dp(BACKDROP_SOLID_DP), ViewGroup.LayoutParams.MATCH_PARENT));
        strip.addView(solid);

        View fade = new View(mContext);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {base | 0xFF000000, base | 0x00000000}));
        fade.setLayoutParams(new LinearLayout.LayoutParams(dp(BACKDROP_FADE_DP), ViewGroup.LayoutParams.MATCH_PARENT));
        strip.addView(fade);
        return strip;
    }

    private TextView addItem(int iconRes, String label, Runnable action) {
        LinearLayout item = new LinearLayout(mContext);
        item.setId(View.generateViewId());
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setFocusable(true);
        item.setFocusableInTouchMode(true);
        item.setClickable(true);
        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemLp.bottomMargin = dp(10);
        item.setLayoutParams(itemLp);

        ImageView icon = new ImageView(mContext);
        icon.setImageDrawable(ContextCompat.getDrawable(mContext, iconRes));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        item.addView(icon);

        TextView text = new TextView(mContext);
        text.setText(label);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setSingleLine(true);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.leftMargin = dp(14);
        textLp.rightMargin = dp(24);
        text.setLayoutParams(textLp);
        item.addView(text);

        item.setOnClickListener(v -> action.run());
        item.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            int index = mItems.indexOf(v);
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    mListener.onLeaveRail();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return true; // edge of the screen
                case KeyEvent.KEYCODE_DPAD_UP:
                    // Leanback would send Up to the top tabs: stay inside the rail instead.
                    if (index > 0) {
                        mItems.get(index - 1).requestFocus();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (index < mItems.size() - 1) {
                        mItems.get(index + 1).requestFocus();
                    }
                    return true;
                default:
                    return false;
            }
        });
        item.setOnFocusChangeListener((v, hasFocus) -> {
            styleItem(mItems.indexOf(v), hasFocus);
            // Open while any item has focus; close when focus leaves the rail.
            v.post(() -> {
                boolean anyFocused = false;
                for (View i : mItems) {
                    anyFocused |= i.hasFocus();
                }
                if (anyFocused != mOpen) {
                    applyOpen(anyFocused, true);
                }
            });
        });

        mColumn.addView(item);
        mItems.add(item);
        mIcons.add(icon);
        mLabels.add(text);
        styleItem(mItems.size() - 1, false);
        return text;
    }

    private void styleItem(int index, boolean focused) {
        if (index < 0) {
            return;
        }
        ImageView icon = mIcons.get(index);
        TextView label = mLabels.get(index);
        if (focused) {
            GradientDrawable tile = new GradientDrawable();
            tile.setColor(Color.WHITE);
            tile.setCornerRadius(dp(10));
            icon.setBackground(tile);
            icon.setColorFilter(ContextCompat.getColor(mContext, R.color.tvbox_on_focus), PorterDuff.Mode.SRC_IN);
            icon.setAlpha(1f);
            label.setTextColor(Color.WHITE);
        } else {
            icon.setBackground(null);
            icon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            icon.setAlpha(mOpen ? 0.9f : 0.7f);
            label.setTextColor(0xCCFFFFFF);
        }
    }

    private void applyOpen(boolean open, boolean animate) {
        mOpen = open;
        long duration = animate ? ANIM_MS : 0;
        mScrim.animate().cancel();
        mScrim.animate().alpha(open ? 1f : 0f).setDuration(duration).start();
        for (TextView label : mLabels) {
            label.animate().cancel();
            if (open) {
                label.setVisibility(View.VISIBLE);
                label.setTranslationX(-dp(12));
                label.animate().alpha(1f).translationX(0).setDuration(duration)
                        .setInterpolator(new DecelerateInterpolator(2f)).start();
            } else {
                label.animate().alpha(0f).setDuration(animate ? ANIM_MS / 2 : 0)
                        .withEndAction(() -> label.setVisibility(View.GONE)).start();
            }
        }
        for (int i = 0; i < mItems.size(); i++) {
            styleItem(i, mItems.get(i).hasFocus());
        }
        if (open) {
            updateProfile();
        }
    }

    /** "Sign in" when no account, otherwise the account's name. */
    public void updateProfile() {
        Account account = MediaServiceManager.instance().getSelectedAccount();
        String name = account != null ? (account.getName() != null ? account.getName() : account.getEmail()) : null;
        mProfileLabel.setText(name != null ? name : mContext.getString(R.string.tvbox_sign_in));
    }

    public boolean hasFocus() {
        for (View item : mItems) {
            if (item.hasFocus()) {
                return true;
            }
        }
        return false;
    }

    /** Where Left from the page should land: the middle item (Search), like Prime Video. */
    public View getFocusTarget() {
        return mItems.isEmpty() ? null : mItems.get(Math.min(1, mItems.size() - 1));
    }

    public boolean requestFocus() {
        return !mItems.isEmpty() && mItems.get(0).requestFocus();
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }
}
