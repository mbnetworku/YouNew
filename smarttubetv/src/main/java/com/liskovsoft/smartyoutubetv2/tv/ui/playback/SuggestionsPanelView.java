package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * YouNew: the Suggestions / playlist list as a pop-up over the video (instead of a full screen of rows).
 * <ul>
 *     <li>phone upright: a sheet that slides up from the bottom (about 60 % of the height)</li>
 *     <li>phone sideways, TV: a panel that slides in from the left (about 46 % of the width)</li>
 * </ul>
 * One tap on a video plays it. Tap next to the panel, the X button or Back closes it.
 * Works with a finger, a mouse and the remote (the rows are focusable).
 */
public class SuggestionsPanelView extends FrameLayout {
    public interface Host {
        void onSuggestionClicked(Video video);
        void onSuggestionLongClicked(Video video);
        /** The list reached its end: load more. */
        void onSuggestionsEnd(Video lastVideo);
    }

    private static final int SLIDE_MS = 220;
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_VIDEO = 1;

    private final Host mHost;
    private final View mScrim;
    private final LinearLayout mPanel;
    private final RecyclerView mList;
    private final TextView mTitle;
    private final ListAdapter mAdapter = new ListAdapter();
    private final List<Object> mItems = new ArrayList<>();
    private boolean mOpen;
    private boolean mFromLeft;

    public SuggestionsPanelView(Context context, Host host) {
        super(context);
        mHost = host;
        setVisibility(GONE);

        mScrim = new View(context);
        mScrim.setBackgroundColor(0x99000000);
        mScrim.setOnClickListener(v -> close());
        addView(mScrim, new LayoutParams(-1, -1));

        mPanel = new LinearLayout(context);
        mPanel.setOrientation(LinearLayout.VERTICAL);
        mPanel.setClickable(true); // touches on the panel do not reach the scrim
        addView(mPanel, new LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(10), dp(10), dp(6));
        mPanel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        mTitle = new TextView(context);
        mTitle.setText(context.getString(R.string.suggestions));
        mTitle.setTextColor(Color.WHITE);
        mTitle.setTextSize(18);
        mTitle.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(mTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        PlayerOverlayView.RoundButton close = new PlayerOverlayView.RoundButton(context, PlayerOverlayView.RoundButton.CLOSE);
        close.setOnClickListener(v -> close());
        header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));

        mList = new RecyclerView(context);
        mList.setLayoutManager(new LinearLayoutManager(context));
        mList.setAdapter(mAdapter);
        mList.setClipToPadding(false);
        mList.setPadding(dp(8), dp(4), dp(8), dp(16));
        mList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm != null && dy > 0 && lm.findLastVisibleItemPosition() >= mAdapter.getItemCount() - 4) {
                    Video last = lastVideo();
                    if (last != null) {
                        mHost.onSuggestionsEnd(last);
                    }
                }
            }
        });
        mPanel.addView(mList, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    public boolean isOpen() {
        return mOpen;
    }

    /** Replaces the list. Items are {@link Video} or a String (row title). */
    public void setItems(List<Object> items) {
        mItems.clear();
        mItems.addAll(items);
        mAdapter.notifyDataSetChanged();
    }

    public void open() {
        if (mOpen) {
            return;
        }
        mOpen = true;

        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        mFromLeft = !portrait;

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;

        FrameLayout.LayoutParams lp;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2141420);
        if (portrait) {
            lp = new FrameLayout.LayoutParams(-1, (int) (screenH * 0.62f), Gravity.BOTTOM);
            bg.setCornerRadii(new float[]{dp(22), dp(22), dp(22), dp(22), 0, 0, 0, 0});
        } else {
            lp = new FrameLayout.LayoutParams(Math.min((int) (screenW * 0.46f), dp(460)), -1, Gravity.START);
            bg.setCornerRadii(new float[]{0, 0, dp(22), dp(22), dp(22), dp(22), 0, 0});
        }
        mPanel.setLayoutParams(lp);
        mPanel.setBackground(bg);

        mScrim.animate().cancel();
        mPanel.animate().cancel();
        setVisibility(VISIBLE);
        mScrim.setAlpha(0f);
        mScrim.animate().alpha(1f).setStartDelay(0).setDuration(SLIDE_MS).start();

        if (portrait) {
            mPanel.setTranslationX(0);
            mPanel.setTranslationY(lp.height);
            mPanel.animate().translationY(0).setStartDelay(0).setDuration(SLIDE_MS).withEndAction(() -> mPanel.setTranslationY(0)).start();
        } else {
            mPanel.setTranslationY(0);
            mPanel.setTranslationX(-lp.width);
            mPanel.animate().translationX(0).setStartDelay(0).setDuration(SLIDE_MS).withEndAction(() -> mPanel.setTranslationX(0)).start();
        }

        mList.scrollToPosition(0);
        // The remote: put the focus on the first video
        postDelayed(() -> {
            if (mOpen && mList.getChildCount() > 0) {
                for (int i = 0; i < mList.getChildCount(); i++) {
                    View child = mList.getChildAt(i);
                    if (child.isFocusable()) {
                        child.requestFocus();
                        break;
                    }
                }
            }
        }, SLIDE_MS + 60);
    }

    public void close() {
        if (!mOpen) {
            return;
        }
        mOpen = false;

        mScrim.animate().cancel();
        mPanel.animate().cancel();
        mScrim.animate().alpha(0f).setStartDelay(0).setDuration(SLIDE_MS).start();
        ViewGroup.LayoutParams lp = mPanel.getLayoutParams();
        mPanel.animate()
                .translationX(mFromLeft ? -lp.width : 0)
                .translationY(mFromLeft ? 0 : lp.height)
                .setStartDelay(0)
                .setDuration(SLIDE_MS)
                .withEndAction(() -> {
                    if (!mOpen) {
                        setVisibility(GONE);
                    }
                })
                .start();
    }

    private Video lastVideo() {
        for (int i = mItems.size() - 1; i >= 0; i--) {
            if (mItems.get(i) instanceof Video) {
                return (Video) mItems.get(i);
            }
        }
        return null;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private class ListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemViewType(int position) {
            return mItems.get(position) instanceof Video ? TYPE_VIDEO : TYPE_HEADER;
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();

            if (viewType == TYPE_HEADER) {
                TextView header = new TextView(context);
                header.setTextColor(0xFFB8B8C8);
                header.setTextSize(14);
                header.setTypeface(Typeface.DEFAULT_BOLD);
                header.setPadding(dp(10), dp(12), dp(10), dp(4));
                header.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerView.ViewHolder(header) {};
            }

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            row.setFocusable(true);
            row.setClickable(true);
            RecyclerView.LayoutParams rowLp = new RecyclerView.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(2);
            row.setLayoutParams(rowLp);

            GradientDrawable lit = new GradientDrawable();
            lit.setColor(0x33FFFFFF);
            lit.setCornerRadius(dp(14));
            lit.setStroke(dp(2), Color.WHITE);
            GradientDrawable hover = new GradientDrawable();
            hover.setColor(0x26FFFFFF);
            hover.setCornerRadius(dp(14));
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_focused}, lit);
            states.addState(new int[]{android.R.attr.state_pressed}, lit);
            states.addState(new int[]{android.R.attr.state_hovered}, hover);
            row.setBackground(states);

            ImageView thumb = new ImageView(context);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundColor(0xFF2A2A38);
            thumb.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(10));
                }
            });
            thumb.setClipToOutline(true);
            row.addView(thumb, new LinearLayout.LayoutParams(dp(150), dp(84)));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(0, -2, 1f);
            textsLp.leftMargin = dp(12);
            row.addView(texts, textsLp);

            TextView title = new TextView(context);
            title.setTextColor(Color.WHITE);
            title.setTextSize(14);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setMaxLines(3);
            title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title, new LinearLayout.LayoutParams(-1, -2));

            TextView sub = new TextView(context);
            sub.setTextColor(0xFFB8B8C8);
            sub.setTextSize(12);
            sub.setMaxLines(2);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
            subLp.topMargin = dp(4);
            texts.addView(sub, subLp);

            return new RecyclerView.ViewHolder(row) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = mItems.get(position);

            if (!(item instanceof Video)) {
                ((TextView) holder.itemView).setText(String.valueOf(item));
                return;
            }

            final Video video = (Video) item;
            LinearLayout row = (LinearLayout) holder.itemView;
            ImageView thumb = (ImageView) row.getChildAt(0);
            LinearLayout texts = (LinearLayout) row.getChildAt(1);
            TextView title = (TextView) texts.getChildAt(0);
            TextView sub = (TextView) texts.getChildAt(1);

            title.setText(video.getTitle() != null ? video.getTitle() : "");
            CharSequence second = video.getSecondTitle();
            sub.setText(second != null ? second : "");

            String url = video.getCardImageUrl();
            if (!TextUtils.isEmpty(url)) {
                Glide.with(row.getContext()).load(url).into(thumb);
            } else {
                thumb.setImageDrawable(null);
            }

            row.setOnClickListener(v -> {
                close();
                mHost.onSuggestionClicked(video);
            });
            row.setOnLongClickListener(v -> {
                mHost.onSuggestionLongClicked(video);
                return true;
            });
        }
    }
}
