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
    private TextView mQuery;
    private LinearLayout mChips;
    private OnScreenKeyboard mKeyboard;
    private final MediaServiceSearchTagProvider mTagProvider;
    private String mText = "";
    private boolean mReleased;
    private boolean mNarrow;
    private List<Tag> mTags;

    private final Runnable mSuggest = new Runnable() {
        @Override
        public void run() {
            loadSuggestions(mText);
        }
    };

    public SearchPanelView(Context context) {
        super(context);
        mTagProvider = new MediaServiceSearchTagProvider(false);

        build(isNarrow(0));

        loadSuggestions(""); // history / trending when nothing is typed yet
    }

    /**
     * Narrow = a phone held upright. Decided by the room the panel really has (the sidebar takes a part of the
     * screen); before the panel is measured, by the screen without the collapsed sidebar.
     */
    private boolean isNarrow(int widthPx) {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        float widthDp = widthPx > 0 ? widthPx / metrics.density : metrics.widthPixels / metrics.density - 72;
        return widthDp < 548;
    }

    /** The panel got its size, or another one (phone turned): choose the layout again and fit the keyboard. */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w <= 0) {
            return;
        }

        // Not inside the layout pass that reported the size
        post(() -> {
            if (mReleased || getWidth() <= 0) {
                return;
            }

            boolean narrow = isNarrow(visibleWidth());
            if (narrow != mNarrow) {
                build(narrow);
                showChips(mTags);
            }
            fitKeyboard();
        });
    }

    /**
     * The keyboard may use the width that is really visible, and no more. The Home screen moves this panel right
     * (next to the sidebar) without making it narrower, so its own width can reach past the screen edge: in
     * landscape the DELETE key ended outside the screen. Wide layout: the mic and a column of suggestions share
     * the row with the keyboard.
     */
    private void fitKeyboard() {
        if (mKeyboard == null || getWidth() <= 0) {
            return;
        }

        int visible = visibleWidth();

        // Keep everything inside the visible part: the hidden strip on the right becomes extra padding
        if (mRoot != null) {
            int right = mSide + Math.max(0, getWidth() - visible);
            if (mRoot.getPaddingRight() != right) {
                mRoot.setPadding(mRoot.getPaddingLeft(), mRoot.getPaddingTop(), right, mRoot.getPaddingBottom());
            }
        }

        if (mNarrow) {
            mKeyboard.setMaxWidthPx(visible - 2 * dp(12));
        } else {
            mKeyboard.setMaxWidthPx(visible - 2 * dp(24) - dp(52 + 12) - dp(140));
        }

        // Safety net, whatever the cause: when the layout is done, if the keyboard still reaches past the screen's
        // right edge (seen on an Android 5 emulator: keys A to E on the screen, the rest outside), pull the content
        // back in by exactly that overflow.
        final int basePadding = mRoot != null ? mSide + Math.max(0, getWidth() - visible) : 0;
        mKeyboard.post(() -> {
            if (mReleased || mRoot == null || mKeyboard == null || mKeyboard.getWidth() <= 0) {
                return;
            }

            int[] place = new int[2];
            mKeyboard.getLocationOnScreen(place);
            int over = place[0] + mKeyboard.getWidth() - (getResources().getDisplayMetrics().widthPixels - dp(8));

            if (over > 0) {
                mRoot.setPadding(mRoot.getPaddingLeft(), mRoot.getPaddingTop(), basePadding + over, mRoot.getPaddingBottom());
            }
        });
    }

    /** The part of this panel's width that is on the screen. */
    private int visibleWidth() {
        int[] place = new int[2];
        getLocationOnScreen(place);
        int screen = getResources().getDisplayMetrics().widthPixels;
        int visible = Math.min(getWidth(), screen - Math.max(0, place[0]));
        return visible > 0 ? visible : getWidth();
    }

    private int mLastLeft = -1;
    private LinearLayout mRoot;
    private int mSide;

    /**
     * The sidebar opened or closed, or the Home screen slid the panel into place (an animation that runs after
     * the layout, so onLayout does not see it): the visible width changed. Checked before every frame is drawn,
     * which costs one position lookup; the layout is only redone when the panel really moved.
     */
    private final android.view.ViewTreeObserver.OnPreDrawListener mMoveWatcher = () -> {
        int[] place = new int[2];
        getLocationOnScreen(place);
        if (place[0] != mLastLeft && getWidth() > 0) {
            mLastLeft = place[0];
            post(this::refit);
        }
        return true;
    };

    private void refit() {
        if (mReleased || getWidth() <= 0) {
            return;
        }

        boolean narrow = isNarrow(visibleWidth());
        if (narrow != mNarrow) {
            build(narrow);
            showChips(mTags);
        }
        fitKeyboard();
    }

    private final Runnable mRefit = this::refit;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(mMoveWatcher);

        // Safety net, like the Settings panel has: the Home screen slides this panel into place and a slow device
        // can finish after the fits above, so check once more shortly after it appeared (cheap and harmless)
        postDelayed(mRefit, 500);
        postDelayed(mRefit, 1_500);
        postDelayed(mRefit, 3_000);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(mMoveWatcher);
        removeCallbacks(mRefit);
        super.onDetachedFromWindow();
    }

    private void build(boolean narrow) {
        Context context = getContext();
        mNarrow = narrow;
        removeAllViews();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int side = dp(narrow ? 12 : 24); // an upright phone needs every bit of width for the keyboard
        root.setPadding(side, dp(8), side, dp(8));
        addView(root, new LayoutParams(-1, -1));
        mRoot = root;
        mSide = side;

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
        showQuery();

        // Suggestions (chips)
        mChips = new LinearLayout(context);
        mChips.setOrientation(LinearLayout.VERTICAL);
        ScrollView chipsScroll = new ScrollView(context);
        chipsScroll.setVerticalScrollBarEnabled(false);
        chipsScroll.addView(mChips, new ViewGroup.LayoutParams(-1, -2));

        // Mic + keyboard
        mKeyboard = new OnScreenKeyboard(context, new OnScreenKeyboard.Listener() {
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
            // The typed text and the mic share a line; the keyboard has the whole width below them, then the
            // suggestions. (Mic and keyboard side by side were wider than a phone: the right keys were cut off.)
            LinearLayout queryRow = new LinearLayout(context);
            queryRow.setOrientation(LinearLayout.HORIZONTAL);
            queryRow.setGravity(Gravity.CENTER_VERTICAL);
            queryRow.addView(mQuery, new LinearLayout.LayoutParams(0, dp(44), 1f));
            LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(dp(44), dp(44));
            micLp.leftMargin = dp(8);
            queryRow.addView(mic, micLp);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.topMargin = dp(8);
            rowLp.bottomMargin = dp(8);
            root.addView(queryRow, rowLp);

            LinearLayout.LayoutParams kbLp = new LinearLayout.LayoutParams(-2, -2);
            kbLp.gravity = Gravity.CENTER_HORIZONTAL;
            kbLp.bottomMargin = dp(8);
            root.addView(mKeyboard, kbLp);
            root.addView(chipsScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
            fitKeyboard();
        } else {
            LinearLayout.LayoutParams queryLp = new LinearLayout.LayoutParams(dp(420), dp(44));
            queryLp.topMargin = dp(8);
            queryLp.bottomMargin = dp(10);
            root.addView(mQuery, queryLp);

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
            right.addView(mKeyboard, new LinearLayout.LayoutParams(-2, -2));
            body.addView(right, new LinearLayout.LayoutParams(-2, -1));
        }
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

        mTags = tags; // kept, so they can be drawn again when the layout changes (phone turned)
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
        // Dark text on the white pill when focused, hovered or pressed (a finger presses without focusing)
        chip.setTextColor(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_hovered}, {android.R.attr.state_pressed}, {}},
                new int[]{0xFF0F0F0F, 0xFF0F0F0F, 0xFF0F0F0F, 0xFFF1F1F1}));
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
