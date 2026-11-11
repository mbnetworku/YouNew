package com.liskovsoft.smartyoutubetv2.tv.ui.search;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.OnScreenKeyboard;

import java.util.List;

/**
 * YouNew: the Search panel inside the Home screen (like YouTube TV).
 * <ul>
 *     <li>wide screen (TV, phone sideways): the suggestions list on the left, the round mic button and the compact
 *     keyboard on the right</li>
 *     <li>narrow screen (phone upright): the keyboard on top, the suggestions below it</li>
 * </ul>
 * The typed text shows in a rounded field above. SEARCH (or a tap on a suggestion) opens the search results,
 * the mic opens voice search.
 */
public class SearchPanelView extends FrameLayout {
    private static final int SUGGEST_DELAY_MS = 250;
    private static final int MAX_CHIPS = 10;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final TextView mQuery;
    private final LinearLayout mChips;
    private final MediaServiceSearchTagProvider mTagProvider;
    private String mText = "";
    private boolean mReleased;

    private final Runnable mSuggest = new Runnable() {
        @Override
        public void run() {
            loadSuggestions(mText);
        }
    };

    public SearchPanelView(Context context) {
        super(context);
        mTagProvider = new MediaServiceSearchTagProvider(false);

        float density = getResources().getDisplayMetrics().density;
        boolean narrow = getResources().getDisplayMetrics().widthPixels / density < 620;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(8), dp(24), dp(8));
        addView(root, new LayoutParams(-1, -1));

        // Title and the typed text
        TextView title = new TextView(context);
        title.setText(R.string.younew_header_search);
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-2, -2));

        mQuery = new TextView(context);
        mQuery.setTextColor(Color.WHITE);
        mQuery.setTextSize(18);
        mQuery.setSingleLine(true);
        mQuery.setEllipsize(TextUtils.TruncateAt.START);
        mQuery.setGravity(Gravity.CENTER_VERTICAL);
        mQuery.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable queryBg = new GradientDrawable();
        queryBg.setColor(0xFF272727);
        queryBg.setCornerRadius(dp(22));
        queryBg.setStroke(dp(1), 0xFF3A3A3A); // soft edge, like the YouTube search field
        mQuery.setBackground(queryBg);
        LinearLayout.LayoutParams queryLp = new LinearLayout.LayoutParams(narrow ? -1 : dp(420), dp(44));
        queryLp.topMargin = dp(8);
        queryLp.bottomMargin = dp(10);
        root.addView(mQuery, queryLp);
        showQuery();

        // Suggestions (chips)
        mChips = new LinearLayout(context);
        mChips.setOrientation(LinearLayout.VERTICAL);
        ScrollView chipsScroll = new ScrollView(context);
        chipsScroll.setVerticalScrollBarEnabled(false);
        chipsScroll.addView(mChips, new ViewGroup.LayoutParams(-1, -2));

        // Mic + keyboard
        OnScreenKeyboard keyboard = new OnScreenKeyboard(context, new OnScreenKeyboard.Listener() {
            @Override
            public String getText() {
                return mText;
            }

            @Override
            public void onTextChanged(String text) {
                mText = text;
                showQuery();
                mHandler.removeCallbacks(mSuggest);
                mHandler.postDelayed(mSuggest, SUGGEST_DELAY_MS);
            }

            @Override
            public void onSearch(String text) {
                search(text);
            }
        });
        MicButton mic = new MicButton(context);
        mic.setOnClickListener(v -> SearchPresenter.instance(getContext()).startVoice());

        if (narrow) {
            LinearLayout inputRow = new LinearLayout(context);
            inputRow.setOrientation(LinearLayout.HORIZONTAL);
            inputRow.setGravity(Gravity.CENTER_VERTICAL);
            inputRow.addView(mic, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout.LayoutParams kbLp = new LinearLayout.LayoutParams(-2, -2);
            kbLp.leftMargin = dp(8);
            inputRow.addView(keyboard, kbLp);
            root.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));
            root.addView(chipsScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.HORIZONTAL);
            root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
            body.addView(chipsScroll, new LinearLayout.LayoutParams(0, -1, 1f));
            LinearLayout right = new LinearLayout(context);
            right.setOrientation(LinearLayout.HORIZONTAL);
            right.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(dp(52), dp(52));
            micLp.topMargin = dp(40);
            micLp.rightMargin = dp(12);
            right.addView(mic, micLp);
            right.addView(keyboard, new LinearLayout.LayoutParams(-2, -2));
            body.addView(right, new LinearLayout.LayoutParams(-2, -1));
        }

        loadSuggestions(""); // history / trending when nothing is typed yet
    }

    private void showQuery() {
        if (TextUtils.isEmpty(mText)) {
            mQuery.setText(R.string.younew_search_hint);
            mQuery.setTextColor(0xFF9A9A9A);
        } else {
            mQuery.setText(mText);
            mQuery.setTextColor(Color.WHITE);
        }
    }

    private void search(String text) {
        String query = text != null ? text.trim() : "";
        if (!query.isEmpty()) {
            SearchPresenter.instance(getContext()).startSearch(query);
        }
    }

    private void loadSuggestions(String query) {
        if (mReleased) {
            return;
        }

        mTagProvider.search(query, results -> mHandler.post(() -> showChips(results)));
    }

    private void showChips(List<Tag> tags) {
        if (mReleased) {
            return;
        }

        mChips.removeAllViews();
        if (tags == null) {
            return;
        }

        int count = 0;
        for (Tag tag : tags) {
            if (tag == null || TextUtils.isEmpty(tag.tag)) {
                continue;
            }
            mChips.addView(newChip(tag.tag), chipLp());
            if (++count >= MAX_CHIPS) {
                break;
            }
        }
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
        lp.bottomMargin = dp(10); // a little more air between the suggestion pills
        return lp;
    }

    /** A suggestion: a rounded grey pill, white when focused or hovered. */
    private TextView newChip(String text) {
        TextView chip = new TextView(getContext());
        chip.setText(text);
        chip.setTextSize(17);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(18), 0, dp(18), 0);
        chip.setMaxWidth(dp(420));
        chip.setTextColor(0xFFF1F1F1);
        chip.setFocusable(true);
        chip.setClickable(true);

        GradientDrawable lit = new GradientDrawable();
        lit.setColor(0xFFF1F1F1);
        lit.setCornerRadius(dp(19));
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0xFF3A3A3A);
        normal.setCornerRadius(dp(19));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, lit);
        states.addState(new int[]{android.R.attr.state_hovered}, lit);
        states.addState(new int[]{android.R.attr.state_pressed}, lit);
        states.addState(new int[]{}, normal);
        chip.setBackground(states);
        chip.setOnFocusChangeListener((v, focused) -> ((TextView) v).setTextColor(focused ? 0xFF0F0F0F : 0xFFF1F1F1));
        chip.setOnClickListener(v -> search(text));
        return chip;
    }

    public void release() {
        mReleased = true;
        mHandler.removeCallbacksAndMessages(null);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** The round white microphone button (voice search). */
    private static class MicButton extends View {
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mGlyph = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();

        MicButton(Context context) {
            super(context);
            setClickable(true);
            setFocusable(true);
            mGlyph.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            boolean active = isFocused() || isHovered() || isPressed();
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(getWidth(), getHeight()) / 2f;

            mFill.setColor(active ? Color.WHITE : 0xFFEDEDED);
            canvas.drawCircle(cx, cy, r, mFill);

            mGlyph.setColor(0xFF0F0F0F);
            mGlyph.setStyle(Paint.Style.FILL);
            // the capsule
            mRect.set(cx - r * 0.2f, cy - r * 0.5f, cx + r * 0.2f, cy + r * 0.12f);
            canvas.drawRoundRect(mRect, r * 0.2f, r * 0.2f, mGlyph);
            // the cup
            mGlyph.setStyle(Paint.Style.STROKE);
            mGlyph.setStrokeWidth(r * 0.1f);
            mRect.set(cx - r * 0.34f, cy - r * 0.3f, cx + r * 0.34f, cy + r * 0.3f);
            canvas.drawArc(mRect, 0, 180, false, mGlyph);
            // the stem
            canvas.drawLine(cx, cy + r * 0.3f, cx, cy + r * 0.5f, mGlyph);
        }
    }
}
