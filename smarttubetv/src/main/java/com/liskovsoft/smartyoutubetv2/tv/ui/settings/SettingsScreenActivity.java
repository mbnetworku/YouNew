package com.liskovsoft.smartyoutubetv2.tv.ui.settings;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * YouNew: one Settings screen. The pages are listed on the left and the options of the chosen page are shown
 * on the right at the same time, so a click or a touch on either side always works (before, the options were a
 * second transparent screen laid over the list: it swallowed the touches and closed on Left/Right).
 * <br>
 * Remote: Up/Down choose a page, Right goes into its options, Left goes back, Back leaves.
 * Mouse or touch: click a page on the left, click an option on the right.
 * <br>
 * Narrow screens (a phone held upright): one pane at a time. The list of pages first; a click slides the
 * options in, and Back (or the arrow at the top) slides back to the list. Wide screens (TV, a phone held
 * sideways): both panes together. The screen rebuilds itself when the phone is turned.
 * <br>
 * Choices open inside the page (no pop-up): a click on a choice row unfolds its list right under it.
 * Every page and every setting has an icon.
 * <br>
 * Animations: the options of a page glide in one after another, rows grow a little under the remote focus or the
 * mouse and shrink a little while pressed, and the panes slide in a narrow screen.
 * <br>
 * The options come from the same presenters as before (they build option lists for AppDialogPresenter);
 * this screen draws those lists itself. See {@link AppDialogPresenter#setCapture}.
 */
public class SettingsScreenActivity extends MotherActivity {
    public static final String EXTRA_PAGE = "page";
    private static final String TAG = SettingsScreenActivity.class.getSimpleName();
    private static final int BG = 0xFF0F0F0F;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int PILL = 0xFFF1F1F1;
    private static final int FOCUS = 0xFF3A3A3A;
    private static final int PRESSED = 0xFF4A4A4A;
    private static final long FOCUS_DELAY_MS = 150;
    private static final float TWO_PANE_MIN_WIDTH_DP = 640f;
    private static final float FOCUS_SCALE = 1.02f;
    private static final float PRESS_SCALE = 0.97f;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<View> mPageViews = new ArrayList<>();
    private final List<View> mRows = new ArrayList<>();
    private final List<Runnable> mOnFinish = new ArrayList<>();
    private List<SettingsItem> mPages = new ArrayList<>();
    private LinearLayout mContent;
    private ScrollView mScroll;
    private int mPageIconRes;
    private Runnable mCollapseOpenChoices;
    private LinearLayout mLeftPanel;
    private boolean mTwoPane;
    private boolean mShowingOptions;
    private int mCurrentPage = -1;
    private int mLoadId;
    private Runnable mPendingPage;

    @Override
    protected void initTheme() {
        setTheme(R.style.App_Theme_SettingsScreen);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPages = AppDataSourceManager.instance().getSettingItems(this);

        setContentView(buildScreen());

        boolean pageGiven = getIntent().hasExtra(EXTRA_PAGE);
        int start = getIntent().getIntExtra(EXTRA_PAGE, 0);
        start = Math.max(0, Math.min(start, mPages.size() - 1));

        if (mTwoPane || pageGiven) {
            showPage(start);
            showOptions(true, false);
        }

        if (start < mPageViews.size()) {
            mPageViews.get(start).requestFocus();
        }
    }

    /** The phone was turned: the width changed, so choose the layout again. */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        int page = mCurrentPage;
        boolean showing = mShowingOptions;
        mCurrentPage = -1;
        mPageViews.clear();
        mRows.clear();
        ++mLoadId; // drop a page that was still loading

        setContentView(buildScreen());

        if (page >= 0) {
            showPage(page);
            showOptions(showing || mTwoPane, false);
        }
    }

    @Override
    public void onBackPressed() {
        if (!mTwoPane && mShowingOptions) {
            showOptions(false, true);
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        AppDialogPresenter.instance(this).setCapture(null);
        super.onDestroy();
    }

    @Override
    public void finish() {
        runFinishCallbacks();
        super.finish();
    }

    /** Some pages ask for a restart or a refresh when you leave them. */
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

    // ---------------------------------------------------------------- screen

    private View buildScreen() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        mTwoPane = metrics.widthPixels / metrics.density >= TWO_PANE_MIN_WIDTH_DP;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);

        // Left: title and the list of pages
        LinearLayout left = new LinearLayout(this);
        mLeftPanel = left;
        left.setOrientation(LinearLayout.VERTICAL);
        if (mTwoPane) {
            left.setPadding(dp(48), dp(40), dp(24), dp(24));
            root.addView(left, new LinearLayout.LayoutParams(dp(340), ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            left.setPadding(dp(20), dp(32), dp(20), dp(24));
            root.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }

        LinearLayout titleRow = new LinearLayout(this);
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
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(20), 0, dp(20), 0);
            item.setFocusable(true);
            item.setClickable(true);
            item.setBackground(pageBackground());

            ColorStateList onPill = new ColorStateList(
                    new int[][]{{android.R.attr.state_selected}, {}}, new int[]{Color.BLACK, TEXT});
            ImageView icon = icon(mPages.get(i).imageResId, 26);
            icon.setImageTintList(onPill);
            item.addView(icon);

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
        }

        // Right: the options of the chosen page
        mScroll = new ScrollView(this);
        mScroll.setFillViewport(true);
        mScroll.setClipToPadding(false);
        mContent = new LinearLayout(this);
        mContent.setOrientation(LinearLayout.VERTICAL);
        if (mTwoPane) {
            mContent.setPadding(dp(24), dp(40), dp(64), dp(48));
        } else {
            mContent.setPadding(dp(16), dp(24), dp(16), dp(32));
        }
        mScroll.addView(mContent, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(mScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        if (!mTwoPane) { // one pane at a time
            mScroll.setVisibility(mShowingOptions ? View.VISIBLE : View.GONE);
            left.setVisibility(mShowingOptions ? View.GONE : View.VISIBLE);
        }

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
            child.setTranslationX(dp(28));
            child.animate().alpha(1f).translationX(0f)
                    .setStartDelay(Math.min(i, 10) * 35L).setDuration(220).start();
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

        mCurrentPage = index;

        for (int i = 0; i < mPageViews.size(); i++) {
            mPageViews.get(i).setSelected(i == index);
        }

        loadPage(index, -1);
    }

    /** Asks the page's presenter to build its options, and draws them when they arrive. */
    private void loadPage(int index, int focusRow) {
        final int loadId = ++mLoadId;
        AppDialogPresenter presenter = AppDialogPresenter.instance(this);

        presenter.setCapture((categories, title, onFinish) -> runOnUiThread(() -> {
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

        LinearLayout headingRow = new LinearLayout(this);
        headingRow.setOrientation(LinearLayout.HORIZONTAL);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        headingRow.setPadding(0, 0, 0, dp(12));

        if (!mTwoPane) { // narrow screen: an arrow back to the list of pages
            headingRow.addView(backArrow());
        }

        mPageIconRes = mPages.get(index).imageResId;
        ImageView headingIcon = icon(mPageIconRes, 32);
        LinearLayout.LayoutParams headingIconParams = new LinearLayout.LayoutParams(dp(32), dp(32));
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

        Switch toggle = new Switch(this);
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

        final LinearLayout wrapper = new LinearLayout(this);
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

            list[0] = buildChoiceList(options, value, rowIndex);
            wrapper.addView(list[0]);
            list[0].setAlpha(0f);
            list[0].setTranslationY(-dp(8));
            list[0].animate().alpha(1f).translationY(0f).setDuration(180).start();
            arrow.animate().rotation(90f).setDuration(180).start();

            mCollapseOpenChoices = () -> {
                final MaxHeightScrollView open = list[0];
                list[0] = null;
                mCollapseOpenChoices = null;
                arrow.animate().rotation(0f).setDuration(180).start();
                if (open != null) {
                    open.animate().alpha(0f).setDuration(140).withEndAction(() -> wrapper.removeView(open)).start();
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

    /** The unfolded choices: a radio dot and a name each. Long lists scroll inside their own box. */
    private MaxHeightScrollView buildChoiceList(List<OptionItem> options, TextView value, int rowIndex) {
        MaxHeightScrollView box = new MaxHeightScrollView(this, dp(320));
        LinearLayout items = new LinearLayout(this);
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

            LinearLayout choice = new LinearLayout(this);
            choice.setOrientation(LinearLayout.HORIZONTAL);
            choice.setGravity(Gravity.CENTER_VERTICAL);
            choice.setPadding(dp(16), dp(10), dp(16), dp(10));
            choice.setFocusable(true);
            choice.setClickable(true);
            choice.setBackground(rowBackground());
            choice.setOnFocusChangeListener((v, hasFocus) -> grow(v, hasFocus));
            addTouchFeedback(choice);

            RadioButton dot = new RadioButton(this);
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
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(14), dp(20), dp(14));
        row.setMinimumHeight(dp(64));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(rowBackground());
        row.setOnFocusChangeListener((v, hasFocus) -> grow(v, hasFocus));
        addTouchFeedback(row);

        ImageView icon = icon(iconFor(iconTitle), 24);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
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
        LinearLayout block = new LinearLayout(this);
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

    private ImageView icon(int resId, int sizeDp) {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImageTintList(ColorStateList.valueOf(TEXT));
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
     * The icons are the ones the app already has (white pictures).
     */
    private int iconFor(CharSequence title) {
        String name = title == null ? "" : title.toString().toLowerCase();
        String[][] words = {
                {"shorts", "icon_shorts"}, {"watched", "icon_history"}, {"history", "icon_history"},
                {"upcoming", "icon_live"}, {"live", "icon_live"}, {"mix", "icon_playlist"},
                {"playlist", "icon_playlist"}, {"queue", "icon_queue"}, {"boot", "icon_home"},
                {"background", "icon_queue"}, {"news", "icon_news"}, {"music", "icon_music"},
                {"subscription", "icon_subscriptions"}, {"notification", "icon_notification"},
                {"language", "settings_language"}, {"country", "settings_language"}, {"location", "settings_language"},
                {"subtitle", "settings_subtitles"}, {"sign", "settings_account"}, {"account", "settings_account"},
                {"scale", "settings_ui_scale"}, {"grid", "settings_ui_scale"}, {"quality", "settings_hq"},
                {"preset", "settings_hq"}, {"color", "settings_main_ui"}, {"thumbnail", "settings_main_ui"},
                {"search", "settings_search"}, {"block", "settings_block"},
                {"speed", "settings_player"}, {"volume", "settings_player"}, {"audio", "settings_player"},
                {"seek", "settings_player"}, {"sleep", "settings_player"}, {"playback", "settings_player"},
                {"version", "settings_about"}, {"clean", "icon_trending"}
        };

        for (String[] pair : words) {
            if (name.contains(pair[0])) {
                int id = getResources().getIdentifier(pair[1], "drawable", getPackageName());
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
        back.setOnFocusChangeListener((v, hasFocus) -> grow(v, hasFocus));
        addTouchFeedback(back);
        return back;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
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
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(28));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
