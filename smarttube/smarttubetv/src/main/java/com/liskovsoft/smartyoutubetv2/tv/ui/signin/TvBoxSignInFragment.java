package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

/**
 * TV Box build: welcome / sign-in page laid out like the official YouTube TV app
 * (options on the left, QR code and activation code on the right).
 * <p>
 * Used by {@link SignInActivity} when the TV Box scheme is active. With {@link #EXTRA_WELCOME} it is the
 * "Welcome" page that opens by itself at startup when nobody is signed in (see BrowseActivity);
 * otherwise it is the plain "Sign in" page opened from the rail.
 * Sign-in itself is unchanged: YouTube's device-code flow through {@link SignInPresenter}.
 */
public class TvBoxSignInFragment extends Fragment implements SignInView {
    public static final String EXTRA_WELCOME = "tvbox_welcome";

    private SignInPresenter mSignInPresenter;
    private ImageView mQrView;
    private View mProgress;
    private TextView mCodeView;
    private TextView mUrlView;
    private View mTvOption;
    private String mFullSignInUrl;

    // Self-healing: a code that failed, expired or is old is replaced automatically, so the page never
    // shows a raw error (it once showed "Refresh token is empty! ... slow_down" with debug data).
    /** Codes live ~30 min; replace one older than this when the page comes back. */
    private static final long CODE_MAX_AGE_MS = 25 * 60 * 1_000L;
    /** Automatic retries in a row before asking the user to press OK (e.g. no internet). */
    private static final int MAX_AUTO_RENEWALS = 6;
    private static final long RENEW_STEP_MS = 5_000L;
    private static final long RENEW_MAX_DELAY_MS = 30_000L;
    private final android.os.Handler mHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mRenew = this::requestNewCode;
    private boolean mNeedsNewCode = true;
    private long mCodeTime;
    private int mAutoRenewals;

    public static TvBoxSignInFragment newInstance(boolean welcome) {
        TvBoxSignInFragment fragment = new TvBoxSignInFragment();
        Bundle args = new Bundle();
        args.putBoolean(EXTRA_WELCOME, welcome);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSignInPresenter = SignInPresenter.instance(getContext());
        mSignInPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.tvbox_signin_welcome, container, false);
        boolean welcome = getArguments() != null && getArguments().getBoolean(EXTRA_WELCOME, false);

        ((TextView) root.findViewById(R.id.tvbox_welcome_title))
                .setText(welcome ? R.string.tvbox_welcome_title : R.string.tvbox_signin_title);
        ((TextView) root.findViewById(R.id.tvbox_option_guest_text))
                .setText(welcome ? R.string.tvbox_welcome_guest : R.string.tvbox_welcome_not_now);

        mQrView = root.findViewById(R.id.tvbox_signin_qr);
        mProgress = root.findViewById(R.id.tvbox_signin_progress);
        mCodeView = root.findViewById(R.id.tvbox_signin_code);
        mUrlView = root.findViewById(R.id.tvbox_signin_url);
        mTvOption = root.findViewById(R.id.tvbox_option_tv);

        View phone = root.findViewById(R.id.tvbox_option_phone);
        // The phone option is what the right side already shows: OK just keeps focus there.
        phone.setOnClickListener(v -> {
            if (mNeedsNewCode) { // after "code expired": OK gets a fresh one
                mAutoRenewals = 0;
                requestNewCode();
            }
        });
        mTvOption.setOnClickListener(v -> openOnThisTv());
        root.findViewById(R.id.tvbox_option_guest).setOnClickListener(v -> close());

        // "Sign in on this TV" only makes sense when a browser is installed.
        mTvOption.setVisibility(findBrowserPackage(getContext()) != null ? View.VISIBLE : View.GONE);

        phone.requestFocus();
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        boolean stale = mCodeTime > 0 && System.currentTimeMillis() - mCodeTime > CODE_MAX_AGE_MS;
        if (mNeedsNewCode || stale) {
            mAutoRenewals = 0;
            requestNewCode();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mHandler.removeCallbacks(mRenew);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mRenew);
        mSignInPresenter.onViewDestroyed();
    }

    /** Starts (or restarts) YouTube's device-code sign-in; the old code is dropped. */
    private void requestNewCode() {
        mHandler.removeCallbacks(mRenew);
        mNeedsNewCode = false;
        mCodeTime = 0;
        showStatus(getString(R.string.tvbox_welcome_loading), true);
        mSignInPresenter.setView(this);
        mSignInPresenter.onViewInitialized();
    }

    /** A failed / expired code: retry quietly, with growing pauses; after a few tries wait for OK. */
    private void onCodeFailed(String error) {
        com.liskovsoft.sharedutils.mylogger.Log.e("TvBoxSignInFragment", "Sign-in code failed: %s", error);
        mCodeTime = 0;
        mAutoRenewals++;
        if (mAutoRenewals > MAX_AUTO_RENEWALS) {
            mNeedsNewCode = true;
            showStatus(getString(R.string.tvbox_welcome_code_expired), false);
            return;
        }
        showStatus(getString(R.string.tvbox_welcome_loading), true);
        if (isResumed()) {
            mHandler.postDelayed(mRenew, Math.min(RENEW_STEP_MS * mAutoRenewals, RENEW_MAX_DELAY_MS));
        } else {
            mNeedsNewCode = true; // renewed in onStart when the page is visible again
        }
    }

    private void showStatus(String text, boolean loading) {
        if (mCodeView == null) {
            return;
        }
        mFullSignInUrl = null;
        mQrView.setImageDrawable(null);
        mProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        mCodeView.setTextSize(20);
        mCodeView.setText(text);
    }

    @Override
    public void showCode(String userCode, String signInUrl) {
        showCode(userCode, signInUrl, null);
    }

    @Override
    public void showCode(String userCode, String signInUrl, String fullSignInUrl) {
        if (getContext() == null || mCodeView == null || TextUtils.isEmpty(userCode)) {
            return;
        }

        if (TextUtils.isEmpty(signInUrl)) {
            // The presenter passes an error message as the "code" with an empty url. Never show it.
            onCodeFailed(userCode);
            return;
        }

        mHandler.removeCallbacks(mRenew);
        mCodeTime = System.currentTimeMillis();
        mCodeView.setTextSize(32);
        mCodeView.setText(userCode);
        mUrlView.setText(signInUrl.replaceFirst("^https?://", "")); // "yt.be/activate" reads easier
        mFullSignInUrl = fullSignInUrl != null ? fullSignInUrl : signInUrl;

        Glide.with(getContext())
                .load(Utils.toQrCodeLink(mFullSignInUrl))
                .apply(ViewUtil.glideOptions())
                .error(R.drawable.activate_account_qrcode)
                .into(mQrView);
        mProgress.setVisibility(View.GONE);
    }

    private void openOnThisTv() {
        String browser = findBrowserPackage(getContext());
        if (mFullSignInUrl == null || browser == null) {
            return;
        }
        try {
            // Force a real web browser: yt.be / youtube.com links would otherwise open the official YouTube app.
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(mFullSignInUrl))
                    .setPackage(browser)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            mTvOption.setVisibility(View.GONE);
        }
    }

    /** A web browser on the TV (resolved with a non-YouTube address so the YouTube app doesn't match), or null. */
    @Nullable
    private static String findBrowserPackage(Context context) {
        if (context == null) {
            return null;
        }
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE);
        for (ResolveInfo info : context.getPackageManager().queryIntentActivities(probe, 0)) {
            String pkg = info.activityInfo != null ? info.activityInfo.packageName : null;
            if (pkg != null && !pkg.equals(context.getPackageName()) && !pkg.startsWith("com.google.android.youtube")) {
                return pkg;
            }
        }
        return null;
    }

    @Override
    public void close() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }
}
