package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

/**
 * Shows boot dialogs one by one.
 *
 * YouNew: the only boot step is the update check (see AppUpdater). It looks at this app's own GitHub releases
 * (the old one asked SmartTube's servers and was removed on purpose).
 */
public class BootDialogPresenter extends BasePresenter<Void> {
    @SuppressLint("StaticFieldLeak")
    private static BootDialogPresenter sInstance;

    public BootDialogPresenter(Context context) {
        super(context);
    }

    public static BootDialogPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new BootDialogPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void start() {
        // YouNew: look for a newer version on our own GitHub releases (at most every 12 hours, can be switched off)
        com.liskovsoft.smartyoutubetv2.common.misc.AppUpdater.checkInBackground(getContext());
    }
}
