package com.liskovsoft.smartyoutubetv2.tv.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * YouNew: the small loading ring for cards and lists (the loading card at the end of a list, the picture of a
 * video card while its preview loads). The same look as the big loading picture, {@link YouNewLoadingView}:
 * a faint circle with a red arc turning on it, but without the play mark and the text. It replaces the system
 * spinner, which looked different on every Android version. Its size is the size the layout gives it.
 */
public class YouNewSpinner extends View {
    private static final int RED = Color.parseColor("#E62117");
    private static final float TURN_MS = 1000f;
    private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRing = new RectF();
    private final float mDensity;

    public YouNewSpinner(Context context) {
        this(context, null);
    }

    public YouNewSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDensity = context.getResources().getDisplayMetrics().density;

        mTrack.setStyle(Paint.Style.STROKE);
        mTrack.setColor(Color.parseColor("#33FFFFFF"));

        mArc.setStyle(Paint.Style.STROKE);
        mArc.setStrokeCap(Paint.Cap.ROUND);
        mArc.setColor(RED);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = (int) (36 * mDensity);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float stroke = Math.max(2 * mDensity, size / 11f);
        mTrack.setStrokeWidth(stroke);
        mArc.setStrokeWidth(stroke);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = size / 2f - stroke;
        mRing.set(cx - r, cy - r, cx + r, cy + r);

        canvas.drawOval(mRing, mTrack);
        float t = (SystemClock.uptimeMillis() % (long) TURN_MS) / TURN_MS;
        canvas.drawArc(mRing, t * 360f - 90f, 70f + 60f * (float) Math.sin(t * Math.PI * 2), false, mArc);

        postInvalidateOnAnimation(); // turns while visible
    }
}
