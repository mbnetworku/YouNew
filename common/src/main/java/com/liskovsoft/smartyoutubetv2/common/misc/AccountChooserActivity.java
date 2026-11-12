package com.liskovsoft.smartyoutubetv2.common.misc;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.List;

/**
 * YouNew: "Who's watching?" screen, like YouTube TV.
 * Shows each signed-in account as a large round picture with its name, an "Add account" circle
 * and a "Watch as guest" button. Works with the remote (focus + OK) and with a mouse (one click).
 */
public class AccountChooserActivity extends MotherActivity {
    private static final int AVATAR_DP = 120;
    private static final int AVATAR_SHORT_DP = 84; // a phone held sideways

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
    }

    private View buildContent() {
        FrameLayout root = new FrameLayout(this);
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {Color.parseColor("#120E10"), Color.parseColor("#1C0F13"), Color.parseColor("#4A0E1E")});
        root.setBackground(background);

        // YouNew: logo, accounts and "Watch as guest" stacked in one centred column that scrolls if the screen is
        // short (a phone held sideways). Before, the three were placed separately and overlapped there. The
        // accounts scroll sideways, so with many accounts none is cut off at the screen edge.
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(0, dp(16), 0, dp(16));
        scroll.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Logo at the top
        ImageView logo = new ImageView(this);
        int logoRes = getResources().getIdentifier("app_logo", "mipmap", getPackageName());
        if (logoRes != 0) {
            logo.setImageResource(logoRes);
        }
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        boolean shortScreen = getResources().getConfiguration().screenHeightDp < 480;
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(220), dp(shortScreen ? 56 : 90));
        logoLp.bottomMargin = dp(shortScreen ? 8 : 24);
        column.addView(logo, logoLp);

        // Accounts in the middle
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        View firstFocus = null;

        List<Account> accounts = YouTubeServiceManager.instance().getSignInService().getAccounts();
        if (accounts != null) {
            for (Account account : accounts) {
                if (account == null) {
                    continue;
                }
                View item = accountItem(account);
                row.addView(item);
                if (firstFocus == null || account.isSelected()) {
                    firstFocus = item;
                }
            }
        }

        View addItem = addAccountItem();
        row.addView(addItem);
        if (firstFocus == null) {
            firstFocus = addItem;
        }

        android.widget.HorizontalScrollView rowScroll = new android.widget.HorizontalScrollView(this);
        rowScroll.setHorizontalScrollBarEnabled(false);
        rowScroll.setClipToPadding(false);
        rowScroll.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(rowScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // "Watch as guest" at the bottom
        TextView guest = new TextView(this);
        guest.setText(R.string.younew_watch_as_guest);
        guest.setTextSize(18);
        guest.setTypeface(Typeface.DEFAULT_BOLD);
        guest.setGravity(Gravity.CENTER);
        guest.setPadding(dp(28), dp(10), dp(28), dp(10));
        guest.setFocusable(true);
        guest.setClickable(true);
        guest.setTextColor(Color.parseColor("#F1F1F1"));
        guest.setBackground(pill("#33FFFFFF"));
        guest.setOnFocusChangeListener((v, focused) -> {
            guest.setBackground(pill(focused ? "#F1F1F1" : "#33FFFFFF"));
            guest.setTextColor(Color.parseColor(focused ? "#0F0F0F" : "#F1F1F1"));
        });
        guest.setOnClickListener(v -> choose(null));
        LinearLayout.LayoutParams guestLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        guestLp.topMargin = dp(shortScreen ? 12 : 32);
        column.addView(guest, guestLp);

        View focus = firstFocus;
        root.post(focus::requestFocus);

        return root;
    }

    private View accountItem(Account account) {
        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(oval("#3A2A2E"));
        Glide.with(this)
                .load(account.getAvatarImageUrl())
                .apply(RequestOptions.circleCropTransform())
                .into(avatar);

        String handle = account.getEmail();
        return item(avatar, account.getName(), handle, v -> choose(account));
    }

    private View addAccountItem() {
        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextSize(64);
        plus.setGravity(Gravity.CENTER);
        plus.setTextColor(Color.parseColor("#F1F1F1"));
        plus.setBackground(oval("#40FFFFFF"));
        return item(plus, getString(R.string.younew_add_account), null, v -> {
            YTSignInPresenter.instance(this).start();
            finish();
        });
    }

    /** One choice: round picture (white ring and a little bigger when focused), name, optional second line. */
    private View item(View picture, String name, @Nullable String subtitle, View.OnClickListener onClick) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(dp(18), dp(12), dp(18), dp(12));
        item.setFocusable(true);
        item.setClickable(true);
        item.setOnClickListener(onClick);

        int avatarDp = getResources().getConfiguration().screenHeightDp < 480 ? AVATAR_SHORT_DP : AVATAR_DP;
        FrameLayout frame = new FrameLayout(this);
        frame.addView(picture, new FrameLayout.LayoutParams(dp(avatarDp), dp(avatarDp)));
        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(dp(avatarDp), dp(avatarDp));
        frameLp.bottomMargin = dp(12);
        item.addView(frame, frameLp);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#F1F1F1"));
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        item.addView(title);

        if (subtitle != null) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextSize(14);
            sub.setTextColor(Color.parseColor("#AAAAAA"));
            sub.setGravity(Gravity.CENTER);
            sub.setSingleLine(true);
            item.addView(sub);
        }

        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(dp(4), Color.WHITE);
        item.setOnFocusChangeListener((v, focused) -> highlight(frame, ring, v, focused));
        // A mouse (TV box) lights the account under the pointer, the same as the remote's focus
        item.setOnHoverListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_HOVER_ENTER) {
                highlight(frame, ring, v, true);
            } else if (event.getAction() == android.view.MotionEvent.ACTION_HOVER_EXIT) {
                highlight(frame, ring, v, v.isFocused());
            }
            return false;
        });

        return item;
    }

    private static void highlight(FrameLayout frame, GradientDrawable ring, View item, boolean on) {
        frame.setForeground(on ? ring : null); // FrameLayout has a foreground on every Android version
        float scale = on ? 1.08f : 1f;
        item.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
    }

    private void choose(@Nullable Account account) {
        AccountSelectionPresenter.instance(this).selectAccount(account);
        finish();
    }

    private GradientDrawable oval(String color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Color.parseColor(color));
        return shape;
    }

    private GradientDrawable pill(String color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.parseColor(color));
        shape.setCornerRadius(dp(28));
        return shape;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
