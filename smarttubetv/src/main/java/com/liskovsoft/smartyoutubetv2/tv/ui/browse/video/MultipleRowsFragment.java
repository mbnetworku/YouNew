package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.RowPresenter.ViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ChannelHeaderPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ChannelHeaderPresenter.ChannelHeaderCallback;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ShortsCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CustomListRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemLongPressedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoSection;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class MultipleRowsFragment extends RowsSupportFragment implements VideoSection {
    private static final String TAG = MultipleRowsFragment.class.getSimpleName();
    private UriBackgroundManager mBackgroundManager;
    private ArrayObjectAdapter mRowsAdapter;
    private ListRowPresenter mRowPresenter;
    private Map<Integer, VideoGroupObjectAdapter> mVideoGroupAdapters;
    private final List<VideoGroup> mPendingUpdates = new ArrayList<>();
    private VideoGroupPresenter mMainPresenter;
    private VideoCardPresenter mCardPresenter;
    private ShortsCardPresenter mShortsPresenter;
    private int mSelectedRowIndex = -1;
    private ChannelHeaderCallback mChannelHeaderCallback;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mMainPresenter = getMainPresenter();
        mCardPresenter = new VideoCardPresenter();
        mShortsPresenter = new ShortsCardPresenter();
        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();

        setupAdapter();
        setupEventListeners();
        applyPendingUpdates();
    }

    protected void addHeader(ChannelHeaderCallback callback) {
        mChannelHeaderCallback = callback;
    }

    protected abstract VideoGroupPresenter getMainPresenter();

    @Override
    public void onViewCreated(@androidx.annotation.NonNull android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // YouNew: endless Home. A finger or a mouse wheel scrolls the list without moving the focus, so the
        // "focus is on one of the last rows" check never ran there and the list ended. Also watch the scrolling.
        androidx.leanback.widget.VerticalGridView grid = getVerticalGridView();
        if (grid != null) {
            grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@androidx.annotation.NonNull RecyclerView rv, int dx, int dy) {
                    if (dy > 0) {
                        checkNearBottom(rv);
                    }
                }

                @Override
                public void onScrollStateChanged(@androidx.annotation.NonNull RecyclerView rv, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        checkNearBottom(rv);
                    }
                }
            });
        }
    }

    /** The last row is (nearly) on the screen, or the list cannot scroll down any more: ask for more rows. */
    private void checkNearBottom(RecyclerView rv) {
        if (mRowsAdapter == null || mRowsAdapter.size() < 2
                || !(mMainPresenter instanceof com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter)) {
            return;
        }

        int last = -1;
        if (rv.getChildCount() > 0) {
            last = rv.getChildAdapterPosition(rv.getChildAt(rv.getChildCount() - 1));
        }

        if ((last >= 0 && last >= mRowsAdapter.size() - 2) || !rv.canScrollVertically(1)) {
            ((com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter) mMainPresenter).onRowsEnd();
        }
    }

    private void applyPendingUpdates() {
        // prevent modification within update method
        List<VideoGroup> copyArray = new ArrayList<>(mPendingUpdates);

        mPendingUpdates.clear();

        for (VideoGroup group : copyArray) {
            update(group);
        }
    }

    private void setupAdapter() {
        if (mVideoGroupAdapters == null) {
            mVideoGroupAdapters = new HashMap<>();
        }

        if (mRowsAdapter == null) {
            mRowPresenter = new CustomListRowPresenter();
            mRowPresenter.enableChildRoundedCorners(getMainUIData().isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS));

            ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
            presenterSelector.addClassPresenter(ListRow.class, mRowPresenter);
            presenterSelector.addClassPresenter(ChannelHeaderCallback.class, new ChannelHeaderPresenter());

            mRowsAdapter = new ArrayObjectAdapter(presenterSelector);
            setAdapter(mRowsAdapter);
        }
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
        mCardPresenter.setOnItemViewLongPressedListener(new ItemViewLongPressedListener());
        mShortsPresenter.setOnItemViewLongPressedListener(new ItemViewLongPressedListener());
    }

    @Override
    public void clear() {
        if (mRowsAdapter != null) {
            mRowsAdapter.clear();
            if (mChannelHeaderCallback != null) {
                mRowsAdapter.add(mChannelHeaderCallback);
            }
        }

        if (mVideoGroupAdapters != null) {
            mVideoGroupAdapters.clear();
        }

        // Reset the position (bug appeared after fragment been reused)
        setPosition(mChannelHeaderCallback != null ? 1 : 0);
    }

    private void removeByIndex(int idx) {
        if (mRowsAdapter != null && mRowsAdapter.size() > idx) {
            ListRow row = (ListRow) mRowsAdapter.get(idx);
            mRowsAdapter.remove(row);
            VideoGroupObjectAdapter group = (VideoGroupObjectAdapter) row.getAdapter();
            mVideoGroupAdapters.values().remove(group);
        }
    }

    private void removeById(int id) {
        if (mRowsAdapter != null) {
            VideoGroupObjectAdapter needed = mVideoGroupAdapters.get(id);
            for (int i = 0; i < mRowsAdapter.size(); i++) {
                Object row = mRowsAdapter.get(i);

                if (row instanceof ListRow) {
                    VideoGroupObjectAdapter adapter = (VideoGroupObjectAdapter) ((ListRow) row).getAdapter();
                    if (adapter == needed) {
                        mRowsAdapter.remove(row);
                        mVideoGroupAdapters.remove(id);
                    }
                }
            }
        }
    }

    private int findPositionById(int id) {
        if (mRowsAdapter != null) {
            VideoGroupObjectAdapter needed = mVideoGroupAdapters.get(id);
            for (int i = 0; i < mRowsAdapter.size(); i++) {
                Object row = mRowsAdapter.get(i);

                if (row instanceof ListRow) {
                    VideoGroupObjectAdapter adapter = (VideoGroupObjectAdapter) ((ListRow) row).getAdapter();
                    if (adapter == needed) {
                        return i;
                    }
                }
            }
        }

        return -1;
    }

    private boolean isComputingLayout(VideoGroup group) {
        int action = group.getAction();

        // Attempt to fix: IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling
        if ((action == VideoGroup.ACTION_SYNC || action == VideoGroup.ACTION_REPLACE) && getVerticalGridView() != null) {
            if (getVerticalGridView().isComputingLayout()) {
                return true;
            }
            int position = findPositionById(group.getId());
            if (position != -1) {
                RecyclerView.ViewHolder viewHolder = getVerticalGridView().findViewHolderForAdapterPosition(position);
                if (viewHolder != null) {
                    Object nestedRecyclerView = Helpers.getField(viewHolder, "mNestedRecyclerView");
                    if (nestedRecyclerView instanceof WeakReference) {
                        Object recyclerView = ((WeakReference<?>) nestedRecyclerView).get();
                        return recyclerView instanceof RecyclerView && ((RecyclerView) recyclerView).isComputingLayout();
                    }
                }
            }
        }

        return false;
    }

    @Override
    public boolean isEmpty() {
        if (mRowsAdapter == null) {
            return mPendingUpdates.isEmpty();
        }

        return mRowsAdapter.size() == 0;
    }

    @Override
    public void update(VideoGroup group) {
        if (isComputingLayout(group)) {
            return;
        }

        if (mVideoGroupAdapters == null) {
            mPendingUpdates.add(group);
            return;
        }

        // Correct position depending on the search bar presence
        if (group.getPosition() != -1 && mChannelHeaderCallback != null) {
            group.setPosition(group.getPosition() + 1);
        }

        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE) {
            if (group.getPosition() == -1) {
                clear();
            } else {
                removeById(group.getId());
            }
        } else if (action == VideoGroup.ACTION_REMOVE) {
            VideoGroupObjectAdapter adapter = mVideoGroupAdapters.get(group.getId());
            if (adapter != null) {
                adapter.remove(group);
            }
            return;
        } else if (action == VideoGroup.ACTION_SYNC) {
            VideoGroupObjectAdapter adapter = mVideoGroupAdapters.get(group.getId());
            if (adapter != null) {
                freeze(true);
                adapter.sync(group);
                freeze(false);
            }
            return;
        }

        if (group.isEmpty()) {
            return;
        }

        VideoGroupObjectAdapter existingAdapter = GridFragmentHelper.findRelatedAdapter(mVideoGroupAdapters, group, this::freeze);

        if (existingAdapter == null) {
            HeaderItem rowHeader = new HeaderItem(group.getTitle());
            int videoGroupId = group.getId(); // Create unique int from category.

            VideoGroupObjectAdapter videoGroupAdapter = new VideoGroupObjectAdapter(group, group.isShorts() ? mShortsPresenter : mCardPresenter);

            mVideoGroupAdapters.put(videoGroupId, videoGroupAdapter);

            ListRow row = new ListRow(rowHeader, videoGroupAdapter);

            if (group.getPosition() == -1 || group.getPosition() > mRowsAdapter.size()) {
                mRowsAdapter.add(row);
            } else {
                mRowsAdapter.add(group.getPosition(), row);
            }
        } else {
            Log.d(TAG, "Continue row %s %s", group.getTitle(), System.currentTimeMillis());

            freeze(true);

            existingAdapter.add(group); // continue

            freeze(false);
        }

        restorePosition();
    }

    private void restorePosition() {
        setPosition(mSelectedRowIndex);

        // Maybe we don't need to load next group since all rows already fetched?
    }

    @Override
    public int getPosition() {
        return getSelectedPosition();
    }

    @Override
    public void setPosition(int index) {
        if (index < 0) {
            return;
        }

        if (mRowsAdapter != null && index < mRowsAdapter.size()) {
            setSelectedPosition(index, false);
            mSelectedRowIndex = -1;
        } else {
            mSelectedRowIndex = index;
        }
    }

    @Override
    public void selectItem(Video item) {
        // NOP
    }

    /**
     * Disable scrolling on partially updated rows. This prevent cards from misbehaving.
     */
    private void freeze(boolean freeze) {
        // Disable scrolling on partially updated rows. This prevent controls from misbehaving.
        if (mRowPresenter != null) {
            ViewHolder vh = getRowViewHolder(getSelectedPosition());
            if (vh instanceof ListRowPresenter.ViewHolder) {
                mRowPresenter.freeze(vh, freeze);
            }
        }
    }

    private MainUIData getMainUIData() {
        return MainUIData.instance(getContext());
    }

    private final class ItemViewLongPressedListener implements OnItemLongPressedListener {
        @Override
        public void onItemLongPressed(Presenter.ViewHolder itemViewHolder, Object item) {

            if (item instanceof Video) {
                mMainPresenter.onVideoItemLongClicked((Video) item);
            } else {
                Toast.makeText(getActivity(), item.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final class ItemViewClickedListener implements androidx.leanback.widget.OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Video) {
                mMainPresenter.onVideoItemClicked((Video) item);
            } else {
                Toast.makeText(getActivity(), item.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Video) {
                mBackgroundManager.setBackgroundFrom((Video) item);

                mMainPresenter.onVideoItemSelected((Video) item);

                checkScrollEnd((Video)item);
                checkRowsEnd(row);
            }
        }

        /** YouNew: near the last rows of Home: ask for more rows (endless feed). */
        private void checkRowsEnd(Row row) {
            if (mRowsAdapter != null && row != null && mMainPresenter instanceof com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter) {
                int index = mRowsAdapter.indexOf(row);
                if (index != -1 && index >= mRowsAdapter.size() - 2) {
                    ((com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter) mMainPresenter).onRowsEnd();
                }
            }
        }

        private void checkScrollEnd(Video item) {
            for (VideoGroupObjectAdapter adapter : mVideoGroupAdapters.values()) {
                int index = adapter.indexOf(item);

                if (index != -1) {
                    int size = adapter.size();
                    if (index > (size - ViewUtil.ROW_SCROLL_CONTINUE_NUM)) {
                        mMainPresenter.onScrollEnd((Video) adapter.get(size - 1));
                    }
                    break;
                }
            }
        }
    }
}
