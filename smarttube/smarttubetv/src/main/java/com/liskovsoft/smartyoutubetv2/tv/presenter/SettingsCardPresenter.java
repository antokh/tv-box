package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build.VERSION;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class SettingsCardPresenter extends Presenter {
    private int mDefaultBackgroundColor;
    private int mDefaultTextColor;
    private int mSelectedBackgroundColor;
    private int mSelectedTextColor;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();

        if (com.liskovsoft.smartyoutubetv2.tv.util.TvBoxStyle.isEnabled(context)) {
            return createTvBoxViewHolder(context);
        }

        mDefaultBackgroundColor =
                ContextCompat.getColor(context, Helpers.getThemeAttr(context, R.attr.cardDefaultBackground));
        mDefaultTextColor =
                ContextCompat.getColor(context, R.color.card_default_text);
        mSelectedBackgroundColor =
                ContextCompat.getColor(context, R.color.card_selected_background_white);
        mSelectedTextColor =
                ContextCompat.getColor(context, R.color.card_selected_text_grey);

        @SuppressLint("InflateParams")
        View container = LayoutInflater.from(context).inflate(R.layout.settings_card, null);
        container.setBackgroundColor(mDefaultBackgroundColor);
        //if (VERSION.SDK_INT >= 23 && MainUIData.instance(context).isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS)) {
        //    container.setForeground(ContextCompat.getDrawable(context, R.drawable.lb_card_outline));
        //}

        TextView textView = container.findViewById(R.id.settings_title);
        textView.setBackgroundColor(mDefaultBackgroundColor);
        textView.setTextColor(mDefaultTextColor);

        ViewUtil.setTextScrollSpeed(textView, getCardTextScrollSpeed(context));

        container.setOnFocusChangeListener((v, hasFocus) -> {
            int backgroundColor = hasFocus ? mSelectedBackgroundColor : mDefaultBackgroundColor;
            int textColor = hasFocus ? mSelectedTextColor : mDefaultTextColor;

            textView.setBackgroundColor(backgroundColor);
            textView.setTextColor(textColor);

            if (hasFocus) {
                ViewUtil.enableMarquee(textView);
            } else {
                ViewUtil.disableMarquee(textView);
            }
        });

        return new ViewHolder(container);
    }

    /**
     * TV Box style: wide rounded glass row (icon in a frosted circle + name), white with dark
     * icon/text when focused, small grow like the top tabs. Names never scroll: they fit the row.
     */
    private ViewHolder createTvBoxViewHolder(Context context) {
        @SuppressLint("InflateParams")
        View container = LayoutInflater.from(context).inflate(R.layout.tvbox_settings_card, null);
        // Inflated without a parent, so the root's size has to be set here.
        container.setLayoutParams(new ViewGroup.LayoutParams(
                context.getResources().getDimensionPixelSize(R.dimen.tvbox_settings_tile_width),
                context.getResources().getDimensionPixelSize(R.dimen.tvbox_settings_tile_height)));
        TextView textView = container.findViewById(R.id.settings_title);
        ImageView imageView = container.findViewById(R.id.settings_image);
        int focusedColor = ContextCompat.getColor(context, R.color.tvbox_on_focus);

        imageView.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
        if (VERSION.SDK_INT >= 21) {
            container.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(context, R.animator.tvbox_tab_scale));
        }

        container.setOnFocusChangeListener((v, hasFocus) -> {
            int fg = hasFocus ? focusedColor : android.graphics.Color.WHITE;
            textView.setTextColor(fg);
            imageView.setColorFilter(fg, android.graphics.PorterDuff.Mode.SRC_IN);
        });

        return new ViewHolder(container);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        SettingsItem settingsItem = (SettingsItem) item;

        TextView textView = viewHolder.view.findViewById(R.id.settings_title);

        textView.setText(settingsItem.title);

        if (settingsItem.imageResId > 0) {
            Context context = viewHolder.view.getContext();
            ImageView imageView = viewHolder.view.findViewById(R.id.settings_image);
            imageView.setImageDrawable(ContextCompat.getDrawable(context, settingsItem.imageResId));
            imageView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }

    protected boolean isCardTextAutoScrollEnabled(Context context) {
        return MainUIData.instance(context).isCardTextAutoScrollEnabled();
    }

    protected float getCardTextScrollSpeed(Context context) {
        return MainUIData.instance(context).getCardTextScrollSpeed();
    }
}
