package com.liskovsoft.smartyoutubetv2.tv.ui.browse.settings;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;
import androidx.leanback.widget.VerticalGridView;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.SettingsCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.SettingsSection;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.GridFragmentHelper;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.fragments.GridFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;

public class SettingsGridFragment extends GridFragment implements SettingsSection {
    private static final String TAG = SettingsGridFragment.class.getSimpleName();
    private static final int TVBOX_COLUMNS = 3;
    private static final int TVBOX_TOP_DP = 112; // clears the top tabs
    private static final int TVBOX_GAP_DP = 16;
    private ArrayObjectAdapter mSettingsAdapter;
    private BrowsePresenter mMainPresenter;
    private UriBackgroundManager mBackgroundManager;
    private final List<SettingsGroup> mPendingUpdates = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMainPresenter = BrowsePresenter.instance(getContext());
        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();

        setupAdapter();
        setupEventListeners();
        applyPendingUpdates();

        if (getMainFragmentAdapter().getFragmentHost() != null) {
            getMainFragmentAdapter().getFragmentHost().notifyDataReady(getMainFragmentAdapter());
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        VerticalGridView grid = getBrowseGrid();
        if (grid == null || !isTvBox()) {
            return;
        }

        // TV Box style: start the tiles below the top tabs, even gaps, and keep the grid still
        // (it fits on one screen) instead of scrolling each focused row to the middle.
        float d = getResources().getDisplayMetrics().density;
        grid.setPadding(grid.getPaddingLeft(), Math.round(TVBOX_TOP_DP * d), grid.getPaddingRight(), Math.round(24 * d));
        grid.setClipToPadding(false);
        grid.setHorizontalSpacing(Math.round(TVBOX_GAP_DP * d));
        grid.setVerticalSpacing(Math.round(TVBOX_GAP_DP * d));
        grid.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
        grid.setWindowAlignmentOffsetPercent(BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
    }

    private boolean isTvBox() {
        return com.liskovsoft.smartyoutubetv2.tv.util.TvBoxStyle.isEnabled(getContext());
    }

    @Override
    protected void showOrHideTitle() {
        // NOP. Always show Browse fragment title
    }

    private void applyPendingUpdates() {
        for (SettingsGroup group : mPendingUpdates) {
            update(group);
        }

        mPendingUpdates.clear();
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new ItemViewClickedListener());
    }

    private void setupAdapter() {
        boolean tvBox = isTvBox();
        // TV Box style: the tiles grow by themselves (tvbox_tab_scale), so no Leanback zoom on top.
        VerticalGridPresenter presenter = new VerticalGridPresenter(
                tvBox ? FocusHighlight.ZOOM_FACTOR_NONE : ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.FOCUS_DIMMER_ENABLED);
        if (tvBox) {
            presenter.setShadowEnabled(false); // TV Box style: the tiles carry their own look
        }
        presenter.enableChildRoundedCorners(!tvBox && getMainUIData().isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS));
        presenter.setNumberOfColumns(tvBox ? TVBOX_COLUMNS : GridFragmentHelper.getMaxColsNum(getContext(), R.dimen.settings_card_width));
        setGridPresenter(presenter);

        if (mSettingsAdapter == null) {
            SettingsCardPresenter gridPresenter = new SettingsCardPresenter();
            mSettingsAdapter = new ArrayObjectAdapter(gridPresenter);
            setAdapter(mSettingsAdapter);
        }
    }

    @Override
    public void clear() {
        if (mSettingsAdapter != null) {
            mSettingsAdapter.clear();
        }
    }

    @Override
    public boolean isEmpty() {
        if (mSettingsAdapter == null) {
            return mPendingUpdates.isEmpty();
        }

        return mSettingsAdapter.size() == 0;
    }

    @Override
    public void update(SettingsGroup group) {
        if (mSettingsAdapter == null) {
            mPendingUpdates.add(group);
            return;
        }

        // Always clear (continuation not supported)
        clear();

        if (group != null) {
            for (SettingsItem item : group.getItems()) {
                mSettingsAdapter.add(item);
            }
        }
    }

    private MainUIData getMainUIData() {
        return MainUIData.instance(getContext());
    }

    private GeneralData getGeneralData() {
        return GeneralData.instance(getContext());
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof SettingsItem) {
                String password = getGeneralData().getSettingsPassword();

                if (password == null) {
                    ((SettingsItem) item).onClick.run();
                } else {
                    SimpleEditDialog.showPassword(
                            getContext(),
                            getContext().getString(R.string.enter_settings_password),
                            null,
                            newValue -> {
                                if (Utils.passwordMatch(password, newValue)) {
                                    ((SettingsItem) item).onClick.run();
                                    return true;
                                }
                                return false;
                            });
                }

                // Close PIP inside Settings section
                //if (PlaybackPresenter.instance(getContext()).isInPipMode()) {
                //    PlaybackPresenter.instance(getContext()).forceFinish();
                //}
            } else {
                Toast.makeText(getContext(), item.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
