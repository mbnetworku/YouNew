package com.liskovsoft.smartyoutubetv2.tv.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;

import com.liskovsoft.smartyoutubetv2.common.misc.LoadingStatus;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * YouNew: the loading picture (new design). A clean ring (a faint circle with a red arc turning on it) with a small
 * white play mark inside, and a line of text under it that says what is going on:
 * "Loading...", after a few seconds "Still loading... the connection may be slow", after a long wait "This is taking
 * long. Press Back to leave, or wait." The player can put its own line (for example "Trying again... (2 of 8)",
 * see {@link LoadingStatus}). Drawn by hand (no picture files), so it is light for the TV box and always sharp.
 */
public class YouNewLoadingView extends View {
    private static final int RED = Color.parseColor("#E62117");
    private static final float TURN_MS = 1000f; // one full turn of the ring
    private static final long SLOW_AFTER_MS = 7_000;
    private static final long VERY_SLOW_AFTER_MS = 20_000;
    private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mMark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRing = new RectF();
    private final Path mPath = new Path();
    private final float mDensity;
    private long mShownSinceMs;

    public YouNewLoadingView(Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;

        mTrack.setStyle(Paint.Style.STROKE);
        mTrack.setStrokeWidth(4 * mDensity);
        mTrack.setColor(Color.parseColor("#33FFFFFF"));

        mArc.setStyle(Paint.Style.STROKE);
        mArc.setStrokeWidth(4 * mDensity);
        mArc.setStrokeCap(Paint.Cap.ROUND);
        mArc.setColor(RED);

        mMark.setColor(Color.WHITE);

        mText.setColor(Color.WHITE);
        mText.setTextSize(16 * mDensity);
        mText.setTextAlign(Paint.Align.CENTER);
        mText.setTypeface(Typeface.DEFAULT_BOLD);
        mText.setShadowLayer(3 * mDensity, 0, mDensity, Color.parseColor("#99000000"));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mShownSinceMs = SystemClock.uptimeMillis();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            mShownSinceMs = SystemClock.uptimeMillis(); // the wait starts again each time the picture comes back
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = (int) (300 * mDensity);
        int height = (int) ((72 + 14 + 28) * mDensity);
        setMeasuredDimension(width, height);
    }

    private String line() {
        String own = LoadingStatus.get();
        if (!TextUtils.isEmpty(own)) {
            return own;
        }

        long waited = SystemClock.uptimeMillis() - mShownSinceMs;
        if (waited > VERY_SLOW_AFTER_MS) {
            return getContext().getString(R.string.younew_loading_very_slow);
        } else if (waited > SLOW_AFTER_MS) {
            return getContext().getString(R.string.younew_loading_slow);
        }
        return getContext().getString(R.string.younew_loading);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float ringSize = 72 * mDensity;
        float cy = ringSize / 2f + mDensity * 2;
        float stroke = mArc.getStrokeWidth();
        float ringR = ringSize / 2f - stroke;

        // Ring: faint full circle plus a red arc that turns and stretches a little
        mRing.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
        canvas.drawOval(mRing, mTrack);
        float t = (SystemClock.uptimeMillis() % (long) TURN_MS) / TURN_MS;
        float start = t * 360f - 90f;
        float sweep = 70f + 60f * (float) Math.sin(t * Math.PI * 2);
        canvas.drawArc(mRing, start, sweep, false, mArc);

        // Small white play mark in the middle
        float h = ringR * 0.8f;
        float w = h * 0.9f;
        mPath.reset();
        mPath.moveTo(cx - w * 0.38f, cy - h / 2f);
        mPath.lineTo(cx + w * 0.62f, cy);
        mPath.lineTo(cx - w * 0.38f, cy + h / 2f);
        mPath.close();
        canvas.drawPath(mPath, mMark);

        // The line under the ring
        String text = TextUtils.ellipsize(line(), new android.text.TextPaint(mText), getWidth() - 8 * mDensity, TextUtils.TruncateAt.END).toString();
        canvas.drawText(text, cx, ringSize + 14 * mDensity + 16 * mDensity, mText);

        postInvalidateOnAnimation(); // keep turning while visible
    }
}
