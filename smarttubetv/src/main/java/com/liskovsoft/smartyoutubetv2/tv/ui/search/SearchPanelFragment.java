package com.liskovsoft.smartyoutubetv2.tv.ui.search;

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
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.SettingsSection;

/**
 * YouNew: Search inside the Home screen. The Search entry of the sidebar shows this panel to the right of the
 * sidebar (like YouTube TV): the query, the suggestions list and a compact keyboard. The search itself (the list
 * of results) opens with the normal search screen.
 */
public class SearchPanelFragment extends Fragment implements BrowseSupportFragment.MainFragmentAdapterProvider, SettingsSection {
    private final BrowseSupportFragment.MainFragmentAdapter<Fragment> mMainFragmentAdapter =
            new BrowseSupportFragment.MainFragmentAdapter<Fragment>(this);
    private SearchPanelView mPanel;

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
        FrameLayout root = new FrameLayout(getContext());
        // start below the top bar (logo and topic buttons)
        root.setPadding(0, (int) (64 * getResources().getDisplayMetrics().density), 0, 0);

        mPanel = new SearchPanelView(getContext());
        root.addView(mPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getMainFragmentAdapter().getFragmentHost() != null) {
            getMainFragmentAdapter().getFragmentHost().notifyViewCreated(getMainFragmentAdapter());
        }
    }

    @Override
    public void onDestroyView() {
        if (mPanel != null) {
            mPanel.release();
            mPanel = null;
        }
        super.onDestroyView();
    }

    @Override
    public BrowseSupportFragment.MainFragmentAdapter<Fragment> getMainFragmentAdapter() {
        return mMainFragmentAdapter;
    }

    @Override
    public void update(SettingsGroup items) {
        // NOP
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
