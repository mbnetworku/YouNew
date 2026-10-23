package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * YouNew: the YouTube TV style Shorts screen, drawn over the player while a Short is playing.
 * <br>
 * A dark blue background fills everything around the vertical video, a rounded white frame
 * outlines the video, a thin progress line runs along its bottom and a play sign shows when paused.
 * The title, channel and the Like / Dislike / Subscribe / Channel buttons are shown on the right.
 * <br>
 * The overlay only draws and reports clicks. The {@link Host} (the player screen) does the work:
 * switching Shorts, play/pause and the button actions.
 */
public class ShortsOverlayView extends FrameLayout {
    private static final int TICK_MS = 250;
    private static final int COLOR_ON = Color.parseColor("#3EA6FF"); // liked / disliked / subscribed
    private static final int[] BUTTON_IDS = {R.id.action_thumbs_up, R.id.action_thumbs_down, R.id.action_subscribe, R.id.action_channel};

    /** What the overlay needs from the player screen. */
    public interface Host {
        void onShortsButtonClicked(int buttonId, int buttonState);
        void onShortsTogglePlay();
        long getShortsPositionMs();
        long getShortsDurationMs();
        boolean isShortsPlaying();
    }

    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPausePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPlayIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mHole = new RectF();
    private final LinearLayout mInfo;
    private final TextView mTitle;
    private final TextView mChannel;
    private final List<ButtonRow> mButtons = new ArrayList<>();
    private View mVideoView;
    private Host mHost;
    private int mSelected = -1;
    private boolean mSelectedByMouse;
    private float mProgress;
    private boolean mPaused;

    private final Runnable mTicker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (getVisibility() == View.VISIBLE) {
                postDelayed(this, TICK_MS);
            }
        }
    };

    /** One button on the right: round icon with its label next to it. */
    private class ButtonRow {
        final int id;
        final LinearLayout view;
        final ImageView icon;
        final TextView label;
        final GradientDrawable circle = new GradientDrawable();
        final int labelResId;
        final int iconResId;
        int state = PlayerUI.BUTTON_OFF;
        boolean isPicture; // channel avatar: shown as is, never tinted
        String pictureUrl;

        ButtonRow(Context context, int id, int iconResId, int labelResId) {
            this.id = id;
            this.labelResId = labelResId;
            this.iconResId = iconResId;

            view = new LinearLayout(context);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); // icon always next to the video
            view.setPadding(0, dp(5), 0, dp(5));
            view.setClickable(true);
            view.setOnClickListener(v -> click(mButtons.indexOf(this)));

            circle.setShape(GradientDrawable.OVAL);
            icon = new ImageView(context);
            icon.setBackground(circle);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setPadding(dp(12), dp(12), dp(12), dp(12));
            icon.setImageDrawable(ContextCompat.getDrawable(context, iconResId));
            view.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

            label = new TextView(context);
            label.setTextSize(16);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setText(labelResId);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.leftMargin = dp(16);
            view.addView(label, labelParams);
        }

        void setPicture(Drawable picture) {
            isPicture = true;
            icon.setPadding(0, 0, 0, 0);
            icon.setImageDrawable(picture);
            refresh(mButtons.indexOf(this) == mSelected);
        }

        void resetPicture() {
            isPicture = false;
            pictureUrl = null;
            icon.setPadding(dp(12), dp(12), dp(12), dp(12));
            icon.setImageDrawable(ContextCompat.getDrawable(getContext(), iconResId));
            refresh(mButtons.indexOf(this) == mSelected);
        }

        void refresh(boolean selected) {
            boolean on = state == PlayerUI.BUTTON_ON;
            circle.setColor(selected ? Color.WHITE : Color.parseColor("#33FFFFFF"));

            if (isPicture) {
                icon.clearColorFilter();
            } else {
                icon.setColorFilter(on ? COLOR_ON : (selected ? Color.parseColor("#0F0F0F") : Color.WHITE), PorterDuff.Mode.SRC_IN);
            }

            if (id == R.id.action_subscribe) {
                label.setText(on ? R.string.younew_shorts_subscribed : labelResId);
            }

            label.setTextColor(selected ? Color.WHITE : Color.parseColor("#CCCCCC"));
            label.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    public ShortsOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        setFocusable(false);

        mFramePaint.setStyle(Paint.Style.STROKE);
        mFramePaint.setColor(Color.WHITE);
        mFramePaint.setStrokeWidth(dp(4));
        mTrackPaint.setColor(Color.parseColor("#66FFFFFF"));
        mProgressPaint.setColor(Color.parseColor("#FF0000"));
        mPausePaint.setColor(Color.parseColor("#99000000"));
        mPlayIconPaint.setColor(Color.WHITE);

        mInfo = new LinearLayout(context);
        mInfo.setOrientation(LinearLayout.VERTICAL);

        mTitle = new TextView(context);
        mTitle.setTextColor(Color.WHITE);
        mTitle.setTextSize(22);
        mTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mTitle.setMaxLines(3);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        mInfo.addView(mTitle);

        mChannel = new TextView(context);
        mChannel.setTextColor(Color.parseColor("#DDDDDD"));
        mChannel.setTextSize(16);
        mChannel.setSingleLine(true);
        mChannel.setEllipsize(TextUtils.TruncateAt.END);
        mChannel.setPadding(0, dp(8), 0, dp(20));
        mInfo.addView(mChannel);

        addButton(context, R.id.action_thumbs_up, R.drawable.lb_ic_thumb_up, R.string.action_like);
        addButton(context, R.id.action_thumbs_down, R.drawable.lb_ic_thumb_down, R.string.action_dislike);
        addButton(context, R.id.action_subscribe, R.drawable.action_subscribe, R.string.action_subscribe);
        addButton(context, R.id.action_channel, R.drawable.action_channel, R.string.younew_shorts_channel);

        TextView hint = new TextView(context);
        hint.setTextColor(Color.parseColor("#AAAAAA"));
        hint.setTextSize(14);
        hint.setPadding(0, dp(20), 0, 0);
        hint.setText(R.string.younew_shorts_hint);
        mInfo.addView(hint);

        LayoutParams infoParams = new LayoutParams(dp(300), LayoutParams.WRAP_CONTENT);
        infoParams.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        addView(mInfo, infoParams);

        // A click anywhere else (the video or the background) pauses or resumes
        setOnClickListener(v -> {
            if (mHost != null) {
                mHost.onShortsTogglePlay();
            }
        });

        setVisibility(View.GONE);
    }

    private void addButton(Context context, int id, int iconResId, int labelResId) {
        ButtonRow row = new ButtonRow(context, id, iconResId, labelResId);
        mButtons.add(row);
        mInfo.addView(row.view, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.refresh(false);
    }

    public void setHost(Host host) {
        mHost = host;
    }

    /** The view that shows the video (its bounds follow the video's shape). */
    public void setVideoView(View videoView) {
        mVideoView = videoView;
        videoView.getViewTreeObserver().addOnGlobalLayoutListener(this::updateHole);
    }

    public void show(String title, String channel) {
        mTitle.setText(title);
        mChannel.setText(channel);
        boolean wasHidden = getVisibility() != View.VISIBLE;
        setVisibility(View.VISIBLE);
        mProgress = 0;
        mPaused = false;
        updateHole();

        if (wasHidden) {
            removeCallbacks(mTicker);
            post(mTicker);
        }
    }

    public void hide() {
        setVisibility(View.GONE);
        removeCallbacks(mTicker);
        clearSelection();
    }

    public boolean isActive() {
        return getVisibility() == View.VISIBLE;
    }

    // Buttons: selection with the remote (Right / Up / Down / OK / Left / Back)

    public boolean hasSelection() {
        return mSelected >= 0;
    }

    public void select(int index) {
        mSelected = Math.max(0, Math.min(index, mButtons.size() - 1));
        mSelectedByMouse = false;
        refreshButtons();
    }

    public void moveSelection(int delta) {
        if (hasSelection()) {
            select(mSelected + delta);
        }
    }

    public void clearSelection() {
        mSelected = -1;
        mSelectedByMouse = false;
        refreshButtons();
    }

    public void clickSelected() {
        click(mSelected);
    }

    private void click(int index) {
        if (index < 0 || index >= mButtons.size() || mHost == null) {
            return;
        }

        ButtonRow row = mButtons.get(index);
        mHost.onShortsButtonClicked(row.id, row.state);
    }

    /** Mouse pointer moved: highlight the button under it. */
    public void onPointerMoved(float x, float y) {
        int index = -1;
        for (int i = 0; i < mButtons.size(); i++) {
            View view = mButtons.get(i).view;
            int[] loc = new int[2];
            int[] me = new int[2];
            view.getLocationInWindow(loc);
            getLocationInWindow(me);
            float left = loc[0] - me[0];
            float top = loc[1] - me[1];
            if (x >= left && x <= left + view.getWidth() && y >= top && y <= top + view.getHeight()) {
                index = i;
                break;
            }
        }

        if (index >= 0 && index != mSelected) {
            select(index);
            mSelectedByMouse = true;
        } else if (index < 0 && mSelectedByMouse) {
            clearSelection();
        }
    }

    private void refreshButtons() {
        for (int i = 0; i < mButtons.size(); i++) {
            mButtons.get(i).refresh(i == mSelected);
        }
    }

    // Button states and channel picture (kept in sync with the normal player buttons)

    public void setButtonState(int buttonId, int buttonState) {
        for (ButtonRow row : mButtons) {
            if (row.id == buttonId) {
                row.state = buttonState;
                row.refresh(mButtons.indexOf(row) == mSelected);
            }
        }
    }

    /** Shows the channel's picture on the Channel button (null = back to the plain icon). */
    public void setChannelIcon(String iconUrl) {
        for (ButtonRow row : mButtons) {
            if (row.id != R.id.action_channel) {
                continue;
            }

            if (iconUrl == null) {
                row.resetPicture();
                continue;
            }

            row.pictureUrl = iconUrl;
            Glide.with(getContext())
                    .load(iconUrl)
                    .apply(ViewUtil.glideOptions())
                    .circleCrop()
                    .into(new SimpleTarget<Drawable>(dp(50), dp(50)) {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                            // Ignore a late picture from the previous Short
                            if (iconUrl.equals(row.pictureUrl)) {
                                row.setPicture(resource);
                            }
                        }
                    });
        }
    }

    // Progress line and pause sign

    private void updateProgress() {
        if (mHost == null) {
            return;
        }

        long duration = mHost.getShortsDurationMs();
        float progress = duration > 0 ? Math.min(1f, Math.max(0f, (float) mHost.getShortsPositionMs() / duration)) : 0;
        boolean paused = duration > 0 && !mHost.isShortsPlaying(); // no play sign while the Short is still loading

        if (progress != mProgress || paused != mPaused) {
            mProgress = progress;
            mPaused = paused;
            invalidate();
        }
    }

    /** Called right after play/pause so the sign changes at once. */
    public void setPaused(boolean paused) {
        mPaused = paused;
        invalidate();
    }

    // Layout and drawing

    private void updateHole() {
        if (getVisibility() != View.VISIBLE || getWidth() == 0) {
            return;
        }

        float height = getHeight();
        float width = getWidth();

        if (mVideoView != null && mVideoView.getWidth() > 0 && mVideoView.getWidth() < width) {
            int[] video = new int[2];
            int[] me = new int[2];
            mVideoView.getLocationInWindow(video);
            getLocationInWindow(me);
            float left = video[0] - me[0];
            float top = video[1] - me[1];
            mHole.set(left, top, left + mVideoView.getWidth(), top + mVideoView.getHeight());
        } else {
            // Not laid out yet: assume a 9:16 video in the middle
            float holeHeight = height - dp(48);
            float holeWidth = holeHeight * 9 / 16;
            mHole.set((width - holeWidth) / 2, (height - holeHeight) / 2, (width + holeWidth) / 2, (height + holeHeight) / 2);
        }

        mBackgroundPaint.setShader(new LinearGradient(0, 0, width, height,
                Color.parseColor("#0A1424"), Color.parseColor("#163452"), Shader.TileMode.CLAMP));

        // Title, channel and buttons to the right of the video
        LayoutParams lp = (LayoutParams) mInfo.getLayoutParams();
        int leftMargin = (int) (mHole.right + dp(40));
        int infoWidth = Math.max(dp(160), (int) Math.min(dp(320), width - leftMargin - dp(24)));
        if (lp.leftMargin != leftMargin || lp.width != infoWidth) {
            lp.leftMargin = leftMargin;
            lp.width = infoWidth;
            mInfo.setLayoutParams(lp);
        }

        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateHole();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(mTicker);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float radius = dp(16);

        // Background around the video
        mPath.reset();
        mPath.setFillType(Path.FillType.EVEN_ODD);
        mPath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        mPath.addRoundRect(mHole, radius, radius, Path.Direction.CW);
        canvas.drawPath(mPath, mBackgroundPaint);

        // Progress line along the bottom of the video
        float lineLeft = mHole.left + radius;
        float lineRight = mHole.right - radius;
        float lineTop = mHole.bottom - dp(10);
        float lineBottom = lineTop + dp(4);
        if (lineRight > lineLeft) {
            canvas.drawRect(lineLeft, lineTop, lineRight, lineBottom, mTrackPaint);
            canvas.drawRect(lineLeft, lineTop, lineLeft + (lineRight - lineLeft) * mProgress, lineBottom, mProgressPaint);
        }

        // Play sign while paused
        if (mPaused) {
            float cx = mHole.centerX();
            float cy = mHole.centerY();
            float r = dp(48);
            canvas.drawCircle(cx, cy, r, mPausePaint);
            mPath.reset();
            mPath.moveTo(cx - r * 0.3f, cy - r * 0.45f);
            mPath.lineTo(cx + r * 0.5f, cy);
            mPath.lineTo(cx - r * 0.3f, cy + r * 0.45f);
            mPath.close();
            canvas.drawPath(mPath, mPlayIconPaint);
        }

        // White frame
        canvas.drawRoundRect(mHole, radius, radius, mFramePaint);
        super.onDraw(canvas);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
