package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.os.Bundle;
import androidx.leanback.app.GuidedStepSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SignInActivity extends LeanbackActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == savedInstanceState) {
            if (com.liskovsoft.smartyoutubetv2.tv.util.TvBoxStyle.isSchemeActive(this)) {
                // TV Box build: YouTube-like welcome / sign-in page (options left, QR code right).
                boolean welcome = getIntent() != null && getIntent().getBooleanExtra(TvBoxSignInFragment.EXTRA_WELCOME, false);
                getSupportFragmentManager().beginTransaction()
                        .replace(android.R.id.content, TvBoxSignInFragment.newInstance(welcome))
                        .commit();
            } else {
                GuidedStepSupportFragment.addAsRoot(this, new SignInFragment(), android.R.id.content);
            }
        }
    }

    @Override
    public void finish() {
        super.finish();

        finishReally();
    }
}
