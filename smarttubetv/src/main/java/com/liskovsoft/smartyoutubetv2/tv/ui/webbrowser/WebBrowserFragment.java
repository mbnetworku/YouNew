package com.liskovsoft.smartyoutubetv2.tv.ui.webbrowser;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.WebBrowserPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.WebBrowserView;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class WebBrowserFragment extends Fragment implements WebBrowserView {
    private WebView mWebView;
    private WebBrowserPresenter mWebBrowserPresenter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWebBrowserPresenter = WebBrowserPresenter.instance(getContext());
        mWebBrowserPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.webbrowser, null);
        } catch (Exception e) { // Failed to load WebView provider: No WebView installed
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mWebView = (WebView) view.findViewById(R.id.webview);

        if (mWebView == null) {
            return;
        }

        mWebView.setBackgroundColor(Color.TRANSPARENT);
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        // YouNew: sign-in pages need these, and links must open inside this page, not in another app
        webSettings.setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, true);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // Google refuses sign-in inside this app on some boxes: continue in the box's browser
                if (isGoogleRefusal(url)) {
                    openInBrowserAndClose(null);
                }
            }
        });

        // No caching
        //webSettings.setAppCacheEnabled(false);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mWebBrowserPresenter.onViewInitialized();
    }

    @Override
    public void loadUrl(String url) {
        if (mWebView == null) { // No WebView on this box
            openInBrowserAndClose(url);
            return;
        }

        mWebView.loadUrl(url);
    }

    @Override
    public void close() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private void openInBrowserAndClose(String url) {
        mWebBrowserPresenter.openInBrowser(url);
        close();
    }

    private static boolean isGoogleRefusal(String url) {
        return url != null && url.contains("accounts.google.com")
                && (url.contains("/rejected") || url.contains("disallowed_useragent"));
    }
}
