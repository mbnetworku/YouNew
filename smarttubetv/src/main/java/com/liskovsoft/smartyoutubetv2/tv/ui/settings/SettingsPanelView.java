package com.liskovsoft.smartyoutubetv2.tv.ui.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * YouNew: the Settings panel, drawn as tabs. The pages are a row of tabs along the top (Accounts, General, Player,
 * ...) and the options of the chosen page are in one rounded card below, so a click or a touch on either always works.
 * <br>
 * It is a plain view, so it can live inside the Home screen (next to the sidebar, see SettingsPanelFragment)
 * or fill a screen of its own (SettingsScreenActivity).
 * <br>
 * Remote: Left/Right choose a tab, Down goes into its options, Up comes back to the tabs. Mouse or touch: click a
 * tab, click an option. The row of tabs scrolls sideways when the screen is narrow (a phone).
 * <br>
 * Choices open inside the page (no pop-up): a click on a choice row unfolds its list right under it.
 * <br>
 * No motion: nothing slides, fades or grows (the owner found it slow). Only the colour of the focused or pressed
 * item changes.
 * <br>
 * Settings > About shows a progress bar under "Check for updates now" while an update is looked for or downloaded.
 * <br>
 * The options come from the same presenters as before (they build option lists for AppDialogPresenter);
 * this panel draws those lists itself. See {@link AppDialogPresenter#setCapture}.
 */
public class SettingsPanelView extends FrameLayout {
    /** Who shows the panel: told when the user leaves the list of pages with the back arrow. */
    public interface Host {
        void onClose();
    }

    private static final String TAG = SettingsPanelView.class.getSimpleName();
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int PILL = 0xFFF1F1F1;
    private static final int CARD = 0xFF1A1A1A;
    private static final int FOCUS = 0xFF3A3A3A;
    private static final int PRESSED = 0xFF4A4A4A;
    private static final long FOCUS_DELAY_MS = 150;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<View> mPageViews = new ArrayList<>();
    private final List<View> mRows = new ArrayList<>();
    private final List<Runnable> mOnFinish = new ArrayList<>();
    private final Host mHost;
    private final int mStartPage;
    private final boolean mOpenOptions;
    private final boolean mTakeFocus;
    private List<SettingsItem> mPages = new ArrayList<>();
    private LinearLayout mContent;
    private ScrollView mScroll;
    private int mPageIconRes;
    private Runnable mCollapseOpenChoices;
    private ViewGroup mLeftPanel; // scrolls when the list of pages is taller than the screen
    private LinearLayout mLeftList;
    private int mLastLeft = -1;
    private int mLastTop = -1;
    private boolean mTwoPane;
    private boolean mShowingOptions;
    private int mCurrentPage = -1;
    private int mLoadId;
    private Runnable mPendingPage;

    /**
     * @param startPage   the page to show first (-1: the first one on a wide panel, the list on a narrow one)
     * @param openOptions on a narrow panel, show the options of {@code startPage} at once
     * @param takeFocus   move the remote focus into the panel (a full screen does; the Home screen keeps it on the sidebar)
     */
    public SettingsPanelView(Context context, Host host, int startPage, boolean openOptions, boolean takeFocus) {
        super(context);
        mHost = host;
        mStartPage = startPage;
        mOpenOptions = openOptions;
        mTakeFocus = takeFocus;
        mPages = AppDataSourceManager.instance().getSettingItems(context);

        addView(buildScreen());

        int start = Math.max(0, Math.min(startPage < 0 ? 0 : startPage, mPages.size() - 1));

        if (mTwoPane || (openOptions && startPage >= 0)) {
            showPage(start);
            showOptions(true, false);
        }

        if (takeFocus && start < mPageViews.size()) {
            mPageViews.get(start).requestFocus();
        }
    }

    /**
     * The Home screen moves this panel to the right when the sidebar is expanded, but gives it the same width as
     * before, so its right part would be cut off by the edge of the screen. The panel takes only the part of the
     * screen that is really left (and the same for the height).
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int[] at = new int[2];
        getLocationOnScreen(at);

        widthMeasureSpec = fitSpec(widthMeasureSpec, metrics.widthPixels - Math.max(0, at[0]));
        heightMeasureSpec = fitSpec(heightMeasureSpec, metrics.heightPixels - Math.max(0, at[1]));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private static int fitSpec(int spec, int max) {
        int mode = MeasureSpec.getMode(spec);
        if (mode != MeasureSpec.UNSPECIFIED && max > 0 && MeasureSpec.getSize(spec) > max) {
            return MeasureSpec.makeMeasureSpec(max, mode);
        }
        return spec;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // The panel was moved (the sidebar opened or closed): measure again with what is visible now
        int[] at = new int[2];
        getLocationOnScreen(at);
        if (at[0] != mLastLeft || at[1] != mLastTop) {
            mLastLeft = at[0];
            mLastTop = at[1];
            post(this::requestLayout);
        }
    }

    /** The place the panel is in got another width (phone turned, or the real size is known now): choose the layout again. */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w > 0 && wantTwoPane(w) != mTwoPane) {
            rebuild();
        }
    }

    private boolean wantTwoPane(int widthPx) {
        return true; // tabs on top and options below at every width
    }

    private void rebuild() {
        int page = mCurrentPage;
        boolean showing = mShowingOptions;
        mCurrentPage = -1;
        mPageViews.clear();
        mRows.clear();
        ++mLoadId; // drop a page that was still loading

        removeAllViews();
        addView(buildScreen());

        if (page >= 0) {
            showPage(page);
            showOptions(showing || mTwoPane, false);
        }
    }

    /** Goes back one step inside the panel. Returns false when there is nothing to go back to (the list of pages is shown). */
    public boolean onBack() {
        if (!mTwoPane && mShowingOptions) {
            showOptions(false, true);
            return true;
        }

        return false;
    }

    private void onBackPressed() {
        if (!onBack() && mHost != null) {
            mHost.onClose();
        }
    }

    // The Home screen slides the panel when the sidebar opens or closes WITHOUT laying the panel out again, so
    // onLayout never saw the move: a panel made while the sidebar was open stayed as narrow as it was then, and
    // its page names were cut off on a phone. Its place is now looked at before every frame; when it has moved
    // and come to rest, the panel is measured again.
    private final int[] mPlace = new int[2];
    private final Runnable mMeasureAgain = this::requestLayout;
    private final android.view.ViewTreeObserver.OnPreDrawListener mWatchPlace = () -> {
        getLocationOnScreen(mPlace);
        if (mPlace[0] != mLastLeft || mPlace[1] != mLastTop) {
            mLastLeft = mPlace[0];
            mLastTop = mPlace[1];
            mHandler.removeCallbacks(mMeasureAgain);
            mHandler.postDelayed(mMeasureAgain, 80);
        }
        return true;
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(mWatchPlace);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(mWatchPlace);
        mHandler.removeCallbacksAndMessages(null);
        AppDialogPresenter.instance(getContext()).setCapture(null);
        runFinishCallbacks(); // some pages ask for a restart or a refresh when you leave them
        super.onDetachedFromWindow();
    }

    private void runFinishCallbacks() {
        List<Runnable> callbacks = new ArrayList<>(mOnFinish);
        mOnFinish.clear();

        for (Runnable callback : callbacks) {
            try {
                callback.run();
            } catch (RuntimeException e) {
                Log.e(TAG, "Finish callback error: %s", e.getMessage());
            }
        }
    }

    private String getString(int resId) {
        return getContext().getString(resId);
    }

    private boolean isFinishing() {
        return !isAttachedToWindow();
    }

    // ---------------------------------------------------------------- screen

    private View buildScreen() {
        mTwoPane = true;
        boolean narrow = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density < 600f;

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);

        // Top: back arrow and the row of tabs (scrolls sideways when it is wider than the screen)
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(narrow ? 8 : 24), dp(8), dp(narrow ? 8 : 24), dp(4));
        bar.addView(backArrow());

        final android.widget.HorizontalScrollView tabScroll = new android.widget.HorizontalScrollView(getContext());
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setClipToPadding(false);
        mLeftPanel = tabScroll;
        LinearLayout tabs = new LinearLayout(getContext());
        mLeftList = tabs;
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabScroll.addView(tabs, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams tabScrollParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        tabScrollParams.setMarginStart(dp(8));
        bar.addView(tabScroll, tabScrollParams);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ColorStateList onPill = new ColorStateList(
                new int[][]{{android.R.attr.state_selected}, {}}, new int[]{Color.BLACK, TEXT});

        for (int i = 0; i < mPages.size(); i++) {
            final int index = i;
            LinearLayout tab = new LinearLayout(getContext());
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(dp(14), 0, dp(18), 0);
            tab.setFocusable(true);
            tab.setClickable(true);
            tab.setBackground(pageBackground());
            tab.addView(icon(pageIcon(i), 26));

            TextView label = text(mPages.get(i).title, 18, TEXT, false);
            label.setSingleLine(true);
            label.setTextColor(onPill);
            label.setPadding(dp(8), 0, 0, 0);
            tab.addView(label);

            tab.setOnClickListener(v -> showPage(index));
            // The remote moves the focus: show that page after a short moment (not while moving fast)
            tab.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    showPageDelayed(index);
                    tabScroll.post(() -> tabScroll.smoothScrollTo(Math.max(0, v.getLeft() - dp(40)), 0));
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
            lp.setMarginEnd(dp(6));
            tabs.addView(tab, lp);
            mPageViews.add(tab);
        }

        // Below: the options of the chosen page in one rounded card
        mScroll = new ScrollView(getContext());
        mScroll.setFillViewport(true);
        mScroll.setClipToPadding(false);
        mContent = new LinearLayout(getContext());
        mContent.setOrientation(LinearLayout.VERTICAL);
        mContent.setPadding(dp(narrow ? 12 : 28), dp(narrow ? 12 : 20), dp(narrow ? 12 : 28), dp(32));
        mScroll.addView(mContent, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mScroll.setBackground(round(CARD, 28));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        cardParams.setMargins(dp(narrow ? 8 : 24), dp(4), dp(narrow ? 8 : 24), dp(12));
        root.addView(mScroll, cardParams);

        return root;
    }

    // ---------------------------------------------------------------- pages

    /** Tabs on top and options below are always both visible: nothing to switch. */
    private void showOptions(boolean show, boolean animate) {
        mShowingOptions = show;
    }

    private void showPageDelayed(int index) {
        if (mPendingPage != null) {
            mHandler.removeCallbacks(mPendingPage);
        }

        mPendingPage = () -> showPage(index);
        mHandler.postDelayed(mPendingPage, FOCUS_DELAY_MS);
    }

    private void showPage(int index) {
        if (index < 0 || index >= mPages.size()) {
            return;
        }

        if (mPendingPage != null) {
            mHandler.removeCallbacks(mPendingPage);
            mPendingPage = null;
        }

        if (index == mCurrentPage) {
            return;
        }

        mCurrentPage = index;

        for (int i = 0; i < mPageViews.size(); i++) {
            mPageViews.get(i).setSelected(i == index);
        }

        loadPage(index, -1);
    }

    /** Asks the page's presenter to build its options, and draws them when they arrive. */
    private void loadPage(int index, int focusRow) {
        final int loadId = ++mLoadId;
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        presenter.setCapture((categories, title, onFinish) -> post(() -> {
            if (loadId != mLoadId || isFinishing()) {
                return; // another page was chosen meanwhile
            }

            if (onFinish != null) {
                mOnFinish.add(onFinish);
            }

            renderPage(index, categories, focusRow);
        }));

        try {
            mPages.get(index).onClick.run();
        } catch (RuntimeException e) {
            Log.e(TAG, "Page error: %s", e.getMessage());
            presenter.setCapture(null);
        }
    }

    private void reloadPage(int focusRow) {
        if (mCurrentPage >= 0) {
            loadPage(mCurrentPage, focusRow);
        }
    }

    private void renderPage(int index, List<OptionCategory> categories, int focusRow) {
        mContent.removeAllViews();
        mRows.clear();
        mCollapseOpenChoices = null;

        mPageIconRes = pageIcon(index);
        TextView heading = text(mPages.get(index).title, 26, TEXT, true);
        heading.setPadding(dp(20), 0, 0, dp(10));
        mContent.addView(heading);

        if (categories != null) {
            for (OptionCategory category : categories) {
                if (category == null || category.options == null) {
                    continue;
                }

                switch (category.type) {
                    case OptionCategory.TYPE_SINGLE_SWITCH:
                        for (OptionItem item : category.options) {
                            addSwitchRow(item);
                        }
                        break;
                    case OptionCategory.TYPE_CHECKBOX_LIST:
                        addLabel(category.title);
                        for (OptionItem item : category.options) {
                            addSwitchRow(item);
                        }
                        break;
                    case OptionCategory.TYPE_RADIO_LIST:
                        addChoiceRow(category);
                        break;
                    case OptionCategory.TYPE_SINGLE_BUTTON:
                        for (OptionItem item : category.options) {
                            addButtonRow(item);
                        }
                        break;
                    case OptionCategory.TYPE_STRING_LIST:
                        addLabel(category.title);
                        for (OptionItem item : category.options) {
                            addInfoRow(item);
                        }
                        break;
                    default:
                        break; // chat, comments, long text: not used in Settings
                }
            }
        }

        mScroll.scrollTo(0, 0);

        if (focusRow >= 0) {
            if (focusRow < mRows.size()) {
                mRows.get(focusRow).requestFocus();
            }
        }
    }

    // ---------------------------------------------------------------- rows

    private void addLabel(CharSequence label) {
        if (label == null || label.length() == 0) {
            return;
        }

        TextView view = text(label.toString(), 18, MUTED, false);
        view.setPadding(dp(20), dp(16), 0, dp(6));
        mContent.addView(view);
    }

    private void addSwitchRow(OptionItem item) {
        LinearLayout row = newRow(item.getTitle());
        row.addView(titleBlock(item.getTitle(), item.getDescription()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(getContext());
        toggle.setChecked(item.isSelected());
        toggle.setClickable(false);
        toggle.setFocusable(false);
        row.addView(toggle);

        row.setOnClickListener(v -> {
            boolean now = !toggle.isChecked();
            toggle.setChecked(now);
            item.onSelect(now);
        });

        addRow(row);
    }

    /**
     * A list where one choice is picked. The row shows the current choice; a click unfolds the choices right under
     * it, inside the page (no pop-up). Choosing one folds the list again.
     */
    private void addChoiceRow(OptionCategory category) {
        final List<OptionItem> options = category.options;
        int selected = -1;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).isSelected()) {
                selected = i;
                break;
            }
        }

        final LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout row = newRow(category.title);
        row.addView(titleBlock(category.title, null), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView value = text(selected >= 0 ? String.valueOf(options.get(selected).getTitle()) : "", 20, MUTED, false);
        value.setSingleLine(true);
        value.setMaxWidth(dp(280));
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(value);

        final TextView arrow = text("\u203A", 30, MUTED, true); // a chevron that turns down when unfolded
        arrow.setPadding(dp(12), 0, 0, dp(2));
        row.addView(arrow);
        wrapper.addView(row);

        final int rowIndex = mRows.size();
        final MaxHeightScrollView[] list = {null};

        row.setOnClickListener(v -> {
            if (list[0] != null) {
                if (mCollapseOpenChoices != null) {
                    mCollapseOpenChoices.run();
                }
                return;
            }

            if (mCollapseOpenChoices != null) {
                mCollapseOpenChoices.run(); // only one list is open at a time
            }

            final MaxHeightScrollView box = buildChoiceList(options, value, rowIndex);
            list[0] = box;
            wrapper.addView(box);
            arrow.setRotation(90f);

            mCollapseOpenChoices = () -> {
                final MaxHeightScrollView open = list[0];
                list[0] = null;
                mCollapseOpenChoices = null;
                arrow.setRotation(0f);
                if (open != null) {
                    wrapper.removeView(open);
                }
            };

            // Bring the unfolded list into view (long lists such as Language)
            mScroll.post(() -> mScroll.scrollTo(0, Math.max(0, wrapper.getTop() - dp(8))));
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        mContent.addView(wrapper, lp);
        mRows.add(row);
    }

    /** The unfolded choices: a radio dot and a name each. Long lists scroll inside their own box. */
    private MaxHeightScrollView buildChoiceList(List<OptionItem> options, TextView value, int rowIndex) {
        MaxHeightScrollView box = new MaxHeightScrollView(getContext(), dp(320));
        LinearLayout items = new LinearLayout(getContext());
        items.setOrientation(LinearLayout.VERTICAL);
        items.setPadding(dp(40), dp(2), 0, dp(8));
        box.addView(items, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final List<RadioButton> dots = new ArrayList<>();
        int checked = -1;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).isSelected()) {
                checked = i;
            }
        }

        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            final OptionItem option = options.get(i);

            LinearLayout choice = new LinearLayout(getContext());
            choice.setOrientation(LinearLayout.HORIZONTAL);
            choice.setGravity(Gravity.CENTER_VERTICAL);
            choice.setPadding(dp(16), dp(10), dp(16), dp(10));
            choice.setFocusable(true);
            choice.setClickable(true);
            choice.setBackground(rowBackground());

            RadioButton dot = new RadioButton(getContext());
            dot.setChecked(i == checked);
            dot.setClickable(false);
            dot.setFocusable(false);
            dots.add(dot);
            choice.addView(dot);

            TextView name = text(String.valueOf(option.getTitle()), 20, TEXT, false);
            name.setPadding(dp(12), 0, 0, 0);
            choice.addView(name);

            choice.setOnClickListener(v -> {
                for (int d = 0; d < dots.size(); d++) {
                    dots.get(d).setChecked(d == index);
                }
                value.setText(String.valueOf(option.getTitle()));
                option.onSelect(true); // only the chosen one is told (the old screen did the same)
                if (mCollapseOpenChoices != null) {
                    mCollapseOpenChoices.run();
                }
                mHandler.postDelayed(() -> {
                    if (!isFinishing()) {
                        reloadPage(rowIndex);
                    }
                }, 60);
            });

            items.addView(choice);
        }

        return box;
    }

    private void addButtonRow(OptionItem item) {
        LinearLayout row = newRow(item.getTitle());
        row.addView(titleBlock(item.getTitle(), item.getDescription()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final int rowIndex = mRows.size();
        row.setOnClickListener(v -> {
            item.onSelect(true);
            mHandler.postDelayed(() -> {
                if (!isFinishing()) {
                    reloadPage(rowIndex);
                }
            }, 120);
        });

        addRow(row);

        if (getString(R.string.younew_update_check).contentEquals(String.valueOf(item.getTitle()))) {
            addUpdateProgress();
        }
    }

    private void addInfoRow(OptionItem item) {
        TextView view = text(String.valueOf(item.getTitle()), 22, TEXT, false);
        view.setPadding(dp(20), dp(10), dp(20), dp(10));
        mContent.addView(view);
    }

    private LinearLayout newRow(CharSequence iconTitle) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(14), dp(20), dp(14));
        row.setMinimumHeight(dp(64));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(rowBackground());

        ImageView icon = icon(iconFor(iconTitle), 36);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        iconParams.setMarginEnd(dp(16));
        row.addView(icon, iconParams);
        return row;
    }

    private void addRow(View row) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        mContent.addView(row, lp);
        mRows.add(row);
    }

    private View titleBlock(CharSequence title, CharSequence description) {
        LinearLayout block = new LinearLayout(getContext());
        block.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = text(title != null ? title.toString() : "", 22, TEXT, false);
        block.addView(titleView);

        if (description != null && description.length() > 0) {
            TextView descView = text(description.toString(), 16, MUTED, false);
            descView.setPadding(0, dp(2), 0, 0);
            block.addView(descView);
        }

        return block;
    }

    // ---------------------------------------------------------------- update progress

    /**
     * Under "Check for updates now": a text line and a progress bar that follow AppUpdater (checking, downloading
     * with the percent, opening the installer, failed, up to date). Nothing shows while the updater is idle.
     */
    private void addUpdateProgress() {
        final LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(6), dp(20), dp(14));

        final TextView status = text("", 18, TEXT, false);
        box.addView(status);

        final FrameLayout track = new FrameLayout(getContext());
        track.setBackground(round(0xFF3A3A3A, 6));
        final View fill = new View(getContext());
        fill.setBackground(round(0xFF3EA6FF, 6));
        track.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12));
        trackParams.topMargin = dp(8);
        box.addView(track, trackParams);

        final Runnable refresh = () -> {
            int state = com.liskovsoft.smartyoutubetv2.common.misc.AppUpdater.getState();
            int percent = com.liskovsoft.smartyoutubetv2.common.misc.AppUpdater.getPercent();
            String version = com.liskovsoft.smartyoutubetv2.common.misc.AppUpdater.getVersion();
            boolean bar = false;
            String line;

            switch (state) {
                case com.liskovsoft.smartyoutubetv2.common.misc.AppUpdater.STATE_CHECKING:
                    line = getString(R.string.younew_update_p_checking);
                    break;
                case com.liskovsoft.smartyoutubetv2.common.misc.AppUpdater.STATE_DOWNLOADING:
                    bar = percent >= 0;
                    line = percent >= 0
                            ? getContext().getString(R.string.younew_update_p_downloading, version, percent)
                            : getString(R.string.younew_update_p_downloading_unknown);
                    break;
                case com.liskovsoft.smartyoutubetv2.common.misc.AppUpdater.STATE_INSTALLING:
                    bar = true;
                    percent = 100;
                    line = getString(R.string.younew_update_p_installing);
                    break;
                case com.liskovsoft.smartyoutubetv2.common.misc.AppUpdater.STATE_FAILED:
                    line = getString(R.string.younew_update_p_failed);
                    break;
                case com.liskovsoft.smartyoutubetv2.common.misc.AppUpdater.STATE_UP_TO_DATE:
                    line = getString(R.string.update_not_found);
                    break;
                default:
                    box.setVisibility(View.GONE);
                    return;
            }

            box.setVisibility(View.VISIBLE);
            status.setText(line);
            track.setVisibility(bar ? View.VISIBLE : View.GONE);

            if (bar) {
                final int shown = Math.max(0, Math.min(100, percent));
                track.post(() -> {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
                    lp.width = track.getWidth() * shown / 100;
                    fill.setLayoutParams(lp);
                });
            }
        };

        final com.liskovsoft.smartyoutubetv2.common.misc.AppUpdater.ProgressListener listener = refresh::run;
        box.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                com.liskovsoft.smartyoutubetv2.common.misc.AppUpdater.addProgressListener(listener);
                refresh.run();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                com.liskovsoft.smartyoutubetv2.common.misc.AppUpdater.removeProgressListener(listener);
            }
        });

        refresh.run();
        mContent.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ---------------------------------------------------------------- icons

    private static final String[] PAGE_ICONS =
            {"accounts", "general", "player", "language", "subtitles", "appearance", "about"};

    private int drawableId(String name) {
        return getResources().getIdentifier("younew_ic_" + name, "drawable", getContext().getPackageName());
    }

    /** The pages are always in this order (Accounts, General, Player, Language, Subtitles, Appearance, About). */
    private int pageIcon(int index) {
        return index >= 0 && index < PAGE_ICONS.length ? drawableId(PAGE_ICONS[index]) : 0;
    }

    /** A round, coloured icon (the pictures are already round). */
    private ImageView icon(int resId, int sizeDp) {
        ImageView view = new ImageView(getContext());
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        if (resId > 0) {
            try {
                view.setImageResource(resId);
            } catch (RuntimeException e) {
                // no icon: the space stays empty
            }
        }
        return view;
    }

    /**
     * An icon for a setting, chosen by words in its (English) name; any other setting gets the icon of its page.
     */
    private int iconFor(CharSequence title) {
        String name = title == null ? "" : englishName(title.toString()).toLowerCase(java.util.Locale.ROOT);
        String[][] words = {
                {"update", "sparkle"}, {"shorts", "shorts"}, {"watched", "history"}, {"history", "history"},
                {"upcoming", "live"}, {"live", "live"}, {"mix", "playlist"},
                {"playlist", "playlist"}, {"queue", "queue"}, {"boot", "home"},
                {"background", "queue"}, {"news", "news"}, {"music", "music"},
                {"subscription", "subscriptions"}, {"notification", "bell"},
                {"language", "language"}, {"country", "language"}, {"location", "language"},
                {"subtitle", "subtitles"}, {"sign", "accounts"}, {"account", "accounts"},
                {"scale", "grid"}, {"grid", "grid"}, {"quality", "hq"}, {"preset", "hq"},
                {"color", "appearance"}, {"thumbnail", "appearance"}, {"search", "search"}, {"block", "block"},
                {"speed", "speed"}, {"volume", "volume"}, {"audio", "volume"}, {"seek", "seek"},
                {"sleep", "moon"}, {"playback", "player"}, {"version", "about"}, {"clean", "sparkle"}
        };

        for (String[] pair : words) {
            if (name.contains(pair[0])) {
                int id = drawableId(pair[1]);
                if (id > 0) {
                    return id;
                }
            }
        }

        return mPageIconRes;
    }

    private static java.util.Map<String, String> sEnglishNames;
    private static String sEnglishNamesLocale;

    /**
     * The icons are chosen by English words, but the app may run in another language (Arabic): then every setting
     * got the icon of its page. This gives the English text of a setting's name: the app's texts are read once in
     * both languages and matched. An unknown name (or English itself) is returned as it is.
     */
    private String englishName(String title) {
        try {
            android.content.res.Configuration now = getResources().getConfiguration();
            String locale = String.valueOf(now.locale);

            if (now.locale == null || "en".equals(now.locale.getLanguage())) {
                return title;
            }

            if (sEnglishNames == null || !locale.equals(sEnglishNamesLocale)) {
                java.util.Map<String, String> names = new java.util.HashMap<>();
                android.content.res.Configuration english = new android.content.res.Configuration(now);
                english.setLocale(java.util.Locale.ENGLISH);
                android.content.res.Resources englishTexts = getContext().createConfigurationContext(english).getResources();
                android.content.res.Resources texts = getResources();

                for (java.lang.reflect.Field field : com.liskovsoft.smartyoutubetv2.common.R.string.class.getFields()) {
                    try {
                        int id = field.getInt(null);
                        String local = texts.getString(id);
                        if (!names.containsKey(local)) {
                            names.put(local, englishTexts.getString(id));
                        }
                    } catch (Exception e) {
                        // not a text: skip
                    }
                }

                sEnglishNames = names;
                sEnglishNamesLocale = locale;
            }

            String name = sEnglishNames.get(title);
            return name != null ? name : title;
        } catch (Exception e) {
            Log.e(TAG, "English name error: %s", e.getMessage());
            return title;
        }
    }

    /** A scroll box that stops growing at a maximum height (long lists scroll inside it). */
    private static class MaxHeightScrollView extends ScrollView {
        private final int mMaxHeight;

        MaxHeightScrollView(android.content.Context context, int maxHeight) {
            super(context);
            mMaxHeight = maxHeight;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(mMaxHeight, View.MeasureSpec.AT_MOST));
        }
    }

    // ---------------------------------------------------------------- looks

    /**
     * An arrow button that works with the mouse alone (a TV box with only a USB mouse has no Back key):
     * it goes back one step, and leaves Settings from the list of pages.
     */
    private TextView backArrow() {
        TextView back = text("\u2039", 36, TEXT, true);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(14), 0, dp(14), dp(4));
        back.setFocusable(true);
        back.setClickable(true);
        back.setBackground(rowBackground());
        back.setContentDescription(getString(R.string.younew_back));
        back.setOnClickListener(v -> onBackPressed());
        return back;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private StateListDrawable pageBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_selected}, round(PILL));
        states.addState(new int[]{android.R.attr.state_focused}, round(FOCUS));
        states.addState(new int[]{android.R.attr.state_hovered}, round(0xFF2A2A2A));
        states.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return states;
    }

    private StateListDrawable rowBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, round(PRESSED));
        states.addState(new int[]{android.R.attr.state_focused}, round(FOCUS));
        states.addState(new int[]{android.R.attr.state_hovered}, round(0xFF2A2A2A));
        states.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return states;
    }

    private GradientDrawable round(int color) {
        return round(color, 28);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
