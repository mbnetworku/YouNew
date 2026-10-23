package com.liskovsoft.smartyoutubetv2.tv.ui.search.tags;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * YouNew: YouTube TV style on-screen keyboard for the Search screen.
 * Plain letters in 7 columns, Delete / Clear / &123 / Arabic down the right side, Space and Search below.
 * Keys have no tile; the focused key turns into a white rounded box. Works with the remote and a mouse.
 */
public class OnScreenKeyboard extends LinearLayout {
    /** What the keyboard asks the Search screen to do. */
    public interface Listener {
        void onTextChanged(String text);
        void onSearch(String text);
    }

    private static final int MODE_EN = 0;
    private static final int MODE_AR = 1;
    private static final int MODE_NUM = 2;

    private static final String[][] ROWS_EN = {
            {"a", "b", "c", "d", "e", "f", "g"},
            {"h", "i", "j", "k", "l", "m", "n"},
            {"o", "p", "q", "r", "s", "t", "u"},
            {"v", "w", "x", "y", "z", "-", "'"}
    };
    private static final String[][] ROWS_AR = {
            {"ا", "ب", "ت", "ث", "ج", "ح", "خ"},
            {"د", "ذ", "ر", "ز", "س", "ش", "ص"},
            {"ض", "ط", "ظ", "ع", "غ", "ف", "ق"},
            {"ك", "ل", "م", "ن", "ه", "و", "ي"},
            {"ة", "ء", "أ", "إ", "آ", "ى", "ئ"}
    };
    private static final String[][] ROWS_NUM = {
            {"1", "2", "3", "4", "5", "6", "7"},
            {"8", "9", "0", ".", "@", "_", "&"},
            {"#", "!", "?", "+", "/", ":", "ؤ"}
    };
    private static final int KEY_DP = 44;
    private static final int SIDE_DP = 110;

    private final Listener mListener;
    private final StringBuilder mText = new StringBuilder();
    private int mMode = MODE_EN;

    public OnScreenKeyboard(Context context, Listener listener) {
        super(context);
        mListener = listener;
        setOrientation(VERTICAL);
        build();
    }

    /** Width of the keyboard in pixels. */
    public static int getWidthPx(Context context) {
        return dp(context, 7 * KEY_DP + SIDE_DP + 16);
    }

    /** Height of the keyboard in pixels (5 rows of keys + the Space/Search row). */
    public static int getHeightPx(Context context) {
        return dp(context, 6 * KEY_DP + 8);
    }

    private void build() {
        removeAllViews();
        String[][] rows = mMode == MODE_AR ? ROWS_AR : mMode == MODE_NUM ? ROWS_NUM : ROWS_EN;
        String[] side = {mMode == MODE_AR ? "حذف" : "DELETE", mMode == MODE_AR ? "مسح" : "CLEAR", mMode == MODE_NUM ? "ABC" : "&123", mMode == MODE_AR ? "ENG" : "عربي"};

        for (int i = 0; i < Math.max(rows.length, side.length); i++) {
            LinearLayout line = newLine();
            String[] row = i < rows.length ? rows[i] : new String[0];
            for (String key : row) {
                line.addView(newKey(mMode == MODE_EN ? key.toUpperCase() : key, KEY_DP, v -> type(key)));
            }
            for (int pad = row.length; pad < 7; pad++) {
                line.addView(spacer(KEY_DP));
            }
            line.addView(spacer(16));
            if (i < side.length) {
                line.addView(newKey(side[i], SIDE_DP, sideAction(i)));
            }
            addView(line);
        }

        LinearLayout bottom = newLine();
        bottom.addView(spacer(KEY_DP));
        bottom.addView(newKey(mMode == MODE_AR ? "مسافة" : "SPACE", 2 * KEY_DP + 20, v -> type(" ")));
        bottom.addView(spacer(8));
        bottom.addView(newKey(mMode == MODE_AR ? "بحث" : "SEARCH", 2 * KEY_DP + 20, v -> mListener.onSearch(mText.toString())));
        addView(bottom);
    }

    private OnClickListener sideAction(int index) {
        switch (index) {
            case 0: return v -> backspace();
            case 1: return v -> clear();
            case 2: return v -> switchMode(mMode == MODE_NUM ? MODE_EN : MODE_NUM, 2);
            default: return v -> switchMode(mMode == MODE_AR ? MODE_EN : MODE_AR, 3);
        }
    }

    // Changes the letters. Focus goes back to the same side key so it can be pressed again.
    private void switchMode(int mode, int sideIndex) {
        mMode = mode;
        build();
        LinearLayout line = (LinearLayout) getChildAt(sideIndex);
        line.getChildAt(line.getChildCount() - 1).requestFocus();
    }

    private void type(String s) {
        mText.append(s);
        mListener.onTextChanged(mText.toString());
    }

    private void backspace() {
        if (mText.length() > 0) {
            mText.setLength(mText.length() - 1);
            mListener.onTextChanged(mText.toString());
        }
    }

    private void clear() {
        mText.setLength(0);
        mListener.onTextChanged("");
    }

    private LinearLayout newLine() {
        LinearLayout line = new LinearLayout(getContext());
        line.setOrientation(HORIZONTAL);
        return line;
    }

    private View spacer(int widthDp) {
        View space = new View(getContext());
        space.setLayoutParams(new LayoutParams(dp(getContext(), widthDp), dp(getContext(), KEY_DP)));
        return space;
    }

    /** One key: just the label; when focused, a white rounded box with dark text. */
    private TextView newKey(String label, int widthDp, OnClickListener onClick) {
        Context context = getContext();
        TextView key = new TextView(context);
        key.setText(label);
        key.setGravity(Gravity.CENTER);
        key.setTextSize(label.length() > 1 ? 17 : 22);
        key.setTypeface(Typeface.DEFAULT_BOLD);
        key.setFocusable(true);
        key.setClickable(true);
        key.setOnClickListener(onClick);
        key.setBackground(keyBackground(context));
        key.setTextColor(Color.parseColor("#F1F1F1"));
        key.setOnFocusChangeListener((v, focused) ->
                ((TextView) v).setTextColor(focused ? Color.parseColor("#0F0F0F") : Color.parseColor("#F1F1F1")));
        // Mouse: hovering a key focuses it, so the highlight follows the pointer
        key.setOnHoverListener((v, event) -> {
            v.requestFocus();
            return false;
        });
        key.setLayoutParams(new LayoutParams(dp(context, widthDp), dp(context, KEY_DP)));
        return key;
    }

    private static StateListDrawable keyBackground(Context context) {
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.parseColor("#F1F1F1"));
        focused.setCornerRadius(dp(context, 6));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return states;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}
