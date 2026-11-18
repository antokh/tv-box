package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BackgroundManager;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.TvBoxStyle;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class UriBackgroundManager {
    private static final String TAG = UriBackgroundManager.class.getSimpleName();
    private static final int BACKGROUND_UPDATE_DELAY_MS = 300;
    private Uri mBackgroundURI;
    private Drawable mDefaultBackground;
    private DisplayMetrics mMetrics;
    private Runnable mBackgroundTask;
    private BackgroundManager mBackgroundManager;
    private final Activity mActivity;
    private final Handler mHandler;
    private int mBackgroundColor = -1;
    private static final int AMBIENT_UPDATE_DELAY_MS = 350;
    private final boolean mAmbientEnabled;
    private String mAmbientUrl;
    private String mAmbientFallbackUrl;
    private int mHeroDim = 64;
    private final Runnable mAmbientTask = () -> {
        if (mAmbientUrl != null) {
            showAmbient(mAmbientUrl);
        }
    };

    public UriBackgroundManager(Activity activity) {
        mActivity = activity;
        mHandler = new Handler();
        // Browse screens only: the player has its own video behind the controls.
        mAmbientEnabled = TvBoxStyle.isEnabled(activity)
                && !(activity instanceof com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity);
        prepareBackgroundManager();
        setDefaultBackground();
    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(mActivity);
        mBackgroundManager.attach(mActivity.getWindow());
        mDefaultBackground = ContextCompat.getDrawable(mActivity, Helpers.getThemeAttr(mActivity, R.attr.shelfBackground));
        mBackgroundTask = new UpdateBackgroundTask();
        mMetrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void startBackgroundTimer(Uri backgroundURI) {
        mBackgroundURI = backgroundURI;
        mHandler.removeCallbacks(mBackgroundTask);
        mHandler.postDelayed(mBackgroundTask, BACKGROUND_UPDATE_DELAY_MS);
    }

    public void startBackgroundTimer(String bgImageUrl) {
        if (bgImageUrl != null) {
            startBackgroundTimer(Uri.parse(bgImageUrl));
        }
    }

    public void setBackgroundFrom(Video item) {
        // Selectively change background picture.
        // TV Box style: grids and other screens keep the plain dark base (Prime Video-like); the
        // artwork is only shown as the hero of the home rows, see setHeroFrom().
    }

    /** TV Box style: sharp artwork of the focused video, top-right, fading into the base colour. */
    public void setHeroFrom(Video item) {
        setHeroFrom(item, 64);
    }

    /** @param dimAlpha extra darkening (0-255): 64 for the Home hero, more behind rows/headers. */
    public void setHeroFrom(Video item, int dimAlpha) {
        mHeroDim = dimAlpha;
        if (!mAmbientEnabled || item == null) {
            return;
        }
        String url = item.videoId != null ? "https://i.ytimg.com/vi/" + item.videoId + "/maxresdefault.jpg" : item.cardImageUrl;
        if (url == null) {
            return;
        }
        mAmbientFallbackUrl = item.cardImageUrl;
        mAmbientUrl = url;
        mHandler.removeCallbacks(mAmbientTask);
        mHandler.postDelayed(mAmbientTask, AMBIENT_UPDATE_DELAY_MS);
    }

    /** TV Box style: back to the plain base (rows below the first one, menus...). */
    public void clearHero() {
        if (!mAmbientEnabled) {
            return;
        }
        mHandler.removeCallbacks(mAmbientTask);
        if (mAmbientUrl != null) {
            mAmbientUrl = null;
            if (mBackgroundManager != null) {
                setDefaultBackground();
            }
        }
    }

    private void showAmbient(final String url) {
        if (mBackgroundManager == null || mActivity.isFinishing() || mActivity.isDestroyed()) {
            return;
        }
        RequestOptions options = new RequestOptions().centerCrop();
        com.bumptech.glide.RequestBuilder<Bitmap> request = Glide.with(mActivity).asBitmap().load(url).apply(options);
        if (mAmbientFallbackUrl != null && !mAmbientFallbackUrl.equals(url)) {
            // maxresdefault doesn't exist for every video: fall back to the card's own picture.
            request = request.error(Glide.with(mActivity).asBitmap().load(mAmbientFallbackUrl).apply(options));
        }
        request.into(new SimpleTarget<Bitmap>(960, 540) {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, Transition<? super Bitmap> transition) {
                        // A newer selection may have arrived while this one loaded.
                        if (mBackgroundManager == null || !url.equals(mAmbientUrl)) {
                            return;
                        }
                        try {
                            mBackgroundManager.setBitmap(TvBoxStyle.heroBackdrop(mActivity, resource, mHeroDim));
                        } catch (RuntimeException | OutOfMemoryError e) {
                            Log.w(TAG, "hero backdrop failed", e);
                        }
                    }
                });
    }

    public void onStart() {
        if (mAmbientUrl != null) {
            showAmbient(mAmbientUrl);
        } else if (mBackgroundURI != null) {
            showBackground(mBackgroundURI.toString());
        } else if (mBackgroundColor != -1) {
            setBackgroundColor(mBackgroundColor);
        } else {
            setDefaultBackground();
        }
    }

    public void onStop() {
        mBackgroundManager.release();
    }

    public void onDestroy() {
        mHandler.removeCallbacks(mBackgroundTask);
        mHandler.removeCallbacks(mAmbientTask);
        mBackgroundManager = null;
    }

    public void removeBackground() {
        mBackgroundManager.setDrawable(null);
    }

    public void setDefaultBackground() {
        mBackgroundManager.setDrawable(mDefaultBackground);
    }

    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
        mBackgroundManager.setColor(color);
    }

    private class UpdateBackgroundTask implements Runnable {
        @Override
        public void run() {
            if (mBackgroundURI != null) {
                showBackground(mBackgroundURI.toString());
            }
        }
    }

    public BackgroundManager getBackgroundManager() {
        return mBackgroundManager;
    }

    public void showBackground(String uri) {
        View videoView = mActivity.findViewById(R.id.video_surface);

        if (videoView != null) {
            videoView.setVisibility(uri == null ? View.VISIBLE : View.INVISIBLE);
        }

        if (uri == null) {
            removeBackground();
            return;
        }

        int width = mMetrics.widthPixels;
        int height = mMetrics.heightPixels;

        RequestOptions options = ViewUtil.glideOptions()
                .centerCrop()
                .error(mDefaultBackground);

        Glide.with(mActivity)
                .asBitmap()
                .load(uri)
                .apply(options)
                .into(new SimpleTarget<Bitmap>(width, height) {
                    @Override
                    public void onResourceReady(
                            @NonNull Bitmap resource,
                            Transition<? super Bitmap> transition) {
                        mBackgroundManager.setBitmap(resource);
                    }
                });
    }

    public void showBackgroundColor(int colorResId) {
        try {
            showBackgroundColorInternal(colorResId);
        } catch (NullPointerException e) {
            // Likely caused by OOM in View.setVisibility
            Log.w(TAG, "showBackgroundColor: NPE swallowed", e);
        }
    }

    private void showBackgroundColorInternal(int colorResId) {
        View videoView = mActivity.findViewById(R.id.video_surface);

        if (videoView != null) {
            videoView.setVisibility(colorResId == -1 ? View.VISIBLE : View.INVISIBLE);
        }

        if (colorResId == -1) {
            removeBackground();
            return;
        }

        if (mBackgroundManager != null) {
            mBackgroundManager.setColor(ContextCompat.getColor(mActivity, colorResId));
        }
    }
}
