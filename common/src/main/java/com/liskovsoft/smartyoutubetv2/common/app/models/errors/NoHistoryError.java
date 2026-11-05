package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;

/**
 * YouNew: shown on History when you watch without a Google account and haven't watched anything yet.
 * History works without an account (the watched videos are saved on the box), so this screen explains it
 * instead of asking you to sign in. The button opens Search (the screen needs a button for the remote).
 */
public class NoHistoryError implements ErrorFragmentData {
    private final Context mContext;

    public NoHistoryError(Context context) {
        mContext = context;
    }

    @Override
    public void onAction() {
        SearchPresenter.instance(mContext).startSearch(null);
    }

    @Override
    public String getMessage() {
        return mContext.getString(R.string.younew_no_history);
    }

    @Override
    public String getActionText() {
        return mContext.getString(R.string.younew_search_videos);
    }
}
