package com.liskovsoft.smartyoutubetv2.tv.ui.widgets;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

/**
 * YouNew: a panel inside the Home screen (Settings, Search) that takes only the part of the screen it can be seen in.
 * <br>
 * The Home screen moves its content to the right when the sidebar opens, but gives it the same width as before, so
 * the right part of a panel would be cut off by the edge of the screen. And it slides the content without laying
 * it out again, so the panel is not told when it has moved.
 * <br>
 * This view measures itself against what is left of the screen from its own left and top edge, and looks at its
 * place before every frame: when it has moved and come to rest, it measures itself again.
 */
public class VisibleAreaFrameLayout extends FrameLayout {
    private static final long SETTLE_MS = 80;
    private final int[] mPlace = new int[2];
    private int mLastLeft = -1;
    private int mLastTop = -1;
    private final Runnable mMeasureAgain = this::requestLayout;
    private final ViewTreeObserver.OnPreDrawListener mWatchPlace = () -> {
        getLocationOnScreen(mPlace);
        if (mPlace[0] != mLastLeft || mPlace[1] != mLastTop) {
            mLastLeft = mPlace[0];
            mLastTop = mPlace[1];
            removeCallbacks(mMeasureAgain);
            postDelayed(mMeasureAgain, SETTLE_MS);
        }
        return true;
    };

    public VisibleAreaFrameLayout(Context context) {
        super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        getLocationOnScreen(mPlace);

        widthMeasureSpec = fitSpec(widthMeasureSpec, metrics.widthPixels - Math.max(0, mPlace[0]));
        heightMeasureSpec = fitSpec(heightMeasureSpec, metrics.heightPixels - Math.max(0, mPlace[1]));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private static int fitSpec(int spec, int max) {
        int mode = MeasureSpec.getMode(spec);
        if (mode != MeasureSpec.UNSPECIFIED && max > 0 && MeasureSpec.getSize(spec) > max) {
            return MeasureSpec.makeMeasureSpec(max, mode);
        }
        return spec;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(mWatchPlace);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(mWatchPlace);
        removeCallbacks(mMeasureAgain);
        super.onDetachedFromWindow();
    }
}
