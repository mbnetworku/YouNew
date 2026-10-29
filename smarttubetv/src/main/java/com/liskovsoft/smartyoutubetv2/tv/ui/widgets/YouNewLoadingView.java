package com.liskovsoft.smartyoutubetv2.tv.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * YouNew: the loading picture shown while videos are loading.
 * The red YouNew badge (circle with a white play triangle) with a red ring turning around it.
 * It replaces the plain grey spinner. Drawn by hand (no picture files, no animation objects),
 * so it is light for the TV box and always looks sharp.
 */
public class YouNewLoadingView extends View {
    private static final int RED = Color.parseColor("#E62117");
    private static final float TURN_MS = 1100f; // one full turn of the ring
    private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBadge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTriangle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRing = new RectF();
    private final Path mPath = new Path();

    public YouNewLoadingView(Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;

        mTrack.setStyle(Paint.Style.STROKE);
        mTrack.setStrokeWidth(5 * density);
        mTrack.setColor(Color.parseColor("#33FFFFFF"));

        mArc.setStyle(Paint.Style.STROKE);
        mArc.setStrokeWidth(5 * density);
        mArc.setStrokeCap(Paint.Cap.ROUND);
        mArc.setColor(RED);

        mBadge.setColor(RED);
        mTriangle.setColor(Color.WHITE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = (int) (96 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float stroke = mArc.getStrokeWidth();
        float ringR = Math.min(cx, cy) - stroke;
        float badgeR = ringR * 0.62f;

        // Ring: faint full circle plus a red arc that turns and stretches a little
        mRing.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
        canvas.drawOval(mRing, mTrack);
        float t = (SystemClock.uptimeMillis() % (long) TURN_MS) / TURN_MS;
        float start = t * 360f - 90f;
        float sweep = 80f + 50f * (float) Math.sin(t * Math.PI * 2);
        canvas.drawArc(mRing, start, sweep, false, mArc);

        // Badge: red circle with a white play triangle
        canvas.drawCircle(cx, cy, badgeR, mBadge);
        float h = badgeR * 0.9f;
        float w = h * 0.87f;
        mPath.reset();
        mPath.moveTo(cx - w * 0.38f, cy - h / 2f);
        mPath.lineTo(cx + w * 0.62f, cy);
        mPath.lineTo(cx - w * 0.38f, cy + h / 2f);
        mPath.close();
        canvas.drawPath(mPath, mTriangle);

        postInvalidateOnAnimation(); // keep turning while visible
    }
}
