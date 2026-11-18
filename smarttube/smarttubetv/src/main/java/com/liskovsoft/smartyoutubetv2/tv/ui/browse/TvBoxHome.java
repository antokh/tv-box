package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.List;

/**
 * TV Box home layout (Prime Video-like), drawn inside the browse screen under the rows:
 * <ul>
 *   <li>Hero: big title, chips (LIVE / NEW / duration) and a meta line (channel, views, date) for the
 *   focused video of the first row. Its artwork is the backdrop (see UriBackgroundManager#setHeroFrom).</li>
 *   <li>Rail: a slim column of section icons on the left edge while the full menu is closed, so it's
 *   obvious there is a menu to the left. The current section sits in a frosted rounded square.</li>
 * </ul>
 */
public class TvBoxHome {
    private static final int FADE_MS = 220;
    private final Context mContext;
    private final float mDensity;
    private final LinearLayout mHero;
    private final TextView mTitle;
    private final LinearLayout mChips;
    private final TextView mMeta;
    private final TextView mDescription;
    private final LinearLayout mRail;
    private boolean mHeroWanted;
    private boolean mHeadersShown;
    private List<Integer> mRailIcons;
    private final TvBoxHeroPreview mPreview;

    public TvBoxHome(ViewGroup root) {
        mContext = root.getContext();
        mDensity = mContext.getResources().getDisplayMetrics().density;

        // ---- hero text block (top-left, under the search/account buttons)
        mHero = new LinearLayout(mContext);
        mHero.setOrientation(LinearLayout.VERTICAL);
        mHero.setAlpha(0f);
        mHero.setFocusable(false);
        // Bottom-anchored just above the first row (which sits at 50 % of the height, see
        // MultipleRowsFragment#setAlignment), so long titles grow upwards instead of into the row.
        int screenH = mContext.getResources().getDisplayMetrics().heightPixels;
        int heroTop = dp(76);
        // The row title sits above the row's alignment line: stop well before it.
        int heroBottom = Math.round(screenH * 0.50f) - dp(42);
        FrameLayout.LayoutParams heroLp = new FrameLayout.LayoutParams(
                Math.round(mContext.getResources().getDisplayMetrics().widthPixels * 0.58f), Math.max(dp(80), heroBottom - heroTop));
        heroLp.leftMargin = dp(72);
        heroLp.topMargin = heroTop;
        mHero.setLayoutParams(heroLp);
        mHero.setGravity(Gravity.BOTTOM);

        mTitle = new TextView(mContext);
        mTitle.setTextColor(Color.WHITE);
        mTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 29);
        mTitle.setMaxLines(2);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        mTitle.setLineSpacing(0, 1.05f);
        mTitle.setShadowLayer(12, 0, 2, 0x99000000);
        mHero.addView(mTitle);

        mChips = new LinearLayout(mContext);
        mChips.setOrientation(LinearLayout.HORIZONTAL);
        mChips.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsLp.topMargin = dp(8);
        mChips.setLayoutParams(chipsLp);
        mHero.addView(mChips);

        mMeta = new TextView(mContext);
        mMeta.setTextColor(0xE6FFFFFF);
        mMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        mMeta.setMaxLines(1);
        mMeta.setEllipsize(TextUtils.TruncateAt.END);
        mMeta.setShadowLayer(8, 0, 1, 0x99000000);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = dp(6);
        mMeta.setLayoutParams(metaLp);
        mHero.addView(mMeta);

        mDescription = new TextView(mContext);
        mDescription.setTextColor(0xCCFFFFFF);
        mDescription.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        mDescription.setMaxLines(2);
        mDescription.setEllipsize(TextUtils.TruncateAt.END);
        mDescription.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp(10);
        mDescription.setLayoutParams(descLp);
        mHero.addView(mDescription);

        // ---- icon rail: full-height strip on the left edge, above the rows (cards slide under it, like
        // Prime Video), on a dark fade so the icons stay readable. Hidden while the full menu is open.
        mRail = new LinearLayout(mContext);
        mRail.setOrientation(LinearLayout.VERTICAL);
        mRail.setGravity(Gravity.CENTER);
        mRail.setFocusable(false);
        int base = ContextCompat.getColor(mContext, R.color.tvbox_base);
        mRail.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {base, (base & 0x00FFFFFF) | 0xE6000000, base & 0x00FFFFFF}));
        FrameLayout.LayoutParams railLp = new FrameLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.MATCH_PARENT);
        railLp.gravity = Gravity.START;
        mRail.setLayoutParams(railLp);
        mRail.setPadding(0, 0, dp(10), 0);

        // Hero under the rows. The rail is no longer shown: sections are tabs at the top now
        // (TvBoxTabs); kept here only in case the left menu comes back.
        root.addView(mHero, 0);
        mRail.setVisibility(View.GONE);

        // Muted video of the focused video, playing where its artwork is (added at index 0: under the hero text).
        mPreview = new TvBoxHeroPreview(root);
    }

    /** The screen was left (a video opened, Home pressed): stop the hero video. */
    public void pausePreview() {
        mPreview.pause();
    }

    /** Back on the screen: the hero video starts again if the hero is showing. */
    public void resumePreview() {
        if (mHeroWanted) {
            mPreview.resume();
        }
    }

    // ------------------------------------------------------------------ hero

    public void showHero(Video video) {
        if (video == null) {
            hideHero();
            return;
        }
        mTitle.setText(video.getTitle());

        mChips.removeAllViews();
        if (video.isLive) {
            addChip(mContext.getString(R.string.badge_live), ContextCompat.getColor(mContext, R.color.tvbox_accent), Color.WHITE);
        } else if (video.hasNewContent) {
            addChip(mContext.getString(R.string.badge_new_content), Color.WHITE, 0xFF111111);
        }
        if (!video.isLive && video.badge != null && !video.badge.isEmpty()) {
            addChip(video.badge, 0x40FFFFFF, Color.WHITE);
        }
        if (video.isShorts) {
            addChip(mContext.getString(R.string.header_shorts), 0x40FFFFFF, Color.WHITE);
        }
        mChips.setVisibility(mChips.getChildCount() > 0 ? View.VISIBLE : View.GONE);

        CharSequence meta = video.getSecondTitle();
        mMeta.setText(meta);
        mMeta.setVisibility(TextUtils.isEmpty(meta) ? View.GONE : View.VISIBLE);

        String description = video.description;
        mDescription.setText(description);
        mDescription.setVisibility(TextUtils.isEmpty(description) ? View.GONE : View.VISIBLE);

        mHeroWanted = true;
        applyHeroVisibility();
        mPreview.show(video);
    }

    public void hideHero() {
        mHeroWanted = false;
        applyHeroVisibility();
        mPreview.hide();
    }

    private void applyHeroVisibility() {
        float target = mHeroWanted && !mHeadersShown ? 1f : 0f;
        mHero.animate().cancel();
        mHero.animate().alpha(target).translationY(target == 1f ? 0 : -dp(10))
                .setDuration(FADE_MS).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    private void addChip(CharSequence text, int bg, int fg) {
        TextView chip = new TextView(mContext);
        chip.setText(text.toString().toUpperCase());
        chip.setTextColor(fg);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        chip.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(bg);
        shape.setCornerRadius(dp(5));
        chip.setBackground(shape);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(10);
        chip.setLayoutParams(lp);
        mChips.addView(chip);
    }

    // ------------------------------------------------------------------ menu state + rail

    /** Called when the full left menu opens/closes: hero and rail make way for it. */
    public void onHeadersShown(boolean shown) {
        mHeadersShown = shown;
        mRail.animate().cancel();
        mRail.animate().alpha(shown ? 0f : 1f).setDuration(FADE_MS).start();
        applyHeroVisibility();
    }

    /** Section icons (drawable ids, 0 = none) and the selected one. Rebuilt only when the list changes. */
    public void updateRail(List<Integer> iconResIds, int selected) {
        if (!iconResIds.equals(mRailIcons)) {
            mRailIcons = new java.util.ArrayList<>(iconResIds);
            mRail.removeAllViews();
            for (Integer res : iconResIds) {
                ImageView icon = new ImageView(mContext);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int pad = dp(9);
                icon.setPadding(pad, pad, pad, pad);
                if (res != null && res > 0) {
                    icon.setImageDrawable(ContextCompat.getDrawable(mContext, res));
                }
                icon.setAlpha(0.6f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
                lp.bottomMargin = dp(10);
                icon.setLayoutParams(lp);
                mRail.addView(icon);
            }
        }
        for (int i = 0; i < mRail.getChildCount(); i++) {
            View icon = mRail.getChildAt(i);
            boolean isSelected = i == selected;
            if (isSelected) {
                GradientDrawable tile = new GradientDrawable();
                tile.setColor(0x47FFFFFF);
                tile.setCornerRadius(dp(10));
                icon.setBackground(tile);
                icon.setAlpha(1f);
            } else {
                icon.setBackground(null);
                icon.setAlpha(0.6f);
            }
        }
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }
}
