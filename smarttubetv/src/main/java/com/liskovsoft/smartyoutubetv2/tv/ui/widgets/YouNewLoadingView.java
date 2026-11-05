package com.liskovsoft.smartyoutubetv2.tv.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * YouNew: the loading picture shown while videos are loading.
 * The YouTv Plus mark (red-orange TV screen with a white play triangle) with a red ring turning around it.
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
    private final RectF mScreen = new RectF();
    private float mGradientSize = -1;
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

        // Ring: faint full circle plus a red arc that turns and stretches a little
        mRing.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
        canvas.drawOval(mRing, mTrack);
        float t = (SystemClock.uptimeMillis() % (long) TURN_MS) / TURN_MS;
        float start = t * 360f - 90f;
        float sweep = 80f + 50f * (float) Math.sin(t * Math.PI * 2);
        canvas.drawArc(mRing, start, sweep, false, mArc);

        // Mark: rounded TV screen (red to orange) with a white play triangle, like the app icon
        float sw = ringR * 1.15f;
        float sh = sw * 0.78f;
        mScreen.set(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f);
        if (mGradientSize != sw) { // made once per size, not on every frame
            mGradientSize = sw;
            mBadge.setShader(new LinearGradient(mScreen.left, mScreen.top, mScreen.right, mScreen.bottom,
                    RED, Color.parseColor("#FF8A00"), Shader.TileMode.CLAMP));
        }
        canvas.drawRoundRect(mScreen, sh * 0.25f, sh * 0.25f, mBadge);
        float h = sh * 0.5f;
        float w = h * 0.9f;
        mPath.reset();
        mPath.moveTo(cx - w * 0.40f, cy - h / 2f);
        mPath.lineTo(cx + w * 0.60f, cy);
        mPath.lineTo(cx - w * 0.40f, cy + h / 2f);
        mPath.close();
        canvas.drawPath(mPath, mTriangle);

        postInvalidateOnAnimation(); // keep turning while visible
    }
}
