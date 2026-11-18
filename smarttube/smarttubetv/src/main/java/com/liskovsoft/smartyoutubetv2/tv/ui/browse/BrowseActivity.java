package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.os.Bundle;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class BrowseActivity extends LeanbackActivity {
    private static final String TAG = BrowseActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.fragment_main);
        } catch (NoClassDefFoundError e) {
            // Failed resolution of: Landroidx/lifecycle/ViewTreeLifecycleOwner;
            MessageHelpers.showMessage(this, e.getMessage());
        }

        if (savedInstanceState == null) {
            showWelcomeIfSignedOut();
        }
    }

    /** Shown once per app start, only while nobody is signed in. "Watch as guest" closes it. */
    private static boolean sWelcomeShown;

    /**
     * TV Box build: like the official YouTube TV app, open the welcome / sign-in page
     * (TvBoxSignInFragment) when DodoTube starts and no account is signed in.
     */
    private void showWelcomeIfSignedOut() {
        if (sWelcomeShown || !com.liskovsoft.smartyoutubetv2.tv.util.TvBoxStyle.isSchemeActive(this)) {
            return;
        }
        sWelcomeShown = true;
        try {
            if (com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance().getSignInService().isSigned()) {
                return;
            }
            startActivity(new android.content.Intent(this, com.liskovsoft.smartyoutubetv2.tv.ui.signin.SignInActivity.class)
                    .putExtra(com.liskovsoft.smartyoutubetv2.tv.ui.signin.TvBoxSignInFragment.EXTRA_WELCOME, true));
        } catch (RuntimeException e) {
            // Never block the home screen because of the welcome page.
            com.liskovsoft.sharedutils.mylogger.Log.e(TAG, "Welcome page failed: %s", e.getMessage());
        }
    }

    @Override
    public void onBackPressed() {
        // TV Box build: Back from a page first returns to the top tabs (see BrowseFragment#onTvBoxBack).
        androidx.fragment.app.Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_frame);
        if (fragment instanceof BrowseFragment && ((BrowseFragment) fragment).onTvBoxBack()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void initTheme() {
        int browseThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (browseThemeResId > 0) {
            setTheme(browseThemeResId);
        }
    }
}
