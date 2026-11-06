package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.EnumMap;
import java.util.Map;

/**
 * YouNew: one sign-in screen (before: a Leanback guided step with a tiny QR and three odd buttons).
 * Left: the QR code and the code. Right: three rounded buttons. "Sign in here" shows Google's own page INSIDE this
 * screen (a layer over the card, with a Back button), so no new screen or pop-up window opens.
 * The app never sees the password: it is typed on Google's page.
 */
public class SignInFragment extends Fragment implements SignInView {
    private static final String TAG = SignInFragment.class.getSimpleName();
    private static final int QR_SIZE_DP = 220;
    private static final int BG = 0xFF101018;
    private static final int CARD = 0xFF1C1C28;
    private static final int ACCENT = 0xFFE5334B;
    private static final int BUTTON = 0xFF2C2C3C;

    private SignInPresenter mSignInPresenter;
    private String mUserCode;
    private String mSignInUrl;
    private String mFullSignInUrl;

    private TextView mCodeView;
    private TextView mHintView;
    private ImageView mQrView;
    private FrameLayout mWebLayer;
    private FrameLayout mWebHolder;
    private WebView mWebView;
    private View mSignInButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSignInPresenter = SignInPresenter.instance(getContext());
        mSignInPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout root = new FrameLayout(getContext());
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(true);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout center = new FrameLayout(getContext());
        scroll.addView(center, new ViewGroup.LayoutParams(-1, -1));

        boolean wide = getResources().getConfiguration().screenWidthDp >= 600;

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(CARD, 28));
        card.setPadding(dp(28), dp(24), dp(28), dp(24));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(wide ? dp(820) : -1, -2, Gravity.CENTER);
        cardLp.setMargins(dp(16), dp(16), dp(16), dp(16));
        center.addView(card, cardLp);

        TextView title = text(getString(R.string.younew_signin_title), 26, Color.WHITE, true);
        card.addView(title);
        TextView subtitle = text(getString(R.string.younew_signin_subtitle), 16, 0xFFB8B8C8, false);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        card.addView(subtitle);

        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));

        // Left: QR + code
        LinearLayout left = new LinearLayout(getContext());
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        body.addView(left, wide ? new LinearLayout.LayoutParams(dp(250), -2) : new LinearLayout.LayoutParams(-1, -2));

        FrameLayout qrFrame = new FrameLayout(getContext());
        qrFrame.setBackground(round(Color.WHITE, 20));
        qrFrame.setPadding(dp(8), dp(8), dp(8), dp(8));
        mQrView = new ImageView(getContext());
        mQrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrFrame.addView(mQrView, new FrameLayout.LayoutParams(dp(QR_SIZE_DP), dp(QR_SIZE_DP)));
        left.addView(qrFrame, new LinearLayout.LayoutParams(-2, -2));

        mCodeView = text(getString(R.string.signin_view_title), 24, Color.WHITE, true);
        mCodeView.setGravity(Gravity.CENTER);
        mCodeView.setLetterSpacing(0.08f);
        mCodeView.setPadding(0, dp(14), 0, dp(2));
        left.addView(mCodeView, new LinearLayout.LayoutParams(-1, -2));

        mHintView = text("", 14, 0xFFB8B8C8, false);
        mHintView.setGravity(Gravity.CENTER);
        left.addView(mHintView, new LinearLayout.LayoutParams(-1, -2));

        // Right: buttons
        LinearLayout right = new LinearLayout(getContext());
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightLp = wide ? new LinearLayout.LayoutParams(0, -2, 1f) : new LinearLayout.LayoutParams(-1, -2);
        if (wide) {
            rightLp.leftMargin = dp(28);
        } else {
            rightLp.topMargin = dp(20);
        }
        body.addView(right, rightLp);

        TextView steps = text(getString(R.string.younew_signin_steps), 16, 0xFFD8D8E4, false);
        steps.setLineSpacing(0, 1.15f);
        steps.setPadding(0, 0, 0, dp(16));
        right.addView(steps);

        mSignInButton = button(getString(R.string.younew_sign_in_here), getString(R.string.younew_sign_in_here_desc), true, v -> showWebLayer());
        right.addView(mSignInButton, buttonLp());
        right.addView(button(getString(R.string.younew_sign_in_in_tv_browser), getString(R.string.younew_sign_in_in_tv_browser_desc), false, v -> openOutside()), buttonLp());
        right.addView(button(getString(R.string.signin_view_action_text), null, false, v -> mSignInPresenter.onActionClicked()), buttonLp());

        // Google's page, inside this screen
        mWebLayer = new FrameLayout(getContext());
        mWebLayer.setBackgroundColor(BG);
        mWebLayer.setVisibility(View.GONE);
        mWebLayer.setClickable(true); // swallow touches so the card below does not react
        LinearLayout webColumn = new LinearLayout(getContext());
        webColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView back = button(getString(R.string.younew_sign_in_back), null, false, v -> hideWebLayer());
        bar.addView(back, new LinearLayout.LayoutParams(dp(150), dp(48)));
        TextView barTitle = text(getString(R.string.younew_sign_in_google_page), 18, Color.WHITE, true);
        barTitle.setPadding(dp(16), 0, 0, 0);
        bar.addView(barTitle);
        webColumn.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        mWebHolder = new FrameLayout(getContext());
        mWebHolder.setBackground(round(Color.WHITE, 18));
        mWebHolder.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout.LayoutParams holderLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        holderLp.setMargins(dp(12), 0, dp(12), dp(12));
        webColumn.addView(mWebHolder, holderLp);
        mWebLayer.addView(webColumn, new FrameLayout.LayoutParams(-1, -1));
        root.addView(mWebLayer, new FrameLayout.LayoutParams(-1, -1));

        render();
        mSignInButton.requestFocus();
        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSignInPresenter.onViewInitialized();
    }

    @Override
    public void onDestroyView() {
        destroyWebView();
        mCodeView = null;
        mHintView = null;
        mQrView = null;
        mWebLayer = null;
        mWebHolder = null;
        mSignInButton = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSignInPresenter.onViewDestroyed();
    }

    /** Back key: closes Google's page first (goes back inside it when it can), then lets the screen close. */
    public boolean onBack() {
        if (mWebLayer != null && mWebLayer.getVisibility() == View.VISIBLE) {
            if (mWebView != null && mWebView.canGoBack()) {
                mWebView.goBack();
            } else {
                hideWebLayer();
            }
            return true;
        }
        return false;
    }

    @Override
    public void showCode(String userCode, String signInUrl) {
        showCode(userCode, signInUrl, null);
    }

    @Override
    public void showCode(String userCode, String signInUrl, String fullSignInUrl) {
        if (TextUtils.isEmpty(userCode)) {
            return;
        }

        mUserCode = userCode;
        mSignInUrl = signInUrl;
        mFullSignInUrl = fullSignInUrl != null ? fullSignInUrl : signInUrl;
        render();
    }

    @Override
    public void close() {
        destroyWebView();
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private void render() {
        if (mCodeView == null || getContext() == null) {
            return;
        }

        if (TextUtils.isEmpty(mUserCode)) {
            return;
        }

        mCodeView.setText(mUserCode);

        // The QR code is drawn on the box (no outside web site), black on white so a phone reads it from the sofa
        int size = dp(QR_SIZE_DP);
        Bitmap qr = TextUtils.isEmpty(mFullSignInUrl) ? null : createQrCode(mFullSignInUrl, size);
        if (qr != null) {
            mQrView.setImageBitmap(qr);
        } else {
            mQrView.setImageDrawable(null);
        }

        // An error message is shown as the "code": then there is no address to show
        mHintView.setText(TextUtils.isEmpty(mSignInUrl) ? "" : getString(R.string.younew_signin_qr_hint, mSignInUrl));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showWebLayer() {
        if (getActivity() == null || mWebLayer == null || TextUtils.isEmpty(mFullSignInUrl)) {
            MessageHelpers.showMessage(getContext(), R.string.signin_view_title);
            return;
        }

        if (!Utils.isWebViewAvailable(getContext())) {
            MessageHelpers.showLongMessage(getContext(), R.string.younew_no_webview);
            return;
        }

        destroyWebView();
        try {
            mWebView = new WebView(getActivity());
        } catch (RuntimeException e) { // No WebView on this box
            Log.e(TAG, "WebView error: " + e);
            MessageHelpers.showLongMessage(getContext(), R.string.younew_no_webview);
            return;
        }

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, true);
        mWebView.setWebViewClient(new WebViewClient()); // keep every page inside this screen
        mWebView.setBackgroundColor(Color.WHITE);
        mWebView.loadUrl(mFullSignInUrl);

        mWebHolder.addView(mWebView, new FrameLayout.LayoutParams(-1, -1));
        mWebLayer.setAlpha(0f);
        mWebLayer.setVisibility(View.VISIBLE);
        mWebLayer.animate().alpha(1f).setDuration(180).start();
        mWebView.requestFocus();
    }

    private void hideWebLayer() {
        destroyWebView();
        if (mWebLayer != null) {
            mWebLayer.setVisibility(View.GONE);
        }
        if (mSignInButton != null) {
            mSignInButton.requestFocus();
        }
    }

    private void destroyWebView() {
        if (mWebView != null) {
            try {
                if (mWebHolder != null) {
                    mWebHolder.removeView(mWebView);
                }
                mWebView.destroy();
            } catch (RuntimeException e) {
                // already gone
            }
            mWebView = null;
        }
    }

    /** Fallback for a box where Google refuses its page inside apps: the TV's own web browser. */
    private void openOutside() {
        if (getContext() == null || TextUtils.isEmpty(mFullSignInUrl)) {
            return;
        }

        Utils.openLinkExt(getContext(), mFullSignInUrl);
    }

    // ---- small view helpers ----

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(getContext());
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) {
            tv.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return tv;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        return lp;
    }

    /** A rounded button that works with remote (focus ring), mouse (hover) and finger (press). */
    private TextView button(String label, @Nullable String description, boolean primary, View.OnClickListener listener) {
        TextView b = new TextView(getContext());
        b.setText(description == null ? label : label + "\n" + description);
        if (description != null) {
            android.text.SpannableString s = new android.text.SpannableString(b.getText());
            s.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, label.length(), 0);
            s.setSpan(new android.text.style.RelativeSizeSpan(1.15f), 0, label.length(), 0);
            s.setSpan(new android.text.style.ForegroundColorSpan(primary ? Color.WHITE : 0xFFE8E8F0), 0, label.length(), 0);
            s.setSpan(new android.text.style.ForegroundColorSpan(primary ? 0xFFFFDDE2 : 0xFF9A9AB0), label.length(), s.length(), 0);
            b.setText(s);
        } else {
            b.setTypeface(Typeface.DEFAULT_BOLD);
        }
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setMinHeight(dp(56));
        b.setPadding(dp(22), dp(10), dp(22), dp(10));
        b.setFocusable(true);
        b.setClickable(true);

        int base = primary ? ACCENT : BUTTON;
        GradientDrawable normal = round(base, 28);
        GradientDrawable lit = round(primary ? 0xFFFF4D66 : 0xFF3E3E54, 28);
        lit.setStroke(dp(3), Color.WHITE); // white focus ring like the rest of the app
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, lit);
        states.addState(new int[]{android.R.attr.state_hovered}, lit);
        states.addState(new int[]{android.R.attr.state_pressed}, lit);
        states.addState(new int[]{}, normal);
        b.setBackground(states);

        b.setOnClickListener(listener);
        return b;
    }

    @Nullable
    private static Bitmap createQrCode(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);

            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (WriterException | IllegalArgumentException | OutOfMemoryError e) {
            Log.e(TAG, "QR code error: " + e);
            return null;
        }
    }
}
