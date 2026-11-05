package com.liskovsoft.smartyoutubetv2.tv.ui.settings;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<TextView> mPageViews = new ArrayList<>();
    private final List<View> mRows = new ArrayList<>();
    private final List<Runnable> mOnFinish = new ArrayList<>();
    private List<SettingsItem> mPages = new ArrayList<>();
    private LinearLayout mContent;
    private ScrollView mScroll;
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

        int start = getIntent().getIntExtra(EXTRA_PAGE, 0);
        start = Math.max(0, Math.min(start, mPages.size() - 1));
        showPage(start);

        if (start < mPageViews.size()) {
            mPageViews.get(start).requestFocus();
        }
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);

        // Left: title and the list of pages
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(48), dp(40), dp(24), dp(24));
        root.addView(left, new LinearLayout.LayoutParams(dp(360), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = text(getString(R.string.header_settings), 30, TEXT, true);
        title.setPadding(dp(20), 0, 0, dp(20));
        left.addView(title);

        for (int i = 0; i < mPages.size(); i++) {
            final int index = i;
            TextView item = text(mPages.get(i).title, 22, TEXT, false);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(20), 0, dp(20), 0);
            item.setSingleLine(true);
            item.setFocusable(true);
            item.setClickable(true);
            item.setBackground(pageBackground());
            item.setTextColor(new ColorStateList(
                    new int[][]{{android.R.attr.state_selected}, {}}, new int[]{Color.BLACK, TEXT}));
            item.setOnClickListener(v -> showPage(index));
            // The remote moves the focus: show that page after a short moment (not while scrolling fast)
            item.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    showPageDelayed(index);
                }
            });
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
        mContent.setPadding(dp(24), dp(40), dp(64), dp(48));
        mScroll.addView(mContent, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(mScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        return root;
    }

    // ---------------------------------------------------------------- pages

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

        TextView heading = text(mPages.get(index).title, 30, TEXT, true);
        heading.setPadding(dp(20), 0, 0, dp(16));
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

        if (focusRow >= 0 && focusRow < mRows.size()) {
            mRows.get(focusRow).requestFocus();
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
        LinearLayout row = newRow();
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

    /** A list where one choice is picked: the row shows the current choice, a click opens the choices. */
    private void addChoiceRow(OptionCategory category) {
        final List<OptionItem> options = category.options;
        int selected = -1;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).isSelected()) {
                selected = i;
                break;
            }
        }

        LinearLayout row = newRow();
        row.addView(titleBlock(category.title, null), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = text(selected >= 0 ? options.get(selected).getTitle().toString() : "", 20, MUTED, false);
        value.setSingleLine(true);
        value.setMaxWidth(dp(360));
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(value);

        final int rowIndex = mRows.size();
        row.setOnClickListener(v -> showChoices(category, rowIndex));

        addRow(row);
    }

    private void showChoices(OptionCategory category, int rowIndex) {
        final List<OptionItem> options = category.options;
        CharSequence[] names = new CharSequence[options.size()];
        int checked = -1;
        for (int i = 0; i < options.size(); i++) {
            names[i] = options.get(i).getTitle();
            if (options.get(i).isSelected()) {
                checked = i;
            }
        }

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(category.title)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    dialog.dismiss();
                    options.get(which).onSelect(true); // only the chosen one is told (the old screen did the same)
                    reloadPage(rowIndex);
                })
                .show();
    }

    private void addButtonRow(OptionItem item) {
        LinearLayout row = newRow();
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

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(14), dp(20), dp(14));
        row.setMinimumHeight(dp(64));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(rowBackground());
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

    // ---------------------------------------------------------------- looks

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
