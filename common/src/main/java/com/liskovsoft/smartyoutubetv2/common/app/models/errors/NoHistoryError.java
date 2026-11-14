package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;

/**
 * YouNew: shown on History when you watch without a Google account and haven't watched anything yet.
 * Without an account History only has what was watched in this app (saved on the device). This screen says so and
 * its button signs in, which brings the whole YouTube watch history of the account.
 */
public class NoHistoryError implements ErrorFragmentData {
    private final Context mContext;

    public NoHistoryError(Context context) {
        mContext = context;
    }

    @Override
    public void onAction() {
        YTSignInPresenter.instance(mContext).start();
    }

    @Override
    public String getMessage() {
        return mContext.getString(R.string.younew_no_history);
    }

    @Override
    public String getActionText() {
        return mContext.getString(R.string.younew_history_signin_button);
    }
}
