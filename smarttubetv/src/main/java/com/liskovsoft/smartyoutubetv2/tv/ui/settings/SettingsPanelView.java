package com.liskovsoft.smartyoutubetv2.tv.ui.settings;

import android.animation.ValueAnimator;
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
import android.view.MotionEvent;
import android.view.animation.OvershootInterpolator;
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
 * YouNew: the Settings panel. The pages are listed on the left and the options of the chosen page are shown
 * on the right at the same time, so a click or a touch on either side always works.
 * <br>
 * It is a plain view, so it can live inside the Home screen (next to the sidebar, see SettingsPanelFragment)
 * or fill a screen of its own (SettingsScreenActivity).
 * <br>
 * Remote: Up/Down choose a page, Right goes into its options, Left goes back. Mouse or touch: click a page on
 * the left, click an option on the right.
 * <br>
 * Narrow places (a phone held upright): one pane at a time. The list of pages first; a click slides the
 * options in, and the arrow at the top slides back. Wide places (TV, a phone held sideways): both panes together.
 * The panel rebuilds itself when its width changes.
 * <br>
 * Choices open inside the page (no pop-up): a click on a choice row unfolds its list right under it.
 * Everything is rounded: round coloured icons, a rounded card behind the options, rounded rows and a rounded
 * highlight for the chosen page.
 * <br>
 * Animations: a page's options glide in one after another (from below or above, by the direction moved in the
 * list); pages slide in; rows grow a little under the remote focus or the mouse and shrink while pressed; the
 * icon of a switch pops when flipped; unfolded lists open smoothly; a changed value fades to its new text.
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
    private static final float TWO_PANE_MIN_WIDTH_DP = 600f;
    private static final float FOCUS_SCALE = 1.02f;
    private static final float PRESS_SCALE = 0.97f;

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
    private boolean mSlideFromBelow = true;
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
    private final Runnable mSettle = this::settle;

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
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = widthPx > 0 ? widthPx : metrics.widthPixels;
        return width / metrics.density >= TWO_PANE_MIN_WIDTH_DP;
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

    /**
     * Safety net: after the entrance animations should be over, every item is put in its final place and made fully
     * visible. An animation that was cut short (the panel was detached or hidden while it ran, as the Home screen
     * does when it changes sections) can no longer leave the panel invisible.
     */
    private void settle() {
        settleChildren(mLeftList);
        settleChildren(mContent);
    }

    private void settleChildren(ViewGroup group) {
        if (group == null) {
            return;
        }

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.animate().cancel();
            child.setAlpha(1f);
            child.setTranslationX(0f);
            child.setTranslationY(0f);
        }
    }

    private void scheduleSettle() {
        mHandler.removeCallbacks(mSettle);
        mHandler.postDelayed(mSettle, 1500);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scheduleSettle();
    }

    @Override
    protected void onDetachedFromWindow() {
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
        mTwoPane = wantTwoPane(getWidth());

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.HORIZONTAL);

        // Left: title and the list of pages
        // The list of pages sits in a scroll box: on a phone held sideways the screen is shorter than the list
        final ScrollView leftScroll = new ScrollView(getContext());
        leftScroll.setClipToPadding(false);
        mLeftPanel = leftScroll;
        LinearLayout left = new LinearLayout(getContext());
        mLeftList = left;
        left.setOrientation(LinearLayout.VERTICAL);
        leftScroll.addView(left, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (mTwoPane) {
            left.setPadding(dp(32), dp(24), dp(16), dp(24));
            root.addView(leftScroll, new LinearLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            left.setPadding(dp(16), dp(24), dp(16), dp(24));
            root.addView(leftScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }

        LinearLayout titleRow = new LinearLayout(getContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(0, 0, 0, dp(16));
        titleRow.addView(backArrow());
        TextView title = text(getString(R.string.header_settings), 30, TEXT, true);
        title.setPadding(dp(8), 0, 0, 0);
        titleRow.addView(title);
        left.addView(titleRow);

        for (int i = 0; i < mPages.size(); i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(20), 0, dp(20), 0);
            item.setFocusable(true);
            item.setClickable(true);
            item.setBackground(pageBackground());

            ColorStateList onPill = new ColorStateList(
                    new int[][]{{android.R.attr.state_selected}, {}}, new int[]{Color.BLACK, TEXT});
            item.addView(icon(pageIcon(i), 34));

            TextView label = text(mPages.get(i).title, 22, TEXT, false);
            label.setSingleLine(true);
            label.setTextColor(onPill);
            label.setPadding(dp(16), 0, 0, 0);
            item.addView(label);
            item.setOnClickListener(v -> {
                showPage(index);
                showOptions(true, true); // narrow screen: slide the options in (nothing happens on a wide one)
            });
            // The remote moves the focus: show that page after a short moment (not while scrolling fast).
            // A narrow screen waits for the click instead (the options cover the list).
            item.setOnFocusChangeListener((v, hasFocus) -> {
                grow(v, hasFocus);
                if (hasFocus && mTwoPane) {
                    showPageDelayed(index);
                }
            });
            addTouchFeedback(item);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
            lp.bottomMargin = dp(4);
            left.addView(item, lp);
            mPageViews.add(item);

            // The pages slide in one after another
            item.setAlpha(0f);
            item.setTranslationX(-dp(24));
            item.animate().alpha(1f).translationX(0f).setStartDelay(80L + i * 45L).setDuration(240).start();
        }

        // Right: the options of the chosen page
        mScroll = new ScrollView(getContext());
        mScroll.setFillViewport(true);
        mScroll.setClipToPadding(false);
        mContent = new LinearLayout(getContext());
        mContent.setOrientation(LinearLayout.VERTICAL);
        if (mTwoPane) {
            mContent.setPadding(dp(24), dp(40), dp(64), dp(48));
        } else {
            mContent.setPadding(dp(16), dp(24), dp(16), dp(32));
        }
        mScroll.addView(mContent, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mScroll.setBackground(round(CARD, 32));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        cardParams.setMargins(dp(mTwoPane ? 8 : 0), dp(8), dp(mTwoPane ? 24 : 0), dp(16));
        root.addView(mScroll, cardParams);

        if (!mTwoPane) { // one pane at a time
            mScroll.setVisibility(mShowingOptions ? View.VISIBLE : View.GONE);
            leftScroll.setVisibility(mShowingOptions ? View.GONE : View.VISIBLE);
        }

        scheduleSettle();

        return root;
    }

    // ---------------------------------------------------------------- pages

    /** Narrow screen only: slides between the list of pages and the options of the chosen page. */
    private void showOptions(boolean show, boolean animate) {
        if (mTwoPane) {
            return;
        }

        mShowingOptions = show;
        final View hide = show ? mLeftPanel : mScroll;
        final View reveal = show ? mScroll : mLeftPanel;

        if (!animate) {
            hide.setVisibility(View.GONE);
            reveal.setVisibility(View.VISIBLE);
            return;
        }

        float shift = dp(72);
        reveal.setVisibility(View.VISIBLE);
        reveal.setAlpha(0f);
        reveal.setTranslationX(show ? shift : -shift);
        reveal.animate().alpha(1f).translationX(0f).setStartDelay(0).setDuration(240).start();

        hide.animate().alpha(0f).translationX(show ? -shift : shift).setStartDelay(0).setDuration(180)
                .withEndAction(() -> {
                    hide.setVisibility(View.GONE);
                    hide.setAlpha(1f);
                    hide.setTranslationX(0f);
                }).start();

        if (!show && mCurrentPage >= 0 && mCurrentPage < mPageViews.size()) {
            mPageViews.get(mCurrentPage).requestFocus();
        }
    }

    /** A quick "pop" (bigger, then back with a little overshoot): shows that a switch was flipped or a choice made. */
    private void pop(View view) {
        view.animate().cancel();
        view.setScaleX(0.6f);
        view.setScaleY(0.6f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start();
    }

    /** A value fades out and back in with its new text. */
    private void fadeText(TextView view, String text) {
        view.animate().cancel();
        view.animate().alpha(0f).setDuration(90).withEndAction(() -> {
            view.setText(text);
            view.animate().alpha(1f).setDuration(140).start();
        }).start();
    }

    /** A view grows a little while it has the remote focus or the mouse over it. */
    private void grow(View view, boolean on) {
        float scale = on ? FOCUS_SCALE : 1f;
        view.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
    }

    /** Pressed: shrink a little; released: back (to the focus size if it still has the focus). */
    private void addTouchFeedback(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float scale = v.isFocused() ? FOCUS_SCALE : 1f;
                    v.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
                    break;
                default:
                    break;
            }
            return false; // the click still works
        });

        view.setOnHoverListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                grow(v, true);
            } else if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT && !v.isFocused()) {
                grow(v, false);
            }
            return false;
        });
    }

    /** The options of a page glide in one after another. */
    private void animateContentIn() {
        int count = mContent.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = mContent.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationX(dp(20));
            child.setTranslationY(mSlideFromBelow ? dp(16) : -dp(16));
            child.animate().alpha(1f).translationX(0f).translationY(0f)
                    .setStartDelay(Math.min(i, 10) * 35L).setDuration(240).start();
        }
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

        mSlideFromBelow = index >= mCurrentPage; // moving down the list: the options come up from below
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

        LinearLayout headingRow = new LinearLayout(getContext());
        headingRow.setOrientation(LinearLayout.HORIZONTAL);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        headingRow.setPadding(0, 0, 0, dp(12));

        if (!mTwoPane) { // narrow screen: an arrow back to the list of pages
            headingRow.addView(backArrow());
        }

        mPageIconRes = pageIcon(index);
        ImageView headingIcon = icon(mPageIconRes, 40);
        LinearLayout.LayoutParams headingIconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        headingIconParams.setMarginStart(dp(mTwoPane ? 20 : 8));
        headingRow.addView(headingIcon, headingIconParams);

        TextView heading = text(mPages.get(index).title, 30, TEXT, true);
        heading.setPadding(dp(14), 0, 0, 0);
        headingRow.addView(heading);
        mContent.addView(headingRow);

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
        scheduleSettle();

        if (focusRow >= 0) {
            if (focusRow < mRows.size()) {
                mRows.get(focusRow).requestFocus();
            }
        } else {
            animateContentIn(); // a refresh after a click does not replay it
            if (!mTwoPane && !mRows.isEmpty()) {
                mRows.get(0).requestFocus();
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
            pop(row.getChildAt(0)); // the icon pops
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
            unfold(box, true, null);
            arrow.animate().rotation(90f).setDuration(200).start();

            mCollapseOpenChoices = () -> {
                final MaxHeightScrollView open = list[0];
                list[0] = null;
                mCollapseOpenChoices = null;
                arrow.animate().rotation(0f).setDuration(200).start();
                if (open != null) {
                    unfold(open, false, () -> wrapper.removeView(open));
                }
            };

            // Bring the unfolded list into view (long lists such as Language)
            mScroll.post(() -> mScroll.smoothScrollTo(0, Math.max(0, wrapper.getTop() - dp(8))));
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        mContent.addView(wrapper, lp);
        mRows.add(row);
    }

    /** Opens (or closes) a list smoothly: its height grows from nothing (or shrinks to nothing) while it fades. */
    private void unfold(final View box, final boolean open, final Runnable onEnd) {
        box.measure(View.MeasureSpec.makeMeasureSpec(mContent.getWidth() > 0 ? mContent.getWidth() : dp(600), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int full = Math.max(1, box.getMeasuredHeight());
        final int from = open ? 0 : box.getHeight() > 0 ? box.getHeight() : full;
        final int to = open ? full : 0;

        final ViewGroup.LayoutParams params = box.getLayoutParams();
        params.height = from;
        box.setLayoutParams(params);

        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(open ? 240 : 180);
        animator.addUpdateListener(a -> {
            params.height = (int) a.getAnimatedValue();
            box.setLayoutParams(params);
            box.setAlpha(open ? a.getAnimatedFraction() : 1f - a.getAnimatedFraction());
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (open) {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    box.setLayoutParams(params);
                    box.setAlpha(1f);
                }
                if (onEnd != null) {
                    onEnd.run();
                }
            }
        });
        animator.start();
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
            choice.setOnFocusChangeListener((v, hasFocus) -> grow(v, hasFocus));
            addTouchFeedback(choice);

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
                fadeText(value, String.valueOf(option.getTitle()));
                option.onSelect(true); // only the chosen one is told (the old screen did the same)
                if (mCollapseOpenChoices != null) {
                    mCollapseOpenChoices.run();
                }
                mHandler.postDelayed(() -> {
                    if (!isFinishing()) {
                        reloadPage(rowIndex);
                    }
                }, 260);
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
            }, 400);
        });

        addRow(row);
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
        row.setOnFocusChangeListener((v, hasFocus) -> grow(v, hasFocus));
        addTouchFeedback(row);

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
        String name = title == null ? "" : title.toString().toLowerCase();
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

    /** A scroll box that stops growing at a maximum height (long lists scroll inside it). */
    private static class MaxHeightScrollView extends ScrollView {
        private final int mMaxHeight;

        MaxHeightScrollView(android.content.Context context, int maxHeight) {
            super(context);
            mMaxHeight = maxHeight;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec); // being animated to a given height
            } else {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(mMaxHeight, View.MeasureSpec.AT_MOST));
            }
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
        back.setOnFocusChangeListener((v, hasFocus) -> grow(v, hasFocus));
        addTouchFeedback(back);
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
