package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;

/**
 * YouNew: the touch and mouse gestures of the video screen, like in the YouTube app.
 * <ul>
 *     <li>tap on the video while the controls are shown: hide them (a tap while hidden shows them: that is done elsewhere)</li>
 *     <li>swipe up or down on the left half: brightness, on the right half: volume</li>
 *     <li>press and hold: 2x speed while held</li>
 *     <li>swipe down while the controls are shown: hide them</li>
 * </ul>
 * Double tap to skip 10 s is done by the existing double-tap adapter.
 * Gestures never start on the buttons or the bottom control area.
 */
public class PlayerGestures {
    public interface Host {
        Activity getGestureActivity();
        boolean isGestureOverlayShown();
        void hideGestureOverlay();
        /** Swipe up while the controls are shown: open the Recommended / playlist row below. */
        void showGestureSuggestions();
        boolean isGesturePlaying();
        float getGestureSpeed();
        void setGestureSpeed(float speed);
        /** True if the point (raw screen coordinates) is on a button of the overlay. */
        boolean isOnOverlayButton(float rawX, float rawY);
        void showGesturePill(String text, long autoHideMs);
        void hideGesturePill(long delayMs);
        String getBrightnessText(int percent);
        String getVolumeText(int percent);
        String getSpeedText(float speed);
    }

    /**
     * YouNew: hidden. The swipe up/down on the left half (brightness) and the right half (volume) of the video were
     * not wanted on the player screen (the owner: "no need for Android TV"). The code stays, so it can come back:
     * set this to true.
     */
    private static final boolean LEVEL_GESTURES_ENABLED = false;

    private static final long LONG_PRESS_MS = 550;
    private static final long TAP_MAX_MS = 280;
    private static final float HOLD_SPEED = 2.0f;

    private static final int MODE_NONE = 0;
    private static final int MODE_BRIGHTNESS = 1;
    private static final int MODE_VOLUME = 2;
    private static final int MODE_SPEED = 3;

    private final Host mHost;
    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final int mSlop;
    private final float mDensity;

    private int mMode = MODE_NONE;
    private float mStartX;
    private float mStartY;
    private long mStartTime;
    private boolean mOverlayWasShown;
    private boolean mStartedInZone; // on a button, or in the top / bottom control areas
    private boolean mMoved;
    private float mStartLevel;
    private float mSavedSpeed = 1f;
    private int mScreenWidth;
    private int mScreenHeight;

    private final Runnable mLongPress = new Runnable() {
        @Override
        public void run() {
            if (mMode != MODE_NONE || mMoved || mStartedInZone || mOverlayWasShown || !mHost.isGesturePlaying()) {
                return;
            }
            mMode = MODE_SPEED;
            mSavedSpeed = mHost.getGestureSpeed();
            mHost.hideGestureOverlay(); // the touch down showed the controls
            mHost.setGestureSpeed(HOLD_SPEED);
            mHost.showGesturePill(mHost.getSpeedText(HOLD_SPEED), 0);
        }
    };

    public PlayerGestures(Context context, Host host) {
        mContext = context;
        mHost = host;
        mSlop = ViewConfiguration.get(context).getScaledTouchSlop() * 2;
        mDensity = context.getResources().getDisplayMetrics().density;
    }

    /**
     * @return true when a gesture is running and the event must not go any further
     */
    public boolean onTouch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                onDown(e);
                return false;
            case MotionEvent.ACTION_MOVE:
                return onMove(e);
            case MotionEvent.ACTION_UP:
                return onUp(e, false);
            case MotionEvent.ACTION_CANCEL:
                return onUp(e, true);
            default:
                return mMode != MODE_NONE;
        }
    }

    private void onDown(MotionEvent e) {
        mStartX = e.getX();
        mStartY = e.getY();
        mStartTime = System.currentTimeMillis();
        mMoved = false;
        if (mMode == MODE_SPEED) { // the last press-and-hold never got its "finger up": give the speed back
            mHost.setGestureSpeed(mSavedSpeed);
            mHost.hideGesturePill(0);
        }
        mMode = MODE_NONE;
        mOverlayWasShown = mHost.isGestureOverlayShown();

        Activity activity = mHost.getGestureActivity();
        if (activity != null) {
            android.util.DisplayMetrics m = activity.getResources().getDisplayMetrics();
            mScreenWidth = m.widthPixels;
            mScreenHeight = m.heightPixels;
        }

        // Buttons, the top bar area and the bottom control area belong to the normal controls
        mStartedInZone = mHost.isOnOverlayButton(e.getRawX(), e.getRawY())
                || mStartY < 70 * mDensity
                || mStartY > mScreenHeight * 0.62f;

        mHandler.removeCallbacks(mLongPress);
        if (!mStartedInZone) {
            mHandler.postDelayed(mLongPress, LONG_PRESS_MS);
        }
    }

    private boolean onMove(MotionEvent e) {
        float dx = e.getX() - mStartX;
        float dy = e.getY() - mStartY;

        if (!mMoved && (Math.abs(dx) > mSlop || Math.abs(dy) > mSlop)) {
            mMoved = true;
            mHandler.removeCallbacks(mLongPress);

            // A clear vertical move that started on the plain video: volume or brightness
            if (LEVEL_GESTURES_ENABLED && mMode == MODE_NONE && !mStartedInZone && !mOverlayWasShown && Math.abs(dy) > Math.abs(dx) * 1.5f) {
                startLevelGesture();
            }
        }

        if (mMode == MODE_BRIGHTNESS || mMode == MODE_VOLUME) {
            // the whole height of the picture (about 80 %) changes the level from 0 to 100 %
            float range = Math.max(1f, mScreenHeight * 0.8f);
            float level = clamp(mStartLevel - dy / range);
            applyLevel(level);
            return true;
        }

        return mMode == MODE_SPEED;
    }

    private boolean onUp(MotionEvent e, boolean cancelled) {
        mHandler.removeCallbacks(mLongPress);
        int mode = mMode;
        mMode = MODE_NONE;

        if (mode == MODE_SPEED) {
            mHost.setGestureSpeed(mSavedSpeed);
            mHost.hideGesturePill(0);
            return true;
        }

        if (mode == MODE_BRIGHTNESS || mode == MODE_VOLUME) {
            mHost.hideGesturePill(700);
            return true;
        }

        if (cancelled) {
            return false;
        }

        float dx = e.getX() - mStartX;
        float dy = e.getY() - mStartY;
        long duration = System.currentTimeMillis() - mStartTime;

        // Swipe down while the controls are shown: hide them
        if (mOverlayWasShown && mHost.isGestureOverlayShown() && dy > 60 * mDensity && Math.abs(dy) > Math.abs(dx) * 1.5f) {
            mHost.hideGestureOverlay();
            return false;
        }

        // Swipe up while the controls are shown: the Recommended row below (and the playlist)
        if (mOverlayWasShown && mHost.isGestureOverlayShown() && dy < -60 * mDensity && Math.abs(dy) > Math.abs(dx) * 1.5f) {
            mHost.showGestureSuggestions();
            return false;
        }

        // Tap on the plain video while the controls are shown: hide them
        if (mOverlayWasShown && !mStartedInZone && !mMoved && duration < TAP_MAX_MS && mHost.isGestureOverlayShown()) {
            mHost.hideGestureOverlay();
        }

        return false;
    }

    private void startLevelGesture() {
        mHost.hideGestureOverlay(); // the touch down showed the controls

        if (mStartX < mScreenWidth / 2f) {
            mMode = MODE_BRIGHTNESS;
            mStartLevel = getBrightness();
        } else {
            mMode = MODE_VOLUME;
            mStartLevel = getVolume();
        }
    }

    private void applyLevel(float level) {
        int percent = Math.round(level * 100);

        if (mMode == MODE_BRIGHTNESS) {
            setBrightness(level);
            mHost.showGesturePill(mHost.getBrightnessText(percent), 0);
        } else {
            setVolume(level);
            mHost.showGesturePill(mHost.getVolumeText(percent), 0);
        }
    }

    private float getBrightness() {
        Activity activity = mHost.getGestureActivity();
        if (activity != null) {
            float current = activity.getWindow().getAttributes().screenBrightness;
            if (current >= 0f) {
                return current;
            }
        }
        try {
            return Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f;
        } catch (Exception e) {
            return 0.5f;
        }
    }

    private void setBrightness(float level) {
        Activity activity = mHost.getGestureActivity();
        if (activity == null) {
            return;
        }
        Window window = activity.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.screenBrightness = Math.max(0.02f, level); // 0 would switch the screen off on some devices
        window.setAttributes(params);
    }

    private float getVolume() {
        AudioManager audio = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) {
            return 0.5f;
        }
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return max > 0 ? audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max : 0.5f;
    }

    private void setVolume(float level) {
        AudioManager audio = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) {
            return;
        }
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(level * max), 0);
        } catch (SecurityException ex) {
            // some boxes refuse; nothing to do
        }
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /** Stops everything (screen closed). */
    public void release() {
        mHandler.removeCallbacks(mLongPress);
        if (mMode == MODE_SPEED) {
            mHost.setGestureSpeed(mSavedSpeed);
            mHost.hideGesturePill(0);
        }
        mMode = MODE_NONE;
    }
}
