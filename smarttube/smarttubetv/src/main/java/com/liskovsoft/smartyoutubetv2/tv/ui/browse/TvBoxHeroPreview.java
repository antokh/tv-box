package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.embedplayer.EmbedPlayerView;

/**
 * TV Box build: the focused video of the first row plays, muted, in the hero area at the top of the
 * home screen (Netflix / Prime Video-like), exactly where its artwork is drawn
 * ({@code TvBoxStyle#heroBackdrop}: 16:9, 76 % of the width, top-right, fading left and down).
 * <p>
 * It starts after the focus has rested on a video for a moment, fades in once the first frames play,
 * and stops when the focus moves, the hero hides, or the screen is left (a full player is opened).
 * While it owns the hero, the small preview inside the focused card is skipped
 * ({@link #isActive()}): both use the one shared playback engine.
 */
public class TvBoxHeroPreview {
    private static final String TAG = TvBoxHeroPreview.class.getSimpleName();
    /** Focus must rest this long before loading a video (scrolling along a row loads nothing). */
    private static final long START_DELAY_MS = 1_500;
    private static final long FIRST_FRAME_POLL_MS = 300;
    private static final long FIRST_FRAME_TIMEOUT_MS = 20_000;
    private static final int FADE_IN_MS = 600;
    private static final int FADE_OUT_MS = 200;
    /** Same geometry and darkening as the static backdrop, so the switch is seamless. */
    private static final float ART_WIDTH_FRACTION = 0.76f;
    private static final int DIM_ALPHA = 64;

    private static volatile boolean sActive;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final FrameLayout mFrame;
    private final Context mContext;
    private EmbedPlayerView mPlayer;
    private Video mVideo;
    private long mWaitStartedMs;
    private boolean mStartPending; // Handler#hasCallbacks is Android 10+, the box runs 9
    private final Runnable mStart = this::startPlayer;
    private final Runnable mWaitFirstFrame = this::waitFirstFrame;

    /** True while the hero owns (or is about to own) the playback engine: card previews stay off. */
    public static boolean isActive() {
        return sActive;
    }

    public TvBoxHeroPreview(ViewGroup root) {
        mContext = root.getContext();
        int screenW = mContext.getResources().getDisplayMetrics().widthPixels;
        int w = Math.round(screenW * ART_WIDTH_FRACTION);
        int h = Math.round(w * 9f / 16f);

        mFrame = new FrameLayout(mContext);
        mFrame.setFocusable(false);
        mFrame.setAlpha(0f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.gravity = Gravity.TOP | Gravity.END;
        mFrame.setLayoutParams(lp);
        mFrame.addView(new FadeOverlay(mContext), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(mFrame, 0); // under the hero text and the rows
    }

    /** The hero now shows this video: (re)start the preview for it after a short rest. */
    public void show(Video video) {
        if (video == null || TextUtils.isEmpty(video.videoId)) {
            hide();
            return;
        }
        if (mVideo != null && video.videoId.equals(mVideo.videoId) && (mPlayer != null || mStartPending)) {
            return; // same video, already playing or about to
        }
        stopPlayer();
        mVideo = video;
        sActive = true;
        mStartPending = true;
        mHandler.postDelayed(mStart, START_DELAY_MS);
    }

    /** Hero hidden (lower row focused, another tab...): stop and give the engine back to the cards. */
    public void hide() {
        stopPlayer();
        mVideo = null;
        sActive = false;
    }

    /** Screen left (a video opened, Home pressed...): stop now, resume the same video on {@link #resume()}. */
    public void pause() {
        stopPlayer();
        sActive = false;
    }

    public void resume() {
        if (mVideo != null && mPlayer == null) {
            sActive = true;
            mHandler.removeCallbacks(mStart);
            mStartPending = true;
            mHandler.postDelayed(mStart, START_DELAY_MS);
        }
    }

    private void startPlayer() {
        mStartPending = false;
        if (mVideo == null || mPlayer != null) {
            return;
        }
        try {
            // Inflated so it gets a TextureView (see tvbox_hero_player.xml); set in code, it'd be a SurfaceView.
            EmbedPlayerView player = (EmbedPlayerView) LayoutInflater.from(mContext)
                    .inflate(R.layout.tvbox_hero_player, mFrame, false);
            player.setQuality(EmbedPlayerView.QUALITY_NORMAL);
            player.setMute(true);
            player.setBackgroundColor(Color.TRANSPARENT);
            mFrame.addView(player, 0);
            mPlayer = player;
            player.openVideo(mVideo);
            mWaitStartedMs = System.currentTimeMillis();
            mHandler.postDelayed(mWaitFirstFrame, FIRST_FRAME_POLL_MS);
        } catch (RuntimeException e) {
            // A preview must never break the home screen: the artwork simply stays.
            Log.e(TAG, "Hero preview failed: %s", e.getMessage());
            stopPlayer();
        }
    }

    /** Fade the video in only once it really plays, so a slow start never shows a black box. */
    private void waitFirstFrame() {
        if (mPlayer == null) {
            return;
        }
        if (mPlayer.isPlaying() && mPlayer.getPositionMs() > 0) {
            mFrame.animate().cancel();
            mFrame.animate().alpha(1f).setDuration(FADE_IN_MS).start();
        } else if (System.currentTimeMillis() - mWaitStartedMs < FIRST_FRAME_TIMEOUT_MS) {
            mHandler.postDelayed(mWaitFirstFrame, FIRST_FRAME_POLL_MS);
        } else {
            Log.w(TAG, "Hero preview didn't start in time, keeping the artwork");
            stopPlayer();
        }
    }

    private void stopPlayer() {
        mStartPending = false;
        mHandler.removeCallbacks(mStart);
        mHandler.removeCallbacks(mWaitFirstFrame);
        EmbedPlayerView player = mPlayer;
        mPlayer = null;
        mFrame.animate().cancel();
        if (player == null) {
            mFrame.setAlpha(0f);
            return;
        }
        player.setMute(true);
        mFrame.animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction(() -> {
            try {
                player.finish();
            } catch (RuntimeException e) {
                Log.e(TAG, "Hero preview release failed: %s", e.getMessage());
            }
            mFrame.removeView(player);
        }).start();
    }

    /** The backdrop's fades (left, bottom) and light dim, drawn over the video. */
    private static final class FadeOverlay extends View {
        private final Paint mLeft = new Paint();
        private final Paint mBottom = new Paint();
        private final Paint mBase = new Paint();
        private final int mBaseColor;

        FadeOverlay(Context context) {
            super(context);
            mBaseColor = ContextCompat.getColor(context, R.color.tvbox_base);
            mBase.setColor(mBaseColor);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            int clear = mBaseColor & 0x00FFFFFF;
            float fadeEnd = w * 0.62f;
            mLeft.setShader(new LinearGradient(0, 0, fadeEnd, 0,
                    new int[] {mBaseColor, clear | 0xF0000000, clear | 0x99000000, clear},
                    new float[] {0f, 0.25f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            mBottom.setShader(new LinearGradient(0, h * 0.35f, 0, h * 0.92f,
                    new int[] {clear, clear | 0xB3000000, mBaseColor}, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            canvas.drawRect(0, 0, w * 0.62f + 1, h, mLeft);
            canvas.drawRect(0, h * 0.35f, w, h, mBottom);
            canvas.drawRect(0, h * 0.92f, w, h, mBase);
            canvas.drawColor(Color.argb(DIM_ALPHA, 0, 0, 0));
        }
    }
}
