package com.liskovsoft.smartyoutubetv2.common.misc;

import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

/**
 * YouNew: makes playing a video start faster.
 * <br>
 * Starting a video needs YouTube's video data (links, tokens). That used to be fetched only after you pressed OK,
 * which took several seconds even on a fast connection (the time is YouTube's token and link work, not the download).
 * Now the data is fetched while you rest on a card for a moment. The result is kept (see FormatInfoWrapper),
 * so when you press OK the player already has it. If you keep moving, nothing is fetched.
 * <br>
 * Used by the screens that show video cards (Home, Search, channels...). Runs on the main thread only to schedule.
 */
public class VideoPrefetcher {
    /** Rest time on a card before its data is fetched. Short enough to be ready, long enough to skip fast scrolling. */
    private static final long REST_DELAY_MS = 600;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static Runnable sPending;

    private VideoPrefetcher() {
    }

    /** Call when a card gets the focus. A newer call cancels the one still waiting. */
    public static void onCardSelected(Video video) {
        cancel();

        // Only real playable videos: no channels, playlists-only items, live streams or upcoming videos
        if (video == null || !video.hasVideo() || video.isLive || video.isUpcoming) {
            return;
        }

        sPending = () -> {
            sPending = null;
            MediaServiceManager.instance().loadFormatInfo(video, formatInfo -> { /* kept in the cache for the player */ });
        };

        sHandler.postDelayed(sPending, REST_DELAY_MS);
    }

    /** Stop a waiting fetch (the user moved on or left the screen). */
    public static void cancel() {
        if (sPending != null) {
            sHandler.removeCallbacks(sPending);
            sPending = null;
        }
    }
}
