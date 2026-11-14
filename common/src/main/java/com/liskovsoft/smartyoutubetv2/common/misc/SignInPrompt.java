package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;

/**
 * YouNew: asks to sign in when something needs a YouTube account (Like, Dislike, Watch later, Add to playlist,
 * Subscribe) and nobody is signed in. Before, those only showed a short "Signed users only" note, or did nothing.
 * <ul>
 *     <li>Sign in now: opens the sign-in screen (QR code, or Google's page on the TV)</li>
 *     <li>Save on this device only: offered where the app can do it without an account (Subscribe)</li>
 *     <li>Not now: closes the question</li>
 * </ul>
 */
public class SignInPrompt {
    private SignInPrompt() {
    }

    public static boolean isSigned() {
        return YouTubeSignInService.instance().isSigned();
    }

    /**
     * @return true when signed in (go on); false when the question was shown instead
     */
    public static boolean check(Context context) {
        return check(context, null);
    }

    /**
     * @param withoutAccount what to do on "Save on this device only" (null: that choice is not offered)
     * @return true when signed in (go on); false when the question was shown instead
     */
    public static boolean check(Context context, @Nullable Runnable withoutAccount) {
        if (isSigned()) {
            return true;
        }

        show(context, withoutAccount);
        return false;
    }

    /**
     * History without an account shows only what was watched in this app (saved on the device). Tell it once per
     * run, and offer the sign-in that shows the whole YouTube history of the account.
     */
    public static void showLocalHistoryNotice(Context context) {
        if (context == null || isSigned()) {
            return;
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        java.util.List<com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem> text = new java.util.ArrayList<>();
        text.add(UiOptionItem.from(context.getString(R.string.younew_history_local_only)));
        dialog.appendStringsCategory(context.getString(R.string.header_history), text);

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.younew_history_signin_button), option -> {
            dialog.closeDialog();
            YTSignInPresenter.instance(context).start();
        }));
        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.younew_signin_not_now), option -> dialog.closeDialog()));

        dialog.showDialog(context.getString(R.string.header_history));
    }

    public static void show(Context context, @Nullable Runnable withoutAccount) {
        if (context == null) {
            return;
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.younew_signin_now), option -> {
            dialog.closeDialog();
            YTSignInPresenter.instance(context).start();
        }));

        if (withoutAccount != null) {
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.younew_signin_device_only), option -> {
                dialog.closeDialog();
                withoutAccount.run();
            }));
        }

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.younew_signin_not_now), option -> dialog.closeDialog()));

        dialog.showDialog(context.getString(R.string.younew_signin_needed));
    }
}
