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

        // Logo at the top
        ImageView logo = new ImageView(this);
        int logoRes = getResources().getIdentifier("app_logo", "mipmap", getPackageName());
        if (logoRes != 0) {
            logo.setImageResource(logoRes);
        }
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(dp(220), dp(90), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        logoLp.topMargin = dp(36);
        root.addView(logo, logoLp);

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

        root.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

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
        FrameLayout.LayoutParams guestLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        guestLp.bottomMargin = dp(56);
        root.addView(guest, guestLp);

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

        FrameLayout frame = new FrameLayout(this);
        frame.addView(picture, new FrameLayout.LayoutParams(dp(AVATAR_DP), dp(AVATAR_DP)));
        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(dp(AVATAR_DP), dp(AVATAR_DP));
        frameLp.bottomMargin = dp(16);
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
        item.setOnFocusChangeListener((v, focused) -> {
            frame.setForeground(focused ? ring : null);
            float scale = focused ? 1.08f : 1f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
        });

        return item;
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
