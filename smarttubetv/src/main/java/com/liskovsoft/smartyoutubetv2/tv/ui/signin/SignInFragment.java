package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SignInFragment extends GuidedStepSupportFragment implements SignInView {
    private static final String TAG = SignInFragment.class.getSimpleName();
    private static final int CONTINUE = 2;
    private static final int SIGN_IN_ON_TV = 4;
    private static final int QR_SIZE_DP = 240;
    private SignInPresenter mSignInPresenter;
    private String mFullSignInUrl;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSignInPresenter = SignInPresenter.instance(getContext());
        mSignInPresenter.setView(this);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSignInPresenter.onViewInitialized();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSignInPresenter.onViewDestroyed();
    }

    @Override
    public void showCode(String userCode, String signInUrl) {
        setTitle(userCode, signInUrl, null);
    }

    @Override
    public void showCode(String userCode, String signInUrl, String fullSignInUrl) {
        setTitle(userCode, signInUrl, fullSignInUrl);
    }

    private void setTitle(String userCode, String signInUrl, String fullSignInUrl) {
        if (getContext() == null || TextUtils.isEmpty(userCode)) {
            return;
        }

        getGuidanceStylist().getTitleView().setText(userCode);

        mFullSignInUrl = fullSignInUrl != null ? fullSignInUrl : signInUrl;

        // YouNew: the QR code is drawn on the box (before, an outside web site made the picture: when the box
        // could not reach it, a fixed picture without your code was shown and scanning it did not work)
        showQrCode(mFullSignInUrl);

        if (TextUtils.isEmpty(signInUrl)) {
            // An error message is shown as the "code"
            getGuidanceStylist().getDescriptionView().setText("");
            return;
        }

        String description = getString(R.string.signin_view_description, signInUrl);
        int start = description.indexOf(signInUrl);
        int end = start + signInUrl.length();
        CharSequence coloredDescription = Utils.color(description, ContextCompat.getColor(getContext(), R.color.red), start, end);

        getGuidanceStylist().getDescriptionView().setText(coloredDescription);
    }

    /** Draws the QR code for the sign-in link, large, black on white, so a phone camera reads it from the sofa. */
    private void showQrCode(String url) {
        ImageView iconView = getGuidanceStylist().getIconView();

        if (iconView == null) {
            return;
        }

        int size = (int) (QR_SIZE_DP * getResources().getDisplayMetrics().density);
        Bitmap qr = TextUtils.isEmpty(url) ? null : createQrCode(url, size);

        if (qr != null) {
            iconView.setImageBitmap(qr);
        } else {
            iconView.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.activate_account_qrcode));
        }

        iconView.setMaxWidth(size);
        iconView.setMaxHeight(size);
        ViewGroup.LayoutParams lp = iconView.getLayoutParams();
        if (lp != null) {
            lp.width = size;
            lp.height = size;
            iconView.setLayoutParams(lp);
        }
        iconView.setVisibility(View.VISIBLE);
    }

    @Nullable
    private static Bitmap createQrCode(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 2); // white border, needed by phone cameras
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

    @Override
    public void close() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    @NonNull
    public GuidanceStylist.Guidance onCreateGuidance(@NonNull Bundle savedInstanceState) {
        String title = getString(R.string.signin_view_title);
        String description = getString(R.string.signin_view_description, "");
        return new GuidanceStylist.Guidance(title, description, "", ContextCompat.getDrawable(getContext(), R.drawable.activate_account_qrcode));
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        // YouNew: first button (OK on the remote) = sign in right here on the TV
        GuidedAction signInOnTv = new GuidedAction.Builder()
                .id(SIGN_IN_ON_TV)
                .title(getString(R.string.younew_sign_in_on_tv))
                .description(getString(R.string.younew_sign_in_on_tv_desc))
                .build();
        GuidedAction close = new GuidedAction.Builder()
                .id(CONTINUE)
                .title(getString(R.string.signin_view_action_text))
                .build();
        actions.add(signInOnTv);
        actions.add(close);
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == CONTINUE) {
            mSignInPresenter.onActionClicked();
        } else if (action.getId() == SIGN_IN_ON_TV) {
            signInOnTv();
        }
    }

    /**
     * YouNew: opens Google's own sign-in page inside the app, with the code already in it. You type your Google
     * email and password there (mouse or keyboard) and press Allow; the page and this screen then close by
     * themselves. If the box has no WebView, or Google refuses sign-in inside apps, the box's browser is used.
     * (The app never sees the password: it is typed on Google's page.)
     */
    private void signInOnTv() {
        if (getContext() == null || TextUtils.isEmpty(mFullSignInUrl)) {
            return;
        }

        Utils.openLink(getContext(), mFullSignInUrl);
    }
}
