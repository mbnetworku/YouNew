package com.liskovsoft.smartyoutubetv2.tv.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.BrowseSupportFragment;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.SettingsSection;

/**
 * YouNew: Settings inside the Home screen. The Settings entry of the sidebar shows this panel to the right of
 * the sidebar (before, a click opened a list and then another screen on top of the app).
 * <br>
 * The panel draws the options itself (see SettingsPanelView); this fragment only fits it into the Home screen.
 * If the Settings are protected by a password, the panel appears after the password is typed.
 */
public class SettingsPanelFragment extends Fragment implements BrowseSupportFragment.MainFragmentAdapterProvider, SettingsSection {
    private final BrowseSupportFragment.MainFragmentAdapter<Fragment> mMainFragmentAdapter =
            new BrowseSupportFragment.MainFragmentAdapter<Fragment>(this);
    private FrameLayout mRoot;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getMainFragmentAdapter().getFragmentHost() != null) {
            getMainFragmentAdapter().getFragmentHost().notifyDataReady(getMainFragmentAdapter());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mRoot = new FrameLayout(getContext());
        // start below the top bar (search pill and logo)
        mRoot.setPadding(0, (int) (96 * getResources().getDisplayMetrics().density), 0, 0);

        String password = GeneralData.instance(getContext()).getSettingsPassword();

        if (password == null) {
            showPanel();
        } else {
            com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog.showPassword(
                    getContext(),
                    getContext().getString(R.string.enter_settings_password),
                    null,
                    newValue -> {
                        if (com.liskovsoft.smartyoutubetv2.common.utils.Utils.passwordMatch(password, newValue)) {
                            showPanel();
                            return true;
                        }
                        return false;
                    });
        }

        return mRoot;
    }

    private void showPanel() {
        if (mRoot == null || getContext() == null) {
            return;
        }

        mRoot.removeAllViews();
        mRoot.addView(new SettingsPanelView(getContext(), this::showSidebar, -1, false, false),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** The back arrow of the list of pages: go back to the sidebar. */
    private void showSidebar() {
        Fragment parent = getParentFragment();

        if (parent instanceof BrowseFragment) {
            ((BrowseFragment) parent).showSidebar();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getMainFragmentAdapter().getFragmentHost() != null) {
            getMainFragmentAdapter().getFragmentHost().notifyViewCreated(getMainFragmentAdapter());
        }
    }

    @Override
    public BrowseSupportFragment.MainFragmentAdapter<Fragment> getMainFragmentAdapter() {
        return mMainFragmentAdapter;
    }

    @Override
    public void update(SettingsGroup items) {
        // NOP: the panel builds its own pages
    }

    @Override
    public void clear() {
        // NOP
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
