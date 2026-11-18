package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.HeadersSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.PageRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.TitleHelper;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.IconHeaderItemPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog.ErrorDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.headers.ExtendedHeadersSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;

import java.util.HashMap;
import java.util.Map;

/*
 * Main class to show BrowseFragment with header and rows of videos
 */
public class BrowseFragment extends BrowseSupportFragment implements BrowseView {
    private static final String TAG = BrowseFragment.class.getSimpleName();
    private ArrayObjectAdapter mSectionRowAdapter;
    private BrowsePresenter mBrowsePresenter;
    private Map<Integer, BrowseSection> mSections;
    private BrowseSectionFragmentFactory mSectionFragmentFactory;
    private Handler mHandler;
    private ProgressBarManager mProgressBarManager;
    private boolean mIsFragmentCreated;
    private boolean mFocusOnContent;
    private CrashRestorer mCrashRestorer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);

        if (getContext() == null) {
            return;
        }
        
        mCrashRestorer = new CrashRestorer(getContext(), savedInstanceState);
        mIsFragmentCreated = true;

        mSections = new HashMap<>();
        mHandler = new Handler();
        mBrowsePresenter = BrowsePresenter.instance(getContext());
        mBrowsePresenter.setView(this);
        mProgressBarManager = new ProgressBarManager();

        setupAdapter();
        setupFragmentFactory();
        setupUi();

        enableMainFragmentScaling(false);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Called when the activity is paused
        mCrashRestorer.persistHeaderIndex(outState, getSelectedPosition());
        mCrashRestorer.persistVideo(outState, mBrowsePresenter.getCurrentVideo());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        mProgressBarManager.setRootView((ViewGroup) root);

        if (com.liskovsoft.smartyoutubetv2.tv.util.TvBoxStyle.isEnabled(getContext()) && root instanceof ViewGroup) {
            // TV Box style (Prime Video-like): hero info under the rows, section tabs at the top
            // instead of the big left menu.
            mTvBoxHome = new TvBoxHome((ViewGroup) root);
            View titleView = getTitleView();
            android.widget.HorizontalScrollView tabsScroll = titleView != null ? titleView.findViewById(R.id.tvbox_tabs_scroll) : null;
            android.widget.LinearLayout tabsStrip = titleView != null ? titleView.findViewById(R.id.tvbox_tabs) : null;
            if (tabsScroll != null && tabsStrip != null) {
                mTvBoxTabs = new TvBoxTabs(tabsScroll, tabsStrip, null, new TvBoxTabs.Listener() {
                    @Override
                    public void onTabFocused(int index) {
                        switchTvBoxSection(tabToPosition(index), false);
                    }

                    @Override
                    public void onTabClicked(int index) {
                        switchTvBoxSection(tabToPosition(index), true);
                    }
                });
            }
            // Prime Video-style rail: Profile, Search, Settings (they left the top bar and the tabs).
            mTvBoxRail = new TvBoxRail((ViewGroup) root, new TvBoxRail.Listener() {
                @Override
                public void onProfile() {
                    // No account yet: straight to the sign-in page (simplest); otherwise the accounts list.
                    if (com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.instance().getSelectedAccount() == null) {
                        com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter.instance(getContext()).start();
                    } else {
                        com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter.instance(getContext()).nextAccountOrDialog();
                    }
                }

                @Override
                public void onSearch() {
                    com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter.instance(getContext()).startSearch(null);
                }

                @Override
                public void onSettings() {
                    int position = findSectionPosition(com.liskovsoft.mediaserviceinterfaces.data.MediaGroup.TYPE_SETTINGS);
                    if (position >= 0) {
                        switchTvBoxSection(position, true);
                    }
                }

                @Override
                public void onLeaveRail() {
                    focusOnContent();
                }
            });

            // Moving Left towards the (hidden) section list lands in the rail instead.
            View browseFrame = root.findViewById(androidx.leanback.R.id.browse_frame);
            if (browseFrame instanceof androidx.leanback.widget.BrowseFrameLayout) {
                final androidx.leanback.widget.BrowseFrameLayout frame = (androidx.leanback.widget.BrowseFrameLayout) browseFrame;
                final androidx.leanback.widget.BrowseFrameLayout.OnFocusSearchListener original = frame.getOnFocusSearchListener();
                frame.setOnFocusSearchListener((focused, direction) -> {
                    View result = original != null ? original.onFocusSearch(focused, direction) : null;
                    if (result != null && isInHeaders(result)) {
                        View railItem = mTvBoxRail.getFocusTarget();
                        return railItem != null ? railItem : focused;
                    }
                    return result;
                });
            }
        }

        return root;
    }

    private TvBoxHome mTvBoxHome;
    private TvBoxTabs mTvBoxTabs;
    private TvBoxRail mTvBoxRail;
    /** Tab index -> section position (Settings is in the rail, not in the tabs). */
    private final java.util.List<Integer> mTabPositions = new java.util.ArrayList<>();

    private boolean isInHeaders(View view) {
        if (getHeadersSupportFragment() == null || getHeadersSupportFragment().getView() == null) {
            return false;
        }
        View headers = getHeadersSupportFragment().getView();
        for (View v = view; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v == headers) {
                return true;
            }
        }
        return false;
    }

    private int tabToPosition(int tabIndex) {
        return tabIndex >= 0 && tabIndex < mTabPositions.size() ? mTabPositions.get(tabIndex) : tabIndex;
    }

    private int findSectionPosition(int sectionId) {
        if (mSectionRowAdapter == null) {
            return -1;
        }
        for (int i = 0; i < mSectionRowAdapter.size(); i++) {
            Object o = mSectionRowAdapter.get(i);
            if (o instanceof androidx.leanback.widget.Row && ((androidx.leanback.widget.Row) o).getHeaderItem() != null
                    && ((androidx.leanback.widget.Row) o).getHeaderItem().getId() == sectionId) {
                return i;
            }
        }
        return -1;
    }
    private final Runnable mUpdateTvBoxTabs = this::updateTvBoxTabs;

    /** Prime-style hero helper, or null when the TV Box colour scheme isn't used. */
    public TvBoxHome getTvBoxHome() {
        return mTvBoxHome;
    }

    /** True while the top tabs replace the left menu. */
    public boolean hasTvBoxTabs() {
        return mTvBoxTabs != null;
    }

    private void postUpdateTvBoxTabs() {
        if (mTvBoxTabs != null && getView() != null) {
            getView().removeCallbacks(mUpdateTvBoxTabs);
            getView().post(mUpdateTvBoxTabs);
        }
    }

    private void updateTvBoxTabs() {
        if (mTvBoxTabs == null || mSectionRowAdapter == null) {
            return;
        }
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<Integer> icons = new java.util.ArrayList<>();
        mTabPositions.clear();
        int selectedTab = 0;
        int selected = Math.max(0, getSelectedPosition());
        for (int i = 0; i < mSectionRowAdapter.size(); i++) {
            Object o = mSectionRowAdapter.get(i);
            String name = null;
            if (o instanceof androidx.leanback.widget.Row && ((androidx.leanback.widget.Row) o).getHeaderItem() != null) {
                androidx.leanback.widget.HeaderItem header = ((androidx.leanback.widget.Row) o).getHeaderItem();
                if (header.getId() == com.liskovsoft.mediaserviceinterfaces.data.MediaGroup.TYPE_SETTINGS) {
                    continue; // lives in the rail
                }
                name = header.getName();
            }
            if (i == selected) {
                selectedTab = labels.size();
            }
            mTabPositions.add(i);
            labels.add(name != null ? name : "");
            Object headerItem = o instanceof androidx.leanback.widget.Row ? ((androidx.leanback.widget.Row) o).getHeaderItem() : null;
            icons.add(headerItem instanceof SectionHeaderItem ? ((SectionHeaderItem) headerItem).getResId() : 0);
        }
        // On the Settings page no tab is the open one.
        mTvBoxTabs.setTabs(labels, icons, mTabPositions.contains(selected) ? selectedTab : -1);
    }

    /** Swap the page under the tabs with a quick fade/slide (Prime Video-like). */
    private void switchTvBoxSection(int index, boolean enterContent) {
        View container = getView() != null ? getView().findViewById(androidx.leanback.R.id.browse_container_dock) : null;
        if (index == getSelectedPosition()) {
            if (enterContent) {
                focusOnContent();
            }
            return;
        }
        if (mTvBoxHome != null) {
            mTvBoxHome.hideHero();
        }
        if (container == null) {
            selectSection(index, enterContent);
            return;
        }
        final float slide = 24 * getResources().getDisplayMetrics().density;
        container.animate().cancel();
        container.animate().alpha(0f).translationY(slide / 2).setDuration(120)
                .withEndAction(() -> {
                    selectSection(index, enterContent);
                    container.setTranslationY(slide);
                    container.animate().alpha(1f).translationY(0f).setDuration(300)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
                }).start();
    }

    /** Back from the page goes up to the tabs first (Prime Video-like); Back on the tabs exits as usual. */
    public boolean onTvBoxBack() {
        if (mTvBoxTabs == null) {
            return false;
        }
        if (mTvBoxRail != null && mTvBoxRail.hasFocus()) {
            focusOnContent();
            return true;
        }
        View titleView = getTitleView();
        if (titleView != null && titleView.hasFocus()) {
            return false;
        }
        Fragment main = getMainFragment();
        if (main instanceof androidx.leanback.app.RowsSupportFragment) {
            ((androidx.leanback.app.RowsSupportFragment) main).setSelectedPosition(0, true);
        }
        showTitle(true);
        View tab = mTvBoxTabs.getSelectedTab();
        if (tab != null) {
            tab.requestFocus();
            return true;
        }
        return false;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setupEventListeners();

        prepareEntranceTransition();

        mBrowsePresenter.onViewInitialized();

        // Restore state after crash
        mCrashRestorer.restoreHeader((idx, video) -> {
            selectSection(idx, true);
            selectSectionItem(video);
        });
        mCrashRestorer.restorePlayback();
    }

    @Override
    public HeadersSupportFragment onCreateHeadersSupportFragment() {
        return new ExtendedHeadersSupportFragment();
    }

    private void setupEventListeners() {
        getHeadersSupportFragment().setOnHeaderClickedListener(
                (viewHolder, row) -> {
                    long headerId = row.getHeaderItem().getId();
                    int newPosition = indexOf(headerId);

                    if (getHeadersSupportFragment().getSelectedPosition() != newPosition) {
                        // touch screen support
                        getHeadersSupportFragment().setSelectedPosition(newPosition);
                    } else {
                        // update section when clicked or pressed
                        mBrowsePresenter.onSectionFocused((int) headerId);
                        startHeadersTransitionSafe(false);
                    }
                }
        );

        ((ExtendedHeadersSupportFragment) getHeadersSupportFragment()).setOnHeaderLongPressedListener(
                (viewHolder, row) -> {
                    long headerId = row.getHeaderItem().getId();

                    mBrowsePresenter.onSectionLongPressed((int) headerId);
                }
        );

        setOnSearchClickedListener(view -> SearchPresenter.instance(getContext()).startSearch(null));
    }

    private void setupFragmentFactory() {
        mSectionFragmentFactory = new BrowseSectionFragmentFactory(
                (row) -> {
                    focusOnContentIfNeeded();
                    mBrowsePresenter.onSectionFocused(getSelectedHeaderId());
                }
        );

        getMainFragmentRegistry().registerFragment(PageRow.class, mSectionFragmentFactory);
    }

    private int indexOf(long headerId) {
        for (int i = 0; i < mSectionRowAdapter.size(); i++) {
            PageRow row = (PageRow) mSectionRowAdapter.get(i);
            HeaderItem header = row.getHeaderItem();
            if (header.getId() == headerId) {
                return i;
            }
        }

        return 0;
    }

    private void setupAdapter() {
        // Map category results from the database to ListRow objects.
        // This Adapter is used to render the MainFragment sidebar labels.
        mSectionRowAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mSectionRowAdapter);
    }

    private void setupUi() {
        if (getContext() == null) {
            return;
        }

        // TV Box style: sections are tabs at the top, no left menu.
        // Headers stay enabled internally (sections load through them); in the TV Box style they are
        // never shown: Left goes to the rail instead (see onCreateView) and Back doesn't open them.
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(!com.liskovsoft.smartyoutubetv2.tv.util.TvBoxStyle.isEnabled(getContext()));

        int brandColorRes = Helpers.getThemeAttr(getContext(), R.attr.brandColor);
        int brandAccentColorRes = Helpers.getThemeAttr(getContext(), R.attr.brandAccentColor);

        updateBadge();

        // This title replaces badge in case one is null
        //setTitle(getString(R.string.browse_title));

        // Set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(getContext(), brandColorRes));

        // Set search icon color.
        setSearchAffordanceColor(ContextCompat.getColor(getContext(), brandAccentColorRes));

        setHeaderPresenterSelector(new PresenterSelector() {
            private final Map<Integer, Presenter> mPresenterMap = new HashMap<>();

            @Override
            public Presenter getPresenter(Object o) {
                Presenter presenter = mPresenterMap.get(o.hashCode());

                if (presenter == null) {
                    presenter = new IconHeaderItemPresenter(getHeaderResId(o), getIconUrl(o));
                    mPresenterMap.put(o.hashCode(), presenter);
                }

                return presenter;
            }

            private int getHeaderResId(Object o) {
                if (o instanceof PageRow) {
                    return ((SectionHeaderItem) ((PageRow) o).getHeaderItem()).getResId();
                }

                return -1;
            }

            private String getIconUrl(Object o) {
                if (o instanceof PageRow) {
                    return ((SectionHeaderItem) ((PageRow) o).getHeaderItem()).getIconUrl();
                }

                return null;
            }
        });
    }

    private int getSelectedHeaderId() {
        if (getSelectedPosition() >= mSectionRowAdapter.size()) {
            return -1;
        }

        return (int) ((PageRow) mSectionRowAdapter.get(getSelectedPosition())).getHeaderItem().getId();
    }
    
    public void updateErrorIfEmpty(ErrorFragmentData data) {
        mHandler.postDelayed(() -> showErrorIfEmpty(data), 500); // need delay because header may be not updated
    }

    @Override
    public void showError(ErrorFragmentData data) {
        replaceMainFragment(new ErrorDialogFragment(data));
    }

    private void showErrorIfEmpty(ErrorFragmentData data) {
        if (isEmpty()) {
            replaceMainFragment(new ErrorDialogFragment(data));
        }
    }

    private void replaceMainFragment(Fragment fragment) {
        //Object mainFragment = Helpers.getField(this,"mMainFragment");
        Fragment mainFragment = getMainFragment();

        if (mainFragment != null && fragment != null && mainFragment != fragment) {
            Helpers.setField(this, "mMainFragment", fragment);

            FragmentTransaction ft = getChildFragmentManager().beginTransaction();
            ft.replace(R.id.scale_frame, fragment);
            //mFocusOnContent = !isShowingHeaders(); // Fix focus lost when error fragment shown and sidebar is hidden
            mFocusOnContent = hasFocus(); // Maintain focus
            ft.runOnCommit(this::focusOnContentIfNeeded);
            ft.commitAllowingStateLoss(); // FIX: "Can not perform this action after onSaveInstanceState"
        }
    }

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null) {
            return;
        }

        if (mSections.get(section.getId()) != null && (index == -1 || indexOf(section.getId()) == index)) {
            return;
        }

        removeSection(section);

        mSections.put(section.getId(), section);
        createHeader(index, section);
        postUpdateTvBoxTabs();
    }

    @Override
    public void removeSection(BrowseSection section) {
        if (section == null) {
            return;
        }

        mSections.remove(section.getId());
        removeHeader(section);
        postUpdateTvBoxTabs();
    }

    @Override
    public void removeAllSections() {
        mSections.clear();
        mSectionRowAdapter.clear();
        postUpdateTvBoxTabs();
    }

    @Override
    public void updateSection(VideoGroup group) {
        restoreMainFragment();

        mSectionFragmentFactory.updateCurrentFragment(group);

        fixInvisibleSearchOrb();
    }

    @Override
    public void updateSection(SettingsGroup group) {
        restoreMainFragment();

        mSectionFragmentFactory.updateCurrentFragment(group);
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (index >= 0 && mSectionRowAdapter.size() > 0) {
            mFocusOnContent = focusOnContent; // focus after header transition

            // Fix refresh current section
            if (getSelectedPosition() == index) {
                // update section manually
                // headers transition event not fired on the same index
                focusOnContentIfNeeded();
                mBrowsePresenter.onSectionFocused(getSelectedHeaderId());
            }

            // Need select again if current header is removed previously (can't check for it right now)
            // Fallback to the last section if index above size
            setSelectedPosition(index < mSectionRowAdapter.size() ? index : mSectionRowAdapter.size() - 1, false);
            postUpdateTvBoxTabs();
        }
    }

    @Override
    public void focusOnContent() {
        startHeadersTransitionSafe(false);
        if (getMainFragment() != null && getMainFragment().getView() != null) {
            getMainFragment().getView().requestFocus();
        }
    }

    /**
     * Usually called after header transition or fragment transaction
     */
    private void focusOnContentIfNeeded() {
        if (mFocusOnContent) {
            focusOnContent();
            mFocusOnContent = false;
        }
    }

    private boolean hasFocus() {
        if (getMainFragment() == null || getMainFragment().getView() == null) {
            return false;
        }

        return getMainFragment().getView().hasFocus();
    }

    @Override
    public void selectSectionItem(int index) {
        if (index >= 0) {
            mSectionFragmentFactory.setCurrentFragmentItemIndex(index);
        }
    }

    @Override
    public void selectSectionItem(Video item) {
        if (item != null) {
            mSectionFragmentFactory.selectCurrentFragmentItem(item);
        }
    }

    /**
     * Fix: IllegalStateException: "Can not perform this action after onSaveInstanceState"
     */
    private void startHeadersTransitionSafe(boolean withHeaders) {
        // Fix: IllegalStateException: "Can not perform this action after onSaveInstanceState"
        if (!Utils.checkActivity(getActivity())) {
            return;
        }

        try {
            startHeadersTransition(withHeaders);
        } catch (IllegalStateException e) {
            // NOP
        }
    }

    /**
     * Restore after the error fragment
     */
    private void restoreMainFragment() {
        Fragment currentFragment = mSectionFragmentFactory.getCurrentFragment();

        if (currentFragment != null) {
            replaceMainFragment(currentFragment);
        }
    }

    private void createHeader(int index, BrowseSection header) {
        HeaderItem headerItem = new SectionHeaderItem(header);

        PageRow pageRow = new PageRow(headerItem);
        if (index == -1 || mSectionRowAdapter.size() < index) {
            mSectionRowAdapter.add(pageRow); // add to the end
        } else {
            mSectionRowAdapter.add(index, pageRow);
        }
    }

    private void removeHeader(BrowseSection header) {
        Object foundHeader = null;

        for (Object item : mSectionRowAdapter.unmodifiableList()) {
            if (((PageRow) item).getHeaderItem().getId() == header.getId()) {
                foundHeader = item;
                break;
            }
        }

        if (foundHeader != null) {
            mSectionRowAdapter.remove(foundHeader);
        }
    }

    @Override
    public void clearSection(BrowseSection section) {
        mSectionFragmentFactory.clearCurrentFragment();
    }

    @Override
    public void onDestroyView() {
        mSectionFragmentFactory.cleanup();

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBrowsePresenter.onViewDestroyed();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mTvBoxHome != null) {
            mTvBoxHome.pausePreview();
        }

        if (!mIsFragmentCreated) {
            mBrowsePresenter.onViewPaused();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mTvBoxHome != null) {
            mTvBoxHome.resumePreview();
        }

        if (!mIsFragmentCreated) {
            mBrowsePresenter.onViewResumed();
        }

        mIsFragmentCreated = false;
    }

    /**
     * Fix suddenly invisible search orb<br/>
     * Could happen on topmost category when the page partially scrolled<br/>
     * More info: {@link TitleHelper}
     */
    private void fixInvisibleSearchOrb() {
        if (isShowingTitle() && getTitleView() != null && getTitleView().getVisibility() != View.VISIBLE) {
            getTitleView().setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        Runnable callback;

        if (show) {
            callback = mProgressBarManager::show;
        } else {
            callback = mProgressBarManager::hide;
        }

        // Essential. Need to run on the main thread.
        new Handler(Looper.getMainLooper()).post(callback);
    }

    @Override
    public boolean isProgressBarShowing() {
        return mProgressBarManager.isShowing();
    }

    @Override
    public boolean isEmpty() {
        return mSectionFragmentFactory == null || mSectionFragmentFactory.isEmpty();
    }

    @Override
    public void updateBadge() {
        if (getContext() == null) {
            return;
        }

        SplashPresenter splashPresenter = SplashPresenter.instance(getContext());

        if (splashPresenter == null) {
            return;
        }

        int appLogoRes = Helpers.getThemeAttr(getContext(), R.attr.appLogo);

        Drawable bridgeIcon = Utils.getDrawable(getContext(), splashPresenter.getBridgePackageName(), "app_icon");

        // Top right corner logo
        setBadgeDrawable(bridgeIcon != null ? bridgeIcon : appLogoRes > 0 ? ContextCompat.getDrawable(getContext(), appLogoRes) : null);
    }
}
