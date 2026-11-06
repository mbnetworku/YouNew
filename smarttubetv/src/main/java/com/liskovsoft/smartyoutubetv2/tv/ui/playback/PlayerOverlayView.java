package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * YouNew: the YouTube-like layer over the video.
 * <ul>
 *     <li>top: a back arrow and the video title on a soft dark fade</li>
 *     <li>middle: big round Previous / Play-Pause / Next buttons</li>
 *     <li>a small pill for gesture feedback (volume, brightness, 2x speed)</li>
 * </ul>
 * It is shown and hidden together with the normal player controls (the progress bar and the other buttons at the
 * bottom stay as they are). It only draws and reports clicks; {@link Host} does the work.
 * Everything works with a finger, a mouse (hover lights a button) and a remote (the normal controls).
 */
public class PlayerOverlayView extends FrameLayout {
    public interface Host {
        void onOverlayBack();
        void onOverlayPrevious();
        void onOverlayNext();
        void onOverlayPlayPause();
        boolean isOverlayPlaying();
    }

    private static final int FADE_MS = 160;
    private static final int TICK_MS = 400;

    private Host mHost;
    private final FrameLayout mControls;
    private final RoundButton mBack;
    private final RoundButton mPrev;
    private final RoundButton mPlay;
    private final RoundButton mNext;
    private final TextView mTitle;
    private final TextView mPill;
    private boolean mShown;

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            updatePlayIcon();
            if (mShown) {
                postDelayed(this, TICK_MS);
            }
        }
    };

    private final Runnable mHidePill = new Runnable() {
        @Override
        public void run() {
            mPill.setVisibility(GONE);
        }
    };

    public PlayerOverlayView(Context context) {
        super(context);

        // Controls layer (top fade, top bar, middle buttons); hidden until the player controls show
        mControls = new FrameLayout(context);
        mControls.setVisibility(GONE);
        addView(mControls, new LayoutParams(-1, -1));

        View topFade = new View(context);
        GradientDrawable fade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xB3000000, 0x00000000});
        topFade.setBackground(fade);
        mControls.addView(topFade, new LayoutParams(-1, dp(120), Gravity.TOP));

        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        mControls.addView(topBar, new LayoutParams(-1, -2, Gravity.TOP));

        mBack = new RoundButton(context, RoundButton.BACK);
        mBack.setOnClickListener(v -> {
            if (mHost != null) mHost.onOverlayBack();
        });
        topBar.addView(mBack, new LinearLayout.LayoutParams(dp(46), dp(46)));

        mTitle = new TextView(context);
        mTitle.setTextColor(Color.WHITE);
        mTitle.setTextSize(18);
        mTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mTitle.setSingleLine(true);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        mTitle.setShadowLayer(dp(3), 0, dp(1), 0xCC000000);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.leftMargin = dp(14);
        topBar.addView(mTitle, titleLp);

        LinearLayout middle = new LinearLayout(context);
        middle.setOrientation(LinearLayout.HORIZONTAL);
        middle.setGravity(Gravity.CENTER);
        mControls.addView(middle, new LayoutParams(-2, -2, Gravity.CENTER));

        mPrev = new RoundButton(context, RoundButton.PREV);
        mPrev.setOnClickListener(v -> {
            if (mHost != null) mHost.onOverlayPrevious();
        });
        middle.addView(mPrev, new LinearLayout.LayoutParams(dp(58), dp(58)));

        mPlay = new RoundButton(context, RoundButton.PLAY);
        mPlay.setOnClickListener(v -> {
            if (mHost != null) {
                mHost.onOverlayPlayPause();
                postDelayed(this::updatePlayIcon, 150);
            }
        });
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(dp(78), dp(78));
        playLp.leftMargin = dp(34);
        playLp.rightMargin = dp(34);
        middle.addView(mPlay, playLp);

        mNext = new RoundButton(context, RoundButton.NEXT);
        mNext.setOnClickListener(v -> {
            if (mHost != null) mHost.onOverlayNext();
        });
        middle.addView(mNext, new LinearLayout.LayoutParams(dp(58), dp(58)));

        // Feedback pill for swipes and the 2x hold
        mPill = new TextView(context);
        mPill.setTextColor(Color.WHITE);
        mPill.setTextSize(16);
        mPill.setTypeface(Typeface.DEFAULT_BOLD);
        mPill.setGravity(Gravity.CENTER);
        mPill.setPadding(dp(18), dp(9), dp(18), dp(9));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(0xCC000000);
        pillBg.setCornerRadius(dp(24));
        mPill.setBackground(pillBg);
        mPill.setVisibility(GONE);
        LayoutParams pillLp = new LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pillLp.topMargin = dp(40);
        addView(mPill, pillLp);
    }

    public void setHost(Host host) {
        mHost = host;
    }

    public void setTitle(String title) {
        mTitle.setText(title != null ? title : "");
    }

    /** Shows or hides the layer together with the player controls. */
    public void setControlsShown(boolean shown) {
        if (shown == mShown && (shown == (mControls.getVisibility() == VISIBLE))) {
            return;
        }

        mShown = shown;
        mControls.animate().cancel();
        removeCallbacks(mTick);

        if (shown) {
            updatePlayIcon();
            mControls.setVisibility(VISIBLE);
            mControls.setAlpha(0f);
            mControls.animate().alpha(1f).setStartDelay(0).setDuration(FADE_MS).withEndAction(() -> mControls.setAlpha(1f)).start();
            postDelayed(mTick, TICK_MS);
        } else {
            mControls.animate().alpha(0f).setStartDelay(0).setDuration(FADE_MS).withEndAction(() -> {
                if (!mShown) {
                    mControls.setVisibility(GONE);
                    mControls.setAlpha(1f);
                }
            }).start();
        }
    }

    /** Shows the feedback pill; with autoHideMs > 0 it goes away by itself. */
    public void showPill(String text, long autoHideMs) {
        removeCallbacks(mHidePill);
        mPill.setText(text);
        mPill.setVisibility(VISIBLE);
        if (autoHideMs > 0) {
            postDelayed(mHidePill, autoHideMs);
        }
    }

    public void hidePill(long delayMs) {
        removeCallbacks(mHidePill);
        postDelayed(mHidePill, delayMs);
    }

    /** True if the screen point (raw coordinates) is on one of the round buttons. */
    public boolean hitsControl(float rawX, float rawY) {
        if (mControls.getVisibility() != VISIBLE) {
            return false;
        }
        return hits(mBack, rawX, rawY) || hits(mPrev, rawX, rawY) || hits(mPlay, rawX, rawY) || hits(mNext, rawX, rawY);
    }

    private boolean hits(View v, float rawX, float rawY) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        int slop = dp(10);
        return rawX >= loc[0] - slop && rawX <= loc[0] + v.getWidth() + slop
                && rawY >= loc[1] - slop && rawY <= loc[1] + v.getHeight() + slop;
    }

    private void updatePlayIcon() {
        if (mHost != null) {
            mPlay.setKind(mHost.isOverlayPlaying() ? RoundButton.PAUSE : RoundButton.PLAY);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mTick);
        removeCallbacks(mHidePill);
        mControls.animate().cancel();
        super.onDetachedFromWindow();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** A round glass button with a drawn icon. Lights up when focused, hovered by the mouse or pressed. */
    static class RoundButton extends View {
        static final int BACK = 0;
        static final int PREV = 1;
        static final int PLAY = 2;
        static final int PAUSE = 3;
        static final int NEXT = 4;

        private int mKind;
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mGlyph = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path mPath = new Path();

        RoundButton(Context context, int kind) {
            super(context);
            mKind = kind;
            setClickable(true);
            setFocusable(false); // the remote keeps using the normal player controls
            mRing.setStyle(Paint.Style.STROKE);
            mRing.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
            mRing.setColor(Color.WHITE);
            mGlyph.setStyle(Paint.Style.FILL);
        }

        void setKind(int kind) {
            if (kind != mKind) {
                mKind = kind;
                invalidate();
            }
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            boolean active = isPressed() || isHovered() || isFocused();
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(getWidth(), getHeight()) / 2f;

            mFill.setColor(active ? 0xF2FFFFFF : 0x80000000);
            canvas.drawCircle(cx, cy, r - mRing.getStrokeWidth(), mFill);
            if (!active) {
                mRing.setAlpha(70);
                canvas.drawCircle(cx, cy, r - mRing.getStrokeWidth(), mRing);
            }

            int color = active ? 0xFF101018 : Color.WHITE;
            mGlyph.setColor(color);
            float g = r * 0.42f;
            mPath.reset();

            switch (mKind) {
                case PLAY:
                    mPath.moveTo(cx - g * 0.6f, cy - g);
                    mPath.lineTo(cx - g * 0.6f, cy + g);
                    mPath.lineTo(cx + g * 0.95f, cy);
                    mPath.close();
                    canvas.drawPath(mPath, mGlyph);
                    break;
                case PAUSE:
                    canvas.drawRect(cx - g * 0.75f, cy - g, cx - g * 0.2f, cy + g, mGlyph);
                    canvas.drawRect(cx + g * 0.2f, cy - g, cx + g * 0.75f, cy + g, mGlyph);
                    break;
                case NEXT:
                    mPath.moveTo(cx - g * 0.9f, cy - g * 0.85f);
                    mPath.lineTo(cx - g * 0.9f, cy + g * 0.85f);
                    mPath.lineTo(cx + g * 0.55f, cy);
                    mPath.close();
                    canvas.drawPath(mPath, mGlyph);
                    canvas.drawRect(cx + g * 0.7f, cy - g * 0.85f, cx + g * 0.98f, cy + g * 0.85f, mGlyph);
                    break;
                case PREV:
                    mPath.moveTo(cx + g * 0.9f, cy - g * 0.85f);
                    mPath.lineTo(cx + g * 0.9f, cy + g * 0.85f);
                    mPath.lineTo(cx - g * 0.55f, cy);
                    mPath.close();
                    canvas.drawPath(mPath, mGlyph);
                    canvas.drawRect(cx - g * 0.98f, cy - g * 0.85f, cx - g * 0.7f, cy + g * 0.85f, mGlyph);
                    break;
                case BACK:
                default:
                    mGlyph.setStyle(Paint.Style.STROKE);
                    mGlyph.setStrokeWidth(g * 0.3f);
                    mGlyph.setStrokeCap(Paint.Cap.ROUND);
                    mGlyph.setStrokeJoin(Paint.Join.ROUND);
                    mPath.moveTo(cx + g * 0.9f, cy);
                    mPath.lineTo(cx - g * 0.8f, cy);
                    mPath.moveTo(cx - g * 0.05f, cy - g * 0.85f);
                    mPath.lineTo(cx - g * 0.9f, cy);
                    mPath.lineTo(cx - g * 0.05f, cy + g * 0.85f);
                    canvas.drawPath(mPath, mGlyph);
                    mGlyph.setStyle(Paint.Style.FILL);
                    break;
            }
        }
    }
}
