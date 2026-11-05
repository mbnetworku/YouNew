package com.liskovsoft.smartyoutubetv2.tv.ui.settings;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * YouNew: the Settings panel on a screen of its own (used when it is opened with a page number, and as a
 * fallback). In the Home screen the panel is shown by SettingsPanelFragment, next to the sidebar.
 * The screen fades and grows in and out.
 */
public class SettingsScreenActivity extends MotherActivity {
    public static final String EXTRA_PAGE = "page";
    private SettingsPanelView mPanel;

    @Override
    protected void initTheme() {
        setTheme(R.style.App_Theme_SettingsScreen);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        overridePendingTransition(R.anim.settings_enter, R.anim.settings_stay);

        boolean pageGiven = getIntent().hasExtra(EXTRA_PAGE);
        int start = getIntent().getIntExtra(EXTRA_PAGE, 0);

        mPanel = new SettingsPanelView(this, this::finish, pageGiven ? start : -1, pageGiven, true);
        setContentView(mPanel);
    }

    @Override
    public void onBackPressed() {
        if (mPanel != null && mPanel.onBack()) {
            return;
        }

        super.onBackPressed();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.settings_stay, R.anim.settings_exit);
    }
}
