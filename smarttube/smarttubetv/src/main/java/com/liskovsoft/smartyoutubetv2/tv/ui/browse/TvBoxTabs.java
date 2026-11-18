package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.animation.LayoutTransition;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * TV Box build: Prime Video-style section tabs at the top, replacing the big left menu.
 * Each tab is the section's own icon; the focused tab becomes a white pill and its name slides out
 * next to the icon, the open page's tab keeps a frosted pill. Moving along the tabs switches the page
 * underneath (after a short pause, so passing over a tab doesn't load it); OK jumps into the page.
 */
public class TvBoxTabs {
    public interface Listener {
        void onTabFocused(int index);

        void onTabClicked(int index);
    }

    private static final int SWITCH_DELAY_MS = 320;
    private final Context mContext;
    private final HorizontalScrollView mScroll;
    private final LinearLayout mStrip;
    private final View mLeftNeighbor;
    private final Listener mListener;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<LinearLayout> mTabs = new ArrayList<>();
    private final List<ImageView> mIcons = new ArrayList<>();
    private final List<TextView> mNames = new ArrayList<>();
    private List<String> mLabels = new ArrayList<>();
    private List<Integer> mIconRes = new ArrayList<>();
    private int mSelected = -1;
    private Runnable mPendingSwitch;

    public TvBoxTabs(HorizontalScrollView scroll, LinearLayout strip, View leftNeighbor, Listener listener) {
        mContext = scroll.getContext();
        mScroll = scroll;
        mStrip = strip;
        mLeftNeighbor = leftNeighbor;
        mListener = listener;
        mScroll.setVisibility(View.VISIBLE);
        mScroll.setHorizontalScrollBarEnabled(false);
        mScroll.setSmoothScrollingEnabled(true);
        // Smoothly widen/narrow the strip when a tab's name slides in or out.
        LayoutTransition transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        transition.setDuration(180);
        mStrip.setLayoutTransition(transition);

        // While the user is on the tabs, a page that finishes loading must not pull focus down into itself
        // (that made the tabs after Subscriptions unreachable). Only Down / OK leave the tabs.
        mStrip.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (oldFocus == null || newFocus == null || !mTabs.contains(oldFocus) || isInStrip(newFocus)) {
                return;
            }
            boolean userLeft = android.os.SystemClock.uptimeMillis() - mLastLeaveKeyTime < 600;
            if (!userLeft && oldFocus.isAttachedToWindow()) {
                oldFocus.requestFocus();
            }
        });
    }

    private long mLastLeaveKeyTime;

    private boolean isInStrip(View view) {
        for (View v = view; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v == mScroll) {
                return true;
            }
        }
        return false;
    }

    /** Rebuilds the strip only when the sections changed; always updates the selected tab. */
    public void setTabs(List<String> labels, List<Integer> iconRes, int selected) {
        if (!labels.equals(mLabels) || !iconRes.equals(mIconRes)) {
            mLabels = new ArrayList<>(labels);
            mIconRes = new ArrayList<>(iconRes);
            build();
        }
        setSelected(selected);
    }

    public void setSelected(int index) {
        mSelected = index;
        for (int i = 0; i < mTabs.size(); i++) {
            mTabs.get(i).setSelected(i == index);
            style(i);
        }
    }

    /** The open page's tab (where focus goes when moving up to the tabs or pressing Back). */
    public View getSelectedTab() {
        if (mSelected >= 0 && mSelected < mTabs.size()) {
            return mTabs.get(mSelected);
        }
        return mTabs.isEmpty() ? null : mTabs.get(0);
    }

    private void build() {
        mHandler.removeCallbacksAndMessages(null);
        mStrip.removeAllViews();
        mTabs.clear();
        mIcons.clear();
        mNames.clear();

        for (int i = 0; i < mLabels.size(); i++) {
            final int index = i;
            LinearLayout tab = new LinearLayout(mContext);
            tab.setId(View.generateViewId());
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(dp(10), dp(7), dp(10), dp(7));
            tab.setBackground(ContextCompat.getDrawable(mContext, R.drawable.tvbox_tab));
            tab.setFocusable(true);
            tab.setFocusableInTouchMode(true);
            tab.setClickable(true);
            tab.setContentDescription(mLabels.get(i));
            if (Build.VERSION.SDK_INT >= 21) {
                tab.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(mContext, R.animator.tvbox_tab_scale));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            tab.setLayoutParams(lp);

            ImageView icon = new ImageView(mContext);
            int res = mIconRes.get(i);
            if (res > 0) {
                icon.setImageDrawable(ContextCompat.getDrawable(mContext, res));
            } else {
                icon.setVisibility(View.GONE); // no icon (e.g. a pinned channel): the name is shown instead
            }
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
            tab.addView(icon);

            TextView name = new TextView(mContext);
            name.setText(mLabels.get(i));
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            name.setSingleLine(true);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nameLp.leftMargin = res > 0 ? dp(8) : 0;
            name.setLayoutParams(nameLp);
            tab.addView(name);

            tab.setOnFocusChangeListener((v, hasFocus) -> {
                style(index);
                if (mPendingSwitch != null) {
                    mHandler.removeCallbacks(mPendingSwitch);
                    mPendingSwitch = null;
                }
                if (hasFocus && index != mSelected) {
                    mPendingSwitch = () -> {
                        mPendingSwitch = null;
                        setSelected(index);
                        mListener.onTabFocused(index);
                    };
                    mHandler.postDelayed(mPendingSwitch, SWITCH_DELAY_MS);
                }
            });
            tab.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                        || keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_BACK || keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT)) {
                    mLastLeaveKeyTime = android.os.SystemClock.uptimeMillis();
                }
                return false;
            });
            tab.setOnClickListener(v -> {
                if (mPendingSwitch != null) {
                    mHandler.removeCallbacks(mPendingSwitch);
                    mPendingSwitch = null;
                }
                setSelected(index);
                mListener.onTabClicked(index);
            });
            mStrip.addView(tab);
            mTabs.add(tab);
            mIcons.add(icon);
            mNames.add(name);
            style(i);
        }

        // Left/right chain: first tab ... last tab (stops at both ends).
        for (int i = 0; i < mTabs.size(); i++) {
            View tab = mTabs.get(i);
            tab.setNextFocusLeftId(i > 0 ? mTabs.get(i - 1).getId() : (mLeftNeighbor != null ? mLeftNeighbor.getId() : tab.getId()));
            tab.setNextFocusRightId(i < mTabs.size() - 1 ? mTabs.get(i + 1).getId() : tab.getId());
        }
        if (mLeftNeighbor != null && !mTabs.isEmpty()) {
            mLeftNeighbor.setNextFocusRightId(mTabs.get(0).getId());
        }

        // Icons are compact, so the strip usually fits; cap it anyway, it scrolls with focus.
        int cap = Math.round(mContext.getResources().getDisplayMetrics().widthPixels * 0.80f);
        ViewGroup.LayoutParams lp = mScroll.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        mScroll.setLayoutParams(lp);
        mScroll.post(() -> {
            if (mScroll.getWidth() > cap) {
                ViewGroup.LayoutParams capped = mScroll.getLayoutParams();
                capped.width = cap;
                mScroll.setLayoutParams(capped);
            }
        });
    }

    /** Focused: white pill, dark icon, name shown. Open page: frosted pill, white icon. Others: dimmed icon. */
    private void style(int index) {
        if (index < 0 || index >= mTabs.size()) {
            return;
        }
        LinearLayout tab = mTabs.get(index);
        ImageView icon = mIcons.get(index);
        TextView name = mNames.get(index);
        boolean focused = tab.hasFocus();
        boolean selected = index == mSelected;
        boolean hasIcon = icon.getVisibility() == View.VISIBLE;

        int dark = ContextCompat.getColor(mContext, R.color.tvbox_on_focus);
        int fg = focused ? dark : 0xFFFFFFFF;
        icon.setColorFilter(fg, PorterDuff.Mode.SRC_IN);
        icon.setAlpha(focused || selected ? 1f : 0.7f);
        name.setTextColor(focused ? dark : (selected ? 0xFFFFFFFF : 0xB3FFFFFF));
        // Names only on the focused tab (and on tabs without an icon).
        name.setVisibility(focused || !hasIcon ? View.VISIBLE : View.GONE);
    }

    private int dp(float value) {
        return Math.round(value * mContext.getResources().getDisplayMetrics().density);
    }
}
