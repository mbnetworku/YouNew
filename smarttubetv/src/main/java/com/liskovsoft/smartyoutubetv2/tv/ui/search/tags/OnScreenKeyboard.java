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
        /** What is in the search box now (it also changes without the keyboard: a suggestion, voice, a real keyboard). */
        String getText();
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
    // YouNew: compact keys that follow the screen (a phone gets 28 dp keys, a big TV up to 36 dp);
    // before they were always 44 dp and the keyboard filled most of a phone screen.
    private int KEY_DP;
    private int SIDE_DP;

    private final Listener mListener;
    private int mMode = MODE_EN;

    public OnScreenKeyboard(Context context, Listener listener) {
        super(context);
        mListener = listener;
        KEY_DP = keyDp(context);
        SIDE_DP = Math.round(KEY_DP * 2.7f);
        setOrientation(VERTICAL);
        build();
    }

    /**
     * The room the keyboard really has (the Home screen's search panel on an upright phone is narrower than the
     * screen: the sidebar takes a part). The keys shrink until all of them fit, down to 20 dp; with more room
     * they go back to their normal size. Before, the DELETE / CLEAR column was cut off there.
     */
    public void setMaxWidthPx(int maxWidthPx) {
        float density = getResources().getDisplayMetrics().density;
        // one row = 7 keys + a 10 dp gap + the side key (2.7 keys)
        int fitting = (int) ((maxWidthPx / density - 10) / 9.7f);
        int key = Math.max(20, Math.min(keyDp(getContext()), fitting));

        if (key != KEY_DP) {
            KEY_DP = key;
            SIDE_DP = Math.round(KEY_DP * 2.7f);
            build();
        }
    }

    /** Key size in dp: 7 % of the shorter screen side, between 28 and 36 dp. */
    static int keyDp(Context context) {
        android.util.DisplayMetrics m = context.getResources().getDisplayMetrics();
        float shortSideDp = Math.min(m.widthPixels, m.heightPixels) / m.density;
        return Math.max(28, Math.min(36, Math.round(shortSideDp * 0.07f)));
    }

    /** Width of the keyboard in pixels. */
    /** Moves the remote focus onto the first key (A). Used when the search bar is clicked or touched. */
    public boolean focusFirstKey() {
        View first = firstFocusable(this);
        return first != null && first.requestFocus();
    }

    private static View firstFocusable(View view) {
        if (view.isFocusable() && view != null && !(view instanceof OnScreenKeyboard)) {
            return view;
        }

        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = firstFocusable(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    public static int getWidthPx(Context context) {
        int key = keyDp(context);
        return dp(context, 7 * key + Math.round(key * 2.7f) + 16);
    }

    /** Height of the keyboard in pixels (5 rows of keys + the Space/Search row). */
    public static int getHeightPx(Context context) {
        return dp(context, 6 * keyDp(context) + 8);
    }

    private void build() {
        removeAllViews();
        String[][] rows = mMode == MODE_AR ? ROWS_AR : mMode == MODE_NUM ? ROWS_NUM : ROWS_EN;
        String[] side = {mMode == MODE_AR ? "حذف" : "DELETE", mMode == MODE_AR ? "مسح" : "CLEAR", mMode == MODE_NUM ? "ABC" : "&123", mMode == MODE_AR ? "ENG" : "عربي"};

        for (int i = 0; i < Math.max(rows.length, side.length); i++) {
            LinearLayout line = newLine();
            String[] row = i < rows.length ? rows[i] : new String[0];
            for (String key : row) {
                line.addView(newKey(mMode == MODE_EN ? key.toUpperCase(java.util.Locale.ROOT) : key, KEY_DP, v -> type(key)));
            }
            for (int pad = row.length; pad < 7; pad++) {
                line.addView(spacer(KEY_DP));
            }
            line.addView(spacer(10));
            if (i < side.length) {
                line.addView(newKey(side[i], SIDE_DP, sideAction(i)));
            }
            addView(line);
        }

        LinearLayout bottom = newLine();
        bottom.addView(spacer(KEY_DP));
        bottom.addView(newKey(mMode == MODE_AR ? "مسافة" : "SPACE", 2 * KEY_DP + 20, v -> type(" ")));
        bottom.addView(spacer(8));
        bottom.addView(newKey(mMode == MODE_AR ? "بحث" : "SEARCH", 2 * KEY_DP + 20, v -> mListener.onSearch(currentText())));
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

    // The keys always work on what the search box shows now. (Before, the keyboard kept its own copy of the text:
    // when the results screen opened with a search, or a suggestion or a voice search filled the box, the next key
    // threw that text away.)
    private String currentText() {
        String text = mListener.getText();
        return text != null ? text : "";
    }

    private void type(String s) {
        mListener.onTextChanged(currentText() + s);
    }

    private void backspace() {
        String text = currentText();
        if (text.length() > 0) {
            // one whole character, also when it is stored as two (emoji)
            mListener.onTextChanged(text.substring(0, text.offsetByCodePoints(text.length(), -1)));
        }
    }

    private void clear() {
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
        boolean small = KEY_DP < 26; // shrunk keys: smaller letters, so the side labels still fit
        key.setTextSize(label.length() > 1 ? (small ? 10 : 12) : (small ? 15 : 17));
        key.setTypeface(Typeface.DEFAULT_BOLD);
        key.setFocusable(true);
        key.setClickable(true);
        key.setOnClickListener(onClick);
        key.setBackground(keyBackground(context));
        // Dark text on the white box of a focused or pressed key (a finger presses without focusing)
        key.setTextColor(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_pressed}, {}},
                new int[]{Color.parseColor("#0F0F0F"), Color.parseColor("#0F0F0F"), Color.parseColor("#F1F1F1")}));
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
        focused.setCornerRadius(dp(context, 8));
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
