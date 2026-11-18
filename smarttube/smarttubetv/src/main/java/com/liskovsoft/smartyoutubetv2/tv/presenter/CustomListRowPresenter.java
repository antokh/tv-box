package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.util.TvBoxStyle;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class CustomListRowPresenter extends ListRowPresenter {
    public CustomListRowPresenter() {
        super(ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.FOCUS_DIMMER_ENABLED);
        setSelectEffectEnabled(ViewUtil.ROW_SELECT_EFFECT_ENABLED);
    }

    @Override
    protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
        boolean tvBox = TvBoxStyle.isEnabled(parent.getContext());
        if (tvBox) {
            // TV Box style: the thumbnail casts its own shadow; the stock one is a dark box behind the text.
            setShadowEnabled(false);
        }
        return super.createRowViewHolder(parent);
    }

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        super.onBindRowViewHolder(holder, item);
        if (holder.getHeaderViewHolder() != null && holder.getHeaderViewHolder().view instanceof TextView
                && TvBoxStyle.isEnabled(holder.view.getContext())) {
            // Row titles: white, medium weight, larger (Apple TV shelf titles).
            TextView title = (TextView) holder.getHeaderViewHolder().view;
            title.setTextColor(0xFFFFFFFF);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            title.setAlpha(1f);
        }
    }

    @Override
    protected void onSelectLevelChanged(RowPresenter.ViewHolder holder) {
        super.onSelectLevelChanged(holder);
        // Stock behaviour leaves titles of rows at half opacity; keep them crisp white.
        if (holder.getHeaderViewHolder() != null && TvBoxStyle.isEnabled(holder.view.getContext())) {
            holder.getHeaderViewHolder().view.setAlpha(1f);
        }
    }
}
