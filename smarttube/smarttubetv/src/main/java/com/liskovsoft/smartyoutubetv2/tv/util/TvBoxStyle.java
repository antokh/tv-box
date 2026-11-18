package com.liskovsoft.smartyoutubetv2.tv.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * TV Box look (see res/values/tvbox.xml): see-through cards with rounded thumbnails and a white
 * focus ring, plus the blurred "ambient" backdrop built from the focused video's thumbnail.
 * Everything here is a no-op unless the current theme sets tvBoxStyle=true.
 */
public final class TvBoxStyle {
    private TvBoxStyle() {
    }

    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        TypedValue value = new TypedValue();
        return context.getTheme().resolveAttribute(R.attr.tvBoxStyle, value, true)
                && value.type == TypedValue.TYPE_INT_BOOLEAN && value.data != 0;
    }

    /**
     * Whether the "TV Box" colour scheme is selected, for screens with their own fixed theme
     * (e.g. sign-in) where the tvBoxStyle theme attribute isn't available.
     */
    public static boolean isSchemeActive(Context context) {
        if (context == null) {
            return false;
        }
        com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData.ColorScheme scheme =
                com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData.instance(context).getColorScheme();
        return scheme != null && scheme.nameResId == com.liskovsoft.smartyoutubetv2.common.R.string.color_scheme_tv_box;
    }

    public static int secondaryTextColor(Context context) {
        return ContextCompat.getColor(context, R.color.tvbox_text_secondary);
    }

    /** One-time card setup: transparent card, rounded thumbnail, medium-weight title. */
    public static void styleCard(View card) {
        Context context = card.getContext();
        card.setBackgroundColor(Color.TRANSPARENT);
        // Prime Video-like: cards are just the picture; the hero above shows title and details.
        if (card instanceof androidx.leanback.widget.BaseCardView) {
            ((androidx.leanback.widget.BaseCardView) card).setCardType(androidx.leanback.widget.BaseCardView.CARD_TYPE_MAIN_ONLY);
        }

        View image = card.findViewById(R.id.main_image_wrapper);
        if (image != null && Build.VERSION.SDK_INT >= 21) {
            final float radius = context.getResources().getDimension(R.dimen.tvbox_card_corner);
            image.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            image.setClipToOutline(true);
        }

        TextView title = card.findViewById(R.id.title_text);
        if (title != null) {
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
    }

    /** White ring over the thumbnail while focused, and it lifts off the page with a soft shadow. */
    public static void setCardFocused(View card, boolean focused) {
        View image = card.findViewById(R.id.main_image_wrapper);
        if (image == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            image.setForeground(focused ? ContextCompat.getDrawable(card.getContext(), R.drawable.tvbox_focus_ring) : null);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            float lift = focused ? 18 * card.getResources().getDisplayMetrics().density : 0;
            image.animate().translationZ(lift).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        }
    }

    /** Left-menu item: padded so the focus pill has room; focus animates the pill in. */
    public static void styleMenuItem(final View item) {
        float d = item.getResources().getDisplayMetrics().density;
        item.setPadding(Math.round(12 * d), Math.round(7 * d), Math.round(18 * d), Math.round(7 * d));
        // Apple TV-sized: small icon, modest text (stock is 32dp icons and large header text).
        android.widget.ImageView icon = item.findViewById(R.id.header_icon);
        if (icon != null) {
            android.view.ViewGroup.LayoutParams lp = icon.getLayoutParams();
            lp.width = Math.round(22 * d);
            lp.height = Math.round(22 * d);
            icon.setLayoutParams(lp);
            if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
                ((android.view.ViewGroup.MarginLayoutParams) lp).topMargin = Math.round(2 * d);
            }
        }
        TextView label = item.findViewById(R.id.header_label);
        if (label != null) {
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            if (label.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams mlp = (android.view.ViewGroup.MarginLayoutParams) label.getLayoutParams();
                mlp.topMargin = 0;
                mlp.setMarginStart(Math.round(12 * d));
                label.setLayoutParams(mlp);
            }
            label.setGravity(android.view.Gravity.CENTER_VERTICAL);
        }
        if (item instanceof android.widget.LinearLayout) {
            ((android.widget.LinearLayout) item).setGravity(android.view.Gravity.CENTER_VERTICAL);
        }
        item.setOnFocusChangeListener((v, hasFocus) -> setMenuItemFocused(v, hasFocus, true));
    }

    public static void setMenuItemFocused(View item, boolean focused, boolean animate) {
        Context context = item.getContext();
        // Frosted pill with white text: works for built-in icons and channel avatars alike.
        int fg = Color.WHITE;

        item.setBackground(focused ? ContextCompat.getDrawable(context, R.drawable.tvbox_menu_pill) : null);
        TextView label = item.findViewById(R.id.header_label);
        if (label != null) {
            label.setTextColor(fg);
        }

        float scale = focused ? 1.05f : 1f;
        item.setPivotX(0);
        item.setPivotY(item.getHeight() / 2f);
        if (animate) {
            item.animate().scaleX(scale).scaleY(scale).setDuration(200)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        } else {
            item.setScaleX(scale);
            item.setScaleY(scale);
        }
    }

    // ------------------------------------------------------------------ player (Apple TV-like)

    /** Round frosted button; focus = white ring + brighter glass, and it grows smoothly. */
    public static void stylePlayerButton(View button, android.widget.ImageView icon) {
        if (button == null) {
            return;
        }
        Context context = button.getContext();
        button.setBackground(ContextCompat.getDrawable(context, R.drawable.tvbox_player_button));
        if (Build.VERSION.SDK_INT >= 21) {
            button.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(context, R.animator.tvbox_focus_scale));
        }
        if (icon != null) {
            icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        }
        // Let the focus "pop" draw outside the button's own box instead of being cut off.
        button.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                android.view.ViewParent parent = v.getParent();
                for (int i = 0; i < 5 && parent instanceof android.view.ViewGroup; i++) {
                    ((android.view.ViewGroup) parent).setClipChildren(false);
                    ((android.view.ViewGroup) parent).setClipToPadding(false);
                    parent = parent.getParent();
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
            }
        });
    }

    /**
     * Controls under the video: thin rounded progress bar (thicker while seeking) and the time row as
     * "elapsed .......... total" under the bar, like tvOS. Codec/clock text is hidden by the presenter.
     */
    public static void stylePlayerRow(View root, View progressBar) {
        float d = root.getResources().getDisplayMetrics().density;

        if (progressBar instanceof com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.SeekBar) {
            com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.SeekBar bar =
                    (com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.SeekBar) progressBar;
            bar.setBarHeight(Math.round(5 * d));
            bar.setActiveBarHeight(Math.round(9 * d));
            bar.setActiveRadius(Math.round(9 * d));
        }

        View timeInfo = root.findViewById(R.id.time_info);
        View wrapper = root.findViewById(R.id.current_time_wrapper);
        TextView current = root.findViewById(R.id.current_time);
        View separator = root.findViewById(R.id.separate_time);
        TextView total = root.findViewById(R.id.total_time);
        if (timeInfo == null || wrapper == null || current == null || total == null) {
            return;
        }
        timeInfo.getLayoutParams().width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        timeInfo.setLayoutParams(timeInfo.getLayoutParams());
        wrapper.getLayoutParams().width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        wrapper.setLayoutParams(wrapper.getLayoutParams());
        if (current.getLayoutParams() instanceof android.widget.RelativeLayout.LayoutParams) {
            android.widget.RelativeLayout.LayoutParams lp = (android.widget.RelativeLayout.LayoutParams) current.getLayoutParams();
            lp.removeRule(android.widget.RelativeLayout.START_OF);
            lp.removeRule(android.widget.RelativeLayout.LEFT_OF);
            lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_START);
            lp.setMarginStart(0);
            current.setLayoutParams(lp);
        }
        if (separator != null) {
            separator.setVisibility(View.GONE);
        }
        int timeColor = ContextCompat.getColor(root.getContext(), R.color.tvbox_text_secondary);
        for (TextView t : new TextView[] {current, total}) {
            t.setTextColor(timeColor);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        // A little air between the bar and the times.
        if (timeInfo.getParent() instanceof View) {
            ((View) timeInfo.getParent()).setPadding(0, Math.round(6 * d), 0, 0);
        }
    }

    /** Title block above the controls: channel in grey, big medium-weight title, no stats line. */
    public static void stylePlayerDescription(TextView title, TextView subtitle, TextView body) {
        if (title != null) {
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
            title.setTextColor(Color.WHITE);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
        if (subtitle != null) {
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            subtitle.setTextColor(ContextCompat.getColor(subtitle.getContext(), R.color.tvbox_text_secondary));
            subtitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (body != null) {
            body.setText(null);
            body.setVisibility(View.GONE);
        }
    }

    /**
     * Turns a thumbnail into the full-screen backdrop: heavily blurred, dimmed, darker at the top
     * (title bar) and bottom (rows). Works on a tiny bitmap so it is cheap on a TV box.
     */
    public static Bitmap ambientBackdrop(Context context, Bitmap thumb) {
        final int smallW = 64, smallH = 36, outW = 480, outH = 270;

        Bitmap small = Bitmap.createScaledBitmap(thumb, smallW, smallH, true);
        if (small.getConfig() != Bitmap.Config.ARGB_8888 || !small.isMutable()) {
            Bitmap copy = small.copy(Bitmap.Config.ARGB_8888, true);
            if (small != thumb) {
                small.recycle();
            }
            small = copy;
        }
        boxBlur(small, 3);
        boxBlur(small, 3);
        boxBlur(small, 3);

        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(ContextCompat.getColor(context, R.color.tvbox_base));

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        paint.setAlpha(Math.round(255 * 0.85f));
        // Slightly oversized so the blurred edges never show the base colour.
        int padX = outW / 10, padY = outH / 10;
        canvas.drawBitmap(small, null, new Rect(-padX, -padY, outW + padX, outH + padY), paint);
        small.recycle();

        Paint scrim = new Paint();
        scrim.setShader(new LinearGradient(0, 0, 0, outH,
                new int[] {Color.argb(178, 0, 0, 0), Color.argb(90, 0, 0, 0), Color.argb(140, 0, 0, 0), Color.argb(230, 0, 0, 0)},
                new float[] {0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, outW, outH, scrim);
        return out;
    }

    /**
     * Prime Video-style hero: the artwork, sharp, in the top-right 16:9 area, fading into the base
     * colour towards the left (where the title sits) and the bottom (where the rows are).
     */
    public static Bitmap heroBackdrop(Context context, Bitmap art) {
        return heroBackdrop(context, art, 64);
    }

    /** @param dimAlpha extra overall darkening, 0-255 (64 on Home, more where text sits on top of it). */
    public static Bitmap heroBackdrop(Context context, Bitmap art, int dimAlpha) {
        final int outW = 1280, outH = 720;
        int base = ContextCompat.getColor(context, R.color.tvbox_base);
        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(base);

        // Art: 16:9, 76 % of the width, anchored top-right.
        int artW = Math.round(outW * 0.76f);
        int artH = Math.round(artW * 9f / 16f);
        Rect dst = new Rect(outW - artW, 0, outW, artH);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        canvas.drawBitmap(art, null, dst, paint);

        int clear = base & 0x00FFFFFF;
        // Fade to the left...
        Paint left = new Paint();
        float fadeEnd = dst.left + artW * 0.62f;
        left.setShader(new LinearGradient(dst.left, 0, fadeEnd, 0,
                new int[] {base, clear | 0xF0000000, clear | 0x99000000, clear},
                new float[] {0f, 0.25f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(dst.left, 0, fadeEnd + 1, outH, left);
        // ...and to the bottom, where the rows sit.
        Paint bottom = new Paint();
        bottom.setShader(new LinearGradient(0, artH * 0.35f, 0, artH * 0.92f,
                new int[] {clear, clear | 0xB3000000, base}, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, artH * 0.35f, outW, outH, bottom);
        // Light overall dim so white text stays readable on bright pictures.
        canvas.drawColor(Color.argb(dimAlpha, 0, 0, 0));
        return out;
    }

    /** In-place box blur (horizontal + vertical pass) of a small ARGB bitmap. */
    private static void boxBlur(Bitmap bmp, int radius) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] src = new int[w * h];
        int[] tmp = new int[w * h];
        bmp.getPixels(src, 0, w, 0, 0, w, h);
        blurPass(src, tmp, w, h, radius, true);
        blurPass(tmp, src, w, h, radius, false);
        bmp.setPixels(src, 0, w, 0, 0, w, h);
    }

    private static void blurPass(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w;
        int len = horizontal ? w : h;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < len; i++) {
                int a = 0, red = 0, g = 0, b = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.min(len - 1, Math.max(0, i + k));
                    int p = horizontal ? in[line * w + j] : in[j * w + line];
                    a += p >>> 24;
                    red += (p >> 16) & 0xFF;
                    g += (p >> 8) & 0xFF;
                    b += p & 0xFF;
                    n++;
                }
                int idx = horizontal ? line * w + i : i * w + line;
                out[idx] = ((a / n) << 24) | ((red / n) << 16) | ((g / n) << 8) | (b / n);
            }
        }
    }
}
