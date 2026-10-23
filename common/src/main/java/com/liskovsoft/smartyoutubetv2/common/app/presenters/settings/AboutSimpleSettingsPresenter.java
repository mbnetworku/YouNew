package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.os.Build;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * About page: just the app version and the device model. No links, no update check, no donations.
 */
public class AboutSimpleSettingsPresenter extends BasePresenter<Void> {
    public AboutSimpleSettingsPresenter(Context context) {
        super(context);
    }

    public static AboutSimpleSettingsPresenter instance(Context context) {
        return new AboutSimpleSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        List<OptionItem> info = new ArrayList<>();
        info.add(UiOptionItem.from(String.format("%s %s", getContext().getString(R.string.app_name),
                AppInfoHelpers.getAppVersionName(getContext()))));
        info.add(UiOptionItem.from(String.format("%s %s", Build.MANUFACTURER, Build.MODEL)));

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.settings_about), info);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_about));
    }
}
