package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.List;

public class SignInFragment extends GuidedStepSupportFragment implements SignInView {
    private static final String TAG = SignInFragment.class.getSimpleName();
    private static final int CONTINUE = 2;
    private static final int OPEN_BROWSER = 3;
    private SignInPresenter mSignInPresenter;
    private String mFullSignInUrl;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSignInPresenter = SignInPresenter.instance(getContext());
        mSignInPresenter.setView(this);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSignInPresenter.onViewInitialized();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSignInPresenter.onViewDestroyed();
    }

    @Override
    public void showCode(String userCode, String signInUrl) {
        setTitle(userCode, signInUrl, null);
    }

    @Override
    public void showCode(String userCode, String signInUrl, String fullSignInUrl) {
        setTitle(userCode, signInUrl, fullSignInUrl);
    }

    private void setTitle(String userCode, String signInUrl, String fullSignInUrl) {
        if (getContext() == null || TextUtils.isEmpty(userCode)) {
            return;
        }

        getGuidanceStylist().getTitleView().setText(userCode);

        mFullSignInUrl = fullSignInUrl != null ? fullSignInUrl : signInUrl;

        Glide.with(getContext())
                .load(Utils.toQrCodeLink(mFullSignInUrl))
                .placeholder(R.drawable.activate_account_qrcode)
                .apply(ViewUtil.glideOptions())
                .error(R.drawable.activate_account_qrcode)
                .listener(mErrorListener)
                .into(getGuidanceStylist().getIconView());

        if (isTvBox()) {
            signInUrl = signInUrl.replaceFirst("^https?://", ""); // "yt.be/activate" reads easier
        }
        String description = isTvBox() ? getString(R.string.tvbox_signin_description, signInUrl)
                : getString(R.string.signin_view_description, signInUrl);
        int start = description.indexOf(signInUrl);
        int end = start + signInUrl.length();
        CharSequence coloredDescription = Utils.color(description, ContextCompat.getColor(getContext(),
                isTvBox() ? R.color.tvbox_focus : R.color.red), start, end);

        getGuidanceStylist().getDescriptionView().setText(coloredDescription);
    }

    // ------------------------------------------------------------------ TV Box (Prime Video-like) sign-in page

    private boolean isTvBox() {
        return com.liskovsoft.smartyoutubetv2.tv.util.TvBoxStyle.isSchemeActive(getContext());
    }

    @Override
    public int onProvideTheme() {
        return isTvBox() ? R.style.TvBox_GuidedStep : super.onProvideTheme();
    }

    @Override
    public androidx.leanback.widget.GuidanceStylist onCreateGuidanceStylist() {
        // Created in the constructor (no context yet): decide the layout when the view is built.
        return new androidx.leanback.widget.GuidanceStylist() {
            @Override
            public int onProvideLayoutId() {
                return isTvBox() ? R.layout.tvbox_signin_guidance : super.onProvideLayoutId();
            }
        };
    }

    @Override
    public androidx.leanback.widget.GuidedActionsStylist onCreateActionsStylist() {
        // Created in the constructor (no context yet): style the rows when they are built.
        return new androidx.leanback.widget.GuidedActionsStylist() {
            @Override
            public ViewHolder onCreateViewHolder(android.view.ViewGroup parent) {
                ViewHolder vh = super.onCreateViewHolder(parent);
                if (!isTvBox()) {
                    return vh;
                }
                vh.itemView.setBackground(ContextCompat.getDrawable(parent.getContext(), R.drawable.tvbox_signin_action));
                if (vh.getTitleView() != null) {
                    vh.getTitleView().setTextColor(0xFFFFFFFF);
                    vh.getTitleView().setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
                    vh.getTitleView().setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                }
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    vh.itemView.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(parent.getContext(), R.animator.tvbox_tab_scale));
                }
                return vh;
            }
        };
    }

    @Override
    public void close() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    @NonNull
    public GuidanceStylist.Guidance onCreateGuidance(@NonNull Bundle savedInstanceState) {
        String title = getString(R.string.signin_view_title);
        String description = getString(R.string.signin_view_description, "");
        return new GuidanceStylist.Guidance(title, description, "", ContextCompat.getDrawable(getContext(), R.drawable.activate_account_qrcode));
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        GuidedAction login = new GuidedAction.Builder()
                .id(CONTINUE)
                .title(getString(R.string.signin_view_action_text))
                .build();
        GuidedAction openBrowser = new GuidedAction.Builder()
                .id(OPEN_BROWSER)
                .title(getString(R.string.login_from_browser))
                .build();
        actions.add(login);
        actions.add(openBrowser);
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == CONTINUE) {
            mSignInPresenter.onActionClicked();
        } else if (action.getId() == OPEN_BROWSER) {
            if (mFullSignInUrl != null) {
                Utils.openLinkExt(getContext(), mFullSignInUrl);
            }
        }
    }

    private final RequestListener<Drawable> mErrorListener = new RequestListener<Drawable>() {
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
            Log.e(TAG, "Glide load failed: " + e);
            return false;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            return false;
        }
    };
}
