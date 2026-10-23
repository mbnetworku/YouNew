package com.liskovsoft.smartyoutubetv2.tv.ui.search.tags;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * YouNew: the YouTube TV style on-screen keyboard for the Search screen.
 * A plain grid of focusable keys (0-9, a-z, a few symbols) plus Backspace, Space, Clear and Search.
 * Works with the remote (D-pad) and with a mouse click. Keys never open the system keyboard.
 */
public class OnScreenKeyboard extends LinearLayout {
    /** What the keyboard asks the Search screen to do. */
    public interface Listener {
        void onTextChanged(String text);
        void onSearch(String text);
    }

    private static final String[][] ROWS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j"},
            {"k", "l", "m", "n", "o", "p", "q", "r", "s", "t"},
            {"u", "v", "w", "x", "y", "z", "'", "-", ".", "@"}
    };
    private static final String[][] ROWS_AR = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"ا", "ب", "ت", "ث", "ج", "ح", "خ", "د", "ذ", "ر"},
            {"ز", "س", "ش", "ص", "ض", "ط", "ظ", "ع", "غ", "ف"},
            {"ق", "ك", "ل", "م", "ن", "ه", "و", "ي", "ة", "ء"},
            {"أ", "إ", "آ", "ى", "ؤ", "ئ", "-", ".", "'", "@"}
    };
    private static final int KEY_DP = 40;
    private static final int GAP_DP = 6;

    private final Listener mListener;
    private final StringBuilder mText = new StringBuilder();
    private boolean mArabic;

    public OnScreenKeyboard(Context context, Listener listener) {
        super(context);
        mListener = listener;
        setOrientation(VERTICAL);
        build();
    }

    /** Keeps the keyboard in step when the text was changed elsewhere (voice search, a suggestion). */
    public void setText(String text) {
        mText.setLength(0);
        if (text != null) {
            mText.append(text);
        }
    }

    /** Width of the whole keyboard in pixels (so the results can be moved to its right). */
    public static int getWidthPx(Context context) {
        return dp(context, ROWS[0].length * (KEY_DP + GAP_DP));
    }

    private void build() {
        removeAllViews();
        for (String[] row : mArabic ? ROWS_AR : ROWS) {
            LinearLayout line = newLine();
            for (String key : row) {
                line.addView(newKey(key, 1, v -> type(key)));
            }
            addView(line);
        }

        LinearLayout controls = newLine();
        controls.addView(newKey("←", 2, v -> backspace()));
        controls.addView(newKey(mArabic ? "مسافة" : "Space", 2, v -> type(" ")));
        controls.addView(newKey(mArabic ? "EN" : "عربي", 2, v -> switchLanguage()));
        controls.addView(newKey(mArabic ? "مسح" : "Clear", 2, v -> clear()));
        controls.addView(newKey(mArabic ? "بحث" : "Search", 2, v -> mListener.onSearch(mText.toString())));
        addView(controls);
    }

    // Arabic / English letters. Focus stays on the language key so it can be pressed again.
    private void switchLanguage() {
        mArabic = !mArabic;
        build();
        View last = getChildAt(getChildCount() - 1);
        if (last instanceof LinearLayout && ((LinearLayout) last).getChildCount() > 2) {
            ((LinearLayout) last).getChildAt(2).requestFocus();
        }
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
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(getContext(), GAP_DP);
        line.setLayoutParams(lp);
        return line;
    }

    /** One key: dark rounded tile, white when focused (dark text), like the YouTube TV keyboard. */
    private TextView newKey(String label, int span, OnClickListener onClick) {
        Context context = getContext();
        TextView key = new TextView(context);
        key.setText(label);
        key.setGravity(Gravity.CENTER);
        key.setTextSize(label.length() > 1 ? 15 : 18);
        key.setTypeface(Typeface.DEFAULT_BOLD);
        key.setFocusable(true);
        key.setFocusableInTouchMode(false);
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

        int width = dp(context, span * KEY_DP + (span - 1) * GAP_DP);
        LayoutParams lp = new LayoutParams(width, dp(context, KEY_DP));
        lp.rightMargin = dp(context, GAP_DP);
        key.setLayoutParams(lp);
        return key;
    }

    private static StateListDrawable keyBackground(Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, rounded(context, "#F1F1F1"));
        states.addState(new int[]{android.R.attr.state_pressed}, rounded(context, "#F1F1F1"));
        states.addState(new int[]{}, rounded(context, "#212121"));
        return states;
    }

    private static GradientDrawable rounded(Context context, String color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.parseColor(color));
        shape.setCornerRadius(dp(context, 6));
        return shape;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}
