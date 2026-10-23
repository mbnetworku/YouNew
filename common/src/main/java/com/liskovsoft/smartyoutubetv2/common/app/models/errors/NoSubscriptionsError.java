package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;

/**
 * YouNew: shown on Subscriptions when you watch without a Google account and haven't subscribed to anything yet.
 * Subscriptions work without an account (they are saved on the box), so this screen explains how to add
 * a channel instead of asking you to sign in.
 */
public class NoSubscriptionsError implements ErrorFragmentData {
    private final Context mContext;

    public NoSubscriptionsError(Context context) {
        mContext = context;
    }

    @Override
    public void onAction() {
        // No button
    }

    @Override
    public String getMessage() {
        return mContext.getString(R.string.younew_no_subscriptions);
    }

    @Override
    public String getActionText() {
        return null; // no Sign in button: an account isn't needed
    }
}
