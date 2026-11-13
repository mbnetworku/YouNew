/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package androidx.leanback.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.R;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.DividerPresenter;
import androidx.leanback.widget.DividerRow;
import androidx.leanback.widget.FocusHighlightHelper;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowHeaderPresenter;
import androidx.leanback.widget.SectionRow;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

/**
 * An fragment containing a list of row headers. Implementation must support three types of rows:
 * <ul>
 *     <li>{@link DividerRow} rendered by {@link DividerPresenter}.</li>
 *     <li>{@link Row} rendered by {@link RowHeaderPresenter}.</li>
 *     <li>{@link SectionRow} rendered by {@link RowHeaderPresenter}.</li>
 * </ul>
 * Use {@link #setPresenterSelector(PresenterSelector)} in subclass constructor to customize
 * Presenters. App may override {@link BrowseSupportFragment#onCreateHeadersSupportFragment()}.
 */
public class HeadersSupportFragment extends BaseRowSupportFragment {

    /**
     * Interface definition for a callback to be invoked when a header item is clicked.
     */
    public interface OnHeaderClickedListener {
        /**
         * Called when a header item has been clicked.
         *
         * @param viewHolder Row ViewHolder object corresponding to the selected Header.
         * @param row Row object corresponding to the selected Header.
         */
        void onHeaderClicked(RowHeaderPresenter.ViewHolder viewHolder, Row row);
    }

    /**
     * Interface definition for a callback to be invoked when a header item is selected.
     */
    public interface OnHeaderViewSelectedListener {
        /**
         * Called when a header item has been selected.
         *
         * @param viewHolder Row ViewHolder object corresponding to the selected Header.
         * @param row Row object corresponding to the selected Header.
         */
        void onHeaderSelected(RowHeaderPresenter.ViewHolder viewHolder, Row row);
    }

    private OnHeaderViewSelectedListener mOnHeaderViewSelectedListener;
    OnHeaderClickedListener mOnHeaderClickedListener;
    private boolean mHeadersEnabled = true;
    private int mGridPadStart = -1;
    private int mGridPadEnd;
    private boolean mHeadersGone = false;
    private int mBackgroundColor;
    private boolean mBackgroundColorSet;

    private static final PresenterSelector sHeaderPresenter = new ClassPresenterSelector()
            .addClassPresenter(DividerRow.class, new DividerPresenter())
            .addClassPresenter(SectionRow.class,
                    new RowHeaderPresenter(R.layout.lb_section_header, false))
            .addClassPresenter(Row.class, new RowHeaderPresenter(R.layout.lb_header));

    public HeadersSupportFragment() {
        setPresenterSelector(sHeaderPresenter);
        FocusHighlightHelper.setupHeaderItemFocusHighlight(getBridgeAdapter());
    }

    public void setOnHeaderClickedListener(OnHeaderClickedListener listener) {
        mOnHeaderClickedListener = listener;
    }

    public void setOnHeaderViewSelectedListener(OnHeaderViewSelectedListener listener) {
        mOnHeaderViewSelectedListener = listener;
    }

    @Override
    VerticalGridView findGridViewFromRoot(View view) {
        return (VerticalGridView) view.findViewById(R.id.browse_headers);
    }

    @Override
    void onRowSelected(RecyclerView parent, RecyclerView.ViewHolder viewHolder,
            int position, int subposition) {
        if (mOnHeaderViewSelectedListener != null) {
            if (viewHolder != null && position >= 0) {
                ItemBridgeAdapter.ViewHolder vh = (ItemBridgeAdapter.ViewHolder) viewHolder;
                mOnHeaderViewSelectedListener.onHeaderSelected(
                        (RowHeaderPresenter.ViewHolder) vh.getViewHolder(), (Row) vh.getItem());
            } else {
                mOnHeaderViewSelectedListener.onHeaderSelected(null, null);
            }
        }
    }

    private int mClickedPosition = -1;

    /** YouNew: adapter position of the entry that was clicked or tapped last. */
    int getClickedPosition() {
        return mClickedPosition;
    }

    private final ItemBridgeAdapter.AdapterListener mAdapterListener =
            new ItemBridgeAdapter.AdapterListener() {
        @Override
        public void onCreate(final ItemBridgeAdapter.ViewHolder viewHolder) {
            View headerView = viewHolder.getViewHolder().view;
            headerView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // YouNew: remember which entry was tapped (a tap does not move focus, so the selection
                    // would otherwise stay on the old section)
                    mClickedPosition = viewHolder.getAdapterPosition();
                    if (mOnHeaderClickedListener != null) {
                        mOnHeaderClickedListener.onHeaderClicked(
                                (RowHeaderPresenter.ViewHolder) viewHolder.getViewHolder(),
                                (Row) viewHolder.getItem());
                    }
                }
            });
            if (mWrapper != null) {
                viewHolder.itemView.addOnLayoutChangeListener(sLayoutChangeListener);
            } else {
                headerView.addOnLayoutChangeListener(sLayoutChangeListener);
            }
        }

        @Override
        public void onBind(ItemBridgeAdapter.ViewHolder viewHolder) {
            applyRailState(viewHolder.itemView);
            applyRowSize(viewHolder.itemView); // YouNew: row size for this screen
        }
    };

    static OnLayoutChangeListener sLayoutChangeListener = new OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
            v.setPivotX(v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? v.getWidth() : 0);
            v.setPivotY(v.getMeasuredHeight() / 2);
        }
    };

    @Override
    int getLayoutResourceId() {
        return R.layout.lb_headers_fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final VerticalGridView listView = getVerticalGridView();
        if (listView == null) {
            return;
        }
        if (mBackgroundColorSet) {
            listView.setBackgroundColor(mBackgroundColor);
            updateFadingEdgeToBrandColor(mBackgroundColor);
        } else {
            Drawable d = listView.getBackground();
            if (d instanceof ColorDrawable) {
                updateFadingEdgeToBrandColor(((ColorDrawable) d).getColor());
            }
        }
        updateListViewVisibility();

        // YouNew: fit the rows to the height of the sidebar (and again when it changes, e.g. the phone is turned)
        listView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                fitRowsToHeight();
            }
        });
    }

    private void updateListViewVisibility() {
        final VerticalGridView listView = getVerticalGridView();
        if (listView != null) {
            getView().setVisibility(mHeadersGone ? View.GONE : View.VISIBLE);
            if (!mHeadersGone) {
                if (mHeadersEnabled) {
                    listView.setChildrenVisibility(View.VISIBLE);
                } else {
                    listView.setChildrenVisibility(View.VISIBLE); // icon rail stays visible
                }
            }
        }
    }

    void setHeadersEnabled(boolean enabled) {
        mHeadersEnabled = enabled;
        updateListViewVisibility();
        // Rail mode: narrow the pane to an icon strip when collapsed, full width when expanded.
        if (getView() != null) {
            float density = getResources().getDisplayMetrics().density;
            int width = (int) ((enabled ? 240 : 72) * density);
            ViewGroup.LayoutParams rootLp = getView().getLayoutParams();
            rootLp.width = width;
            getView().setLayoutParams(rootLp);
            VerticalGridView grid = getVerticalGridView();
            if (grid != null) {
                if (mGridPadStart < 0) {
                    mGridPadStart = grid.getPaddingStart();
                    mGridPadEnd = grid.getPaddingEnd();
                }
                int top = mGridTopPx > 0 ? mGridTopPx : (int) (96 * density); // YouNew: the fitted gap, if known
                grid.setPaddingRelative((int) (12 * density), top, (int) (12 * density), grid.getPaddingBottom());
                grid.setClipToPadding(false); // the focused pill grows a little: let it draw outside the padding
                grid.setClipChildren(false);
                ViewGroup.LayoutParams gridLp = grid.getLayoutParams();
                gridLp.width = width;
                grid.setLayoutParams(gridLp);
            }
            android.widget.TextView brand = (android.widget.TextView) getView().findViewById(R.id.younew_brand);
            if (brand != null) {
                brand.setText(enabled ? "YouTv Plus" : "");
            }
        }
        final VerticalGridView grid = getVerticalGridView();
        if (grid != null) {
            for (int i = 0; i < grid.getChildCount(); i++) {
                applyRailState(grid.getChildAt(i));
            }
        }
    }

    // Keep the short menu list at the top of the sidebar instead of scrolling the selected item to a fixed offset.
    @Override
    public void setAlignment(int windowAlignOffsetTop) {
        VerticalGridView grid = getVerticalGridView();
        if (grid != null) {
            grid.setItemAlignmentOffset(0);
            grid.setItemAlignmentOffsetPercent(VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);
            grid.setWindowAlignmentOffset(0);
            grid.setWindowAlignmentOffsetPercent(VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
            grid.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_BOTH_EDGE);
        }
    }

    void setHeadersGone(boolean gone) {
        mHeadersGone = gone;
        updateListViewVisibility();
    }

    // ---- YouNew: the rows size themselves to the screen height, so every entry fits without scrolling ----
    private int mItemHeightPx = -1;   // height of one row (without its 2 + 2 dp margins)
    private int mIconPx;
    private float mLabelSp;
    private int mGridTopPx = -1;
    private int mLastFitHeight = -1;
    private int mLastFitCount = -1;

    private static float clampF(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Works out the size of the rows from the height of the sidebar and the number of entries, and applies it.
     * TV (about 540 dp high): the same big rows as before. Phone held sideways (about 360 dp): smaller rows and icons,
     * so all entries are on the screen at once. The decision is made again when the height or the entries change.
     */
    void fitRowsToHeight() {
        final VerticalGridView grid = getVerticalGridView();
        if (grid == null || getView() == null || grid.getHeight() <= 0) {
            return;
        }

        int count = getAdapter() != null ? Math.max(1, getAdapter().size()) : 8;
        if (grid.getHeight() == mLastFitHeight && count == mLastFitCount) {
            return;
        }
        mLastFitHeight = grid.getHeight();
        mLastFitCount = count;

        float density = getResources().getDisplayMetrics().density;
        float heightDp = grid.getHeight() / density;

        float topDp = clampF(heightDp * 0.15f, 52f, 96f);                // room for the YouTv Plus mark
        float brandTopDp = clampF((topDp - 40f) * 0.5f, 6f, 28f);
        float bottomDp = 8f;
        float perRowDp = clampF((heightDp - topDp - bottomDp) / count, 30f, 60f); // row + its 4 dp of margins
        float rowDp = perRowDp - 4f;

        mItemHeightPx = Math.round(rowDp * density);
        mIconPx = Math.round(clampF(rowDp * 0.62f, 16f, 34f) * density);
        mLabelSp = clampF(rowDp * 0.365f, 10f, 19f);
        mGridTopPx = Math.round(topDp * density);

        grid.setPaddingRelative(grid.getPaddingStart(), mGridTopPx, grid.getPaddingEnd(), Math.round(bottomDp * density));

        View brand = getView().findViewById(R.id.younew_brand);
        if (brand != null && brand.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) brand.getLayoutParams()).topMargin = Math.round(brandTopDp * density);
            brand.requestLayout();
        }

        for (int i = 0; i < grid.getChildCount(); i++) {
            applyRowSize(grid.getChildAt(i));
        }
        grid.requestLayout();
    }

    /** Sets the height of the row, the size of its icon and the size of its text (see fitRowsToHeight). */
    void applyRowSize(View view) {
        if (mItemHeightPx <= 0) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;

        if (view instanceof android.widget.ImageView) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp != null) {
                lp.width = mIconPx;
                lp.height = mIconPx;
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    // keep the icon in the middle of the 48 dp wide pill of the rail
                    ((ViewGroup.MarginLayoutParams) lp).setMarginStart(Math.max(0, Math.round((48f * density - mIconPx) / 2f)));
                }
                view.setLayoutParams(lp);
            }
        } else if (view instanceof android.widget.TextView) {
            ((android.widget.TextView) view).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, mLabelSp);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            boolean isRow = false;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (group.getChildAt(i) instanceof android.widget.ImageView) {
                    isRow = true;
                }
            }
            if (isRow) {
                ViewGroup.LayoutParams lp = group.getLayoutParams();
                if (lp != null && lp.height != mItemHeightPx) {
                    lp.height = mItemHeightPx;
                    group.setLayoutParams(lp);
                }
            }
            for (int i = 0; i < group.getChildCount(); i++) {
                applyRowSize(group.getChildAt(i));
            }
        }
    }

    // Rail mode: only icons show; text labels appear when the header list is expanded.
    void applyRailState(View view) {
        if (view instanceof android.widget.TextView) {
            view.setVisibility(mHeadersEnabled ? View.VISIBLE : View.GONE); // collapsed sidebar = icons only
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyRailState(group.getChildAt(i));
            }
        }
    }

    static class NoOverlappingFrameLayout extends FrameLayout {

        public NoOverlappingFrameLayout(Context context) {
            super(context);
        }

        /**
         * Avoid creating hardware layer for header dock.
         */
        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    // Wrapper needed because of conflict between RecyclerView's use of alpha
    // for ADD animations, and RowHeaderPresenter's use of alpha for selected level.
    final ItemBridgeAdapter.Wrapper mWrapper = new ItemBridgeAdapter.Wrapper() {
        @Override
        public void wrap(View wrapper, View wrapped) {
            ((FrameLayout) wrapper).addView(wrapped);
        }

        @Override
        public View createWrapper(View root) {
            NoOverlappingFrameLayout wrapper = new NoOverlappingFrameLayout(root.getContext());
            // YouNew: full-width rows, so the selected pill is as wide as the sidebar
            wrapper.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return wrapper;
        }
    };
    @Override
    void updateAdapter() {
        super.updateAdapter();
        ItemBridgeAdapter adapter = getBridgeAdapter();
        adapter.setAdapterListener(mAdapterListener);
        adapter.setWrapper(mWrapper);
    }

    void setBackgroundColor(int color) {
        mBackgroundColor = color;
        mBackgroundColorSet = true;

        if (getVerticalGridView() != null) {
            getVerticalGridView().setBackgroundColor(mBackgroundColor);
            updateFadingEdgeToBrandColor(mBackgroundColor);
        }
    }

    private void updateFadingEdgeToBrandColor(int backgroundColor) {
        View fadingView = getView().findViewById(R.id.fade_out_edge);
        Drawable background = fadingView.getBackground();
        if (background instanceof GradientDrawable) {
            background.mutate();
            ((GradientDrawable) background).setColors(
                    new int[] {Color.TRANSPARENT, backgroundColor});
        }
    }

    @Override
    public void onTransitionStart() {
        super.onTransitionStart();
        if (!mHeadersEnabled) {
            // When enabling headers fragment,  the RowHeaderView gets a focus but
            // isShown() is still false because its parent is INVISIBLE, accessibility
            // event is not sent.
            // Workaround is: prevent focus to a child view during transition and put
            // focus on it after transition is done.
            final VerticalGridView listView = getVerticalGridView();
            if (listView != null) {
                listView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
                if (listView.hasFocus()) {
                    listView.requestFocus();
                }
            }
        }
    }

    @Override
    public void onTransitionEnd() {
        if (mHeadersEnabled) {
            final VerticalGridView listView = getVerticalGridView();
            if (listView != null) {
                listView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                if (listView.hasFocus()) {
                    listView.requestFocus();
                }
            }
        }
        super.onTransitionEnd();
    }

    public boolean isScrolling() {
        return getVerticalGridView().getScrollState()
                != HorizontalGridView.SCROLL_STATE_IDLE;
    }
}
