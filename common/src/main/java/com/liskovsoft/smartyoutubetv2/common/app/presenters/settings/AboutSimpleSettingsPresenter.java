package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.os.Build;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.misc.AppUpdater;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * About page: the app version, the device model, and the update switch and button. No links, no donations.
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

        // YouNew: the app updates itself from its own GitHub releases
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.younew_update_auto),
                option -> AppUpdater.setAutoCheckEnabled(getContext(), option.isSelected()),
                AppUpdater.isAutoCheckEnabled(getContext())));
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.younew_update_beta),
                option -> AppUpdater.setBetaEnabled(getContext(), option.isSelected()),
                AppUpdater.isBetaEnabled(getContext())));
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.younew_update_check),
                option -> AppUpdater.checkNow(getContext())));

        settingsPresenter.showDialog(getContext().getString(R.string.settings_about));
    }
}
