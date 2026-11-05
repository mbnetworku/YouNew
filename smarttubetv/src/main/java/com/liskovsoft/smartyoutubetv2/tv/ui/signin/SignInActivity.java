package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SignInActivity extends LeanbackActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction().replace(android.R.id.content, new SignInFragment()).commit();
        }
    }

    @Override
    public void onBackPressed() {
        // YouNew: Back first closes Google's page inside the sign-in screen
        Fragment fragment = getSupportFragmentManager().findFragmentById(android.R.id.content);
        if (fragment instanceof SignInFragment && ((SignInFragment) fragment).onBack()) {
            return;
        }

        super.onBackPressed();
    }

    @Override
    public void finish() {
        super.finish();

        finishReally();
    }
}
