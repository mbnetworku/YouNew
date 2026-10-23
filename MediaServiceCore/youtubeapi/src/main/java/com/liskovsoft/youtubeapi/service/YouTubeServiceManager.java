package com.liskovsoft.youtubeapi.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.ChannelGroupService;
import com.liskovsoft.mediaserviceinterfaces.CommentsService;
import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.LiveChatService;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.NotificationsService;
import com.liskovsoft.mediaserviceinterfaces.RemoteControlService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.app.AppService;
import com.liskovsoft.youtubeapi.app.PoTokenGate;
import com.liskovsoft.youtubeapi.app.ReloadPlaybackGate;
import com.liskovsoft.youtubeapi.common.helpers.AppClient;
import com.liskovsoft.youtubeapi.channelgroups.ChannelGroupServiceImpl;
import com.liskovsoft.googlecommon.common.locale.LocaleManager;
import com.liskovsoft.youtubeapi.service.internal.FormatInfoWrapper;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;
import com.liskovsoft.youtubeapi.videoinfo.V2.VideoInfoService;

import io.reactivex.disposables.Disposable;

public class YouTubeServiceManager implements ServiceManager {
    private static final String TAG = YouTubeServiceManager.class.getSimpleName();
    private static final long PO_TOKEN_WARM_UP_DELAY_MS = 5_000;
    private static YouTubeServiceManager sInstance;
    private Disposable mRefreshCoreDataAction;

    private YouTubeServiceManager() {
        Log.d(TAG, "Starting...");
    }

    public static ServiceManager instance() {
        if (sInstance == null) {
            sInstance = new YouTubeServiceManager();
        }

        return sInstance;
    }

    @Override
    public SignInService getSignInService() {
        return getYouTubeSignInService();
    }

    @Override
    public RemoteControlService getRemoteControlService() {
        return getYouTubeRemoteControlService();
    }

    @Override
    public LiveChatService getLiveChatService() {
        return getYouTubeLiveChatService();
    }

    @Override
    public CommentsService getCommentsService() {
        return getYouTubeCommentsService();
    }

    @Override
    public ContentService getContentService() {
        return getYouTubeContentService();
    }

    @Override
    public MediaItemService getMediaItemService() {
        return getYouTubeMediaItemService();
    }

    @Override
    public NotificationsService getNotificationsService() {
        return getYouTubeNotificationsService();
    }

    @Override
    public ChannelGroupService getChannelGroupService() {
        return getChannelGroupServiceImpl();
    }

    @Override
    public void invalidateCache() {
        LocaleManager.unhold();
        getYouTubeSignInService().invalidateCache(); // sections infinite loading fix (request timed out fix)
        getAppService().invalidateCache();
        //AppService.instance().invalidateVisitorData();
        FormatInfoWrapper.invalidateCache();
        FormatInfoWrapper.resetClient();
    }

    @Override
    public void refreshCacheIfNeeded() {
        refreshCacheIfNeededInt();
    }

    @Override
    public void switchNextClient() {
        FormatInfoWrapper.invalidateCache();
        FormatInfoWrapper.switchNextClient(false);
    }

    @Override
    public void switchNextClientNow() {
        FormatInfoWrapper.invalidateCache();
        FormatInfoWrapper.switchNextClient(true);
    }

    @Override
    public void switchNextSubsFormat() {
        FormatInfoWrapper.invalidateCache();
        getVideoInfoService().switchNextSubtitle();
    }

    /**
     * SABR server sent ReloadPlayerResponse and won't send any media until the player response is re-fetched.<br/>
     * The next player request for this video will include the token (reloadPlaybackContext).
     */
    @Override
    public void reloadPlayerResponse(String videoId, String reloadPlaybackToken) {
        FormatInfoWrapper.invalidateCache();
        ReloadPlaybackGate.setToken(videoId, reloadPlaybackToken);
    }

    private void refreshCacheIfNeededInt() {
        if (RxHelper.isAnyActionRunning(mRefreshCoreDataAction)) {
            return;
        }

        mRefreshCoreDataAction =
                RxHelper.execute(RxHelper.fromRunnable(() -> {
                    AppClient client = getVideoInfoService().getClient();
                    getAppService().refreshCacheIfNeeded(client);
                    warmUpPoToken(client);
                }));
    }

    /**
     * YouNew: the "proof of device" token (PO token) takes about 3 seconds to create on a TV box.
     * Creating it a few seconds after start-up, while the user is still looking at the Home screen,
     * means the first video does not have to wait for it.
     */
    private static void warmUpPoToken(AppClient client) {
        if (client == null) {
            return;
        }

        try {
            Thread.sleep(PO_TOKEN_WARM_UP_DELAY_MS); // let the Home screen load first
            PoTokenGate.getPoToken(client);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            Log.e(TAG, "PO token warm-up failed: %s", e.getMessage());
        }
    }

    @NonNull
    private static YouTubeSignInService getYouTubeSignInService() {
        return YouTubeSignInService.instance();
    }

    @NonNull
    private static YouTubeRemoteControlService getYouTubeRemoteControlService() {
        return YouTubeRemoteControlService.instance();
    }

    @NonNull
    private static ContentService getYouTubeContentService() {
        return YouTubeContentService.instance();
    }

    @NonNull
    private static YouTubeMediaItemService getYouTubeMediaItemService() {
        return YouTubeMediaItemService.instance();
    }

    @NonNull
    private static YouTubeLiveChatService getYouTubeLiveChatService() {
        return YouTubeLiveChatService.instance();
    }

    @NonNull
    private static CommentsService getYouTubeCommentsService() {
        return YouTubeCommentsService.INSTANCE;
    }

    @NonNull
    private static VideoInfoService getVideoInfoService() {
        return VideoInfoService.instance();
    }

    @NonNull
    private static AppService getAppService() {
        return AppService.instance();
    }

    @NonNull
    private static NotificationsService getYouTubeNotificationsService() {
        return YouTubeNotificationsService.INSTANCE;
    }

    @NonNull
    private static ChannelGroupService getChannelGroupServiceImpl() {
        return ChannelGroupServiceImpl.INSTANCE;
    }

    @Nullable
    private static MediaServiceData getMediaServiceData() {
        return MediaServiceData.instance();
    }
}
