package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;

import org.json.JSONArray;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * YouNew: the app updates itself from its own GitHub releases.
 * <br>
 * It looks at https://github.com/mbnetworku/YouNew/releases/latest, and if that version is newer than the installed
 * one it asks "Update now / Later". "Update now" downloads the file made for this device (32-bit or 64-bit ARM, else
 * the universal one) and opens Android's installer. It checks when the app starts (at most every 12 hours, can be
 * switched off in Settings > About) and on demand from Settings > About.
 * <br>
 * Android only installs an update that is signed with the same key as the installed app. The downloaded file is
 * checked first, and if the keys differ the app says so instead of failing in the installer.
 */
public final class AppUpdater {
    private static final String TAG = AppUpdater.class.getSimpleName();
    private static final String RELEASES_URL = "https://api.github.com/repos/mbnetworku/YouNew/releases/latest";
    // YouNew: beta channel: the newest release of any kind (production or beta), for people who opted in
    private static final String RELEASES_LIST_URL = "https://api.github.com/repos/mbnetworku/YouNew/releases?per_page=10";
    private static final String KEY_BETA = "include_beta";
    private static final String PREFS = "younew_update";
    private static final String KEY_AUTO = "auto";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final long CHECK_EVERY_MS = 12L * 60 * 60 * 1000;
    private static final int TIMEOUT_MS = 15_000;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static boolean sBusy;

    private AppUpdater() {
    }

    public static boolean isAutoCheckEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AUTO, true);
    }

    public static boolean isBetaEnabled(Context context) {
        return prefs(context).getBoolean(KEY_BETA, false);
    }

    public static void setBetaEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_BETA, enabled).apply();
    }

    /** Production only: the latest release. With the beta channel: the newest release (beta or not). */
    private static JSONObject fetchRelease(Context context) throws Exception {
        if (!isBetaEnabled(context)) {
            return new JSONObject(readText(RELEASES_URL));
        }

        JSONArray list = new JSONArray(readText(RELEASES_LIST_URL));
        for (int i = 0; i < list.length(); i++) {
            JSONObject release = list.optJSONObject(i);
            if (release != null && !release.optBoolean("draft", false)) {
                return release;
            }
        }

        return new JSONObject();
    }

    public static void setAutoCheckEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO, enabled).apply();
    }

    /** At start-up: look for a newer version, at most every 12 hours, and only ask if there is one. */
    public static void checkInBackground(Context context) {
        if (context == null || !isAutoCheckEnabled(context)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - prefs(context).getLong(KEY_LAST_CHECK, 0) < CHECK_EVERY_MS) {
            return;
        }

        check(context.getApplicationContext(), false);
    }

    /** From Settings > About: look now and always say what was found. */
    public static void checkNow(Context context) {
        if (context != null) {
            check(context.getApplicationContext(), true);
        }
    }

    // ------------------------------------------------------------------ check

    private static void check(final Context context, final boolean userAsked) {
        if (sBusy) {
            return;
        }
        sBusy = true;

        if (userAsked) {
            MessageHelpers.showMessage(context, R.string.update_in_progress);
        }

        new Thread(() -> {
            try {
                JSONObject release = fetchRelease(context);
                prefs(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();

                String tag = release.optString("tag_name", "");
                String installed = AppInfoHelpers.getAppVersionName(context);

                if (compareVersions(tag, installed) <= 0) {
                    if (userAsked) {
                        sHandler.post(() -> MessageHelpers.showLongMessage(context, R.string.update_not_found));
                    }
                    return;
                }

                String url = pickAsset(release.optJSONArray("assets"));
                if (url == null) {
                    throw new IllegalStateException("No APK in release " + tag);
                }

                final String version = tag.startsWith("v") ? tag.substring(1) : tag;
                sHandler.post(() -> askToUpdate(context, version, url));
            } catch (Exception e) {
                Log.e(TAG, "Update check error: %s", e.getMessage());
                if (userAsked) {
                    sHandler.post(() -> MessageHelpers.showLongMessage(context, R.string.younew_update_error));
                }
            } finally {
                sBusy = false;
            }
        }, "younew-update-check").start();
    }

    /** The file for this device: the first CPU type it supports (64-bit or 32-bit ARM), else the universal one. */
    private static String pickAsset(JSONArray assets) {
        if (assets == null) {
            return null;
        }

        String[] abis = Build.VERSION.SDK_INT >= 21 ? Build.SUPPORTED_ABIS : new String[]{Build.CPU_ABI};
        String universal = null;

        for (String abi : abis) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset != null && ("YouTvPlus-" + abi + ".apk").equals(asset.optString("name"))) {
                    return asset.optString("browser_download_url", null);
                }
            }
        }

        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && "YouTvPlus.apk".equals(asset.optString("name"))) {
                universal = asset.optString("browser_download_url", null);
            }
        }

        return universal;
    }

    /** "v32.84" against "32.83": a positive number when the first is newer. */
    static int compareVersions(String a, String b) {
        int[] x = parseVersion(a);
        int[] y = parseVersion(b);

        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int left = i < x.length ? x[i] : 0;
            int right = i < y.length ? y[i] : 0;
            if (left != right) {
                return left > right ? 1 : -1;
            }
        }

        return 0;
    }

    private static int[] parseVersion(String version) {
        if (version == null) {
            return new int[0];
        }

        String[] parts = version.replaceAll("[^0-9.]", "").split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                numbers[i] = 0;
            }
        }
        return numbers;
    }

    // ------------------------------------------------------------------ ask

    private static void askToUpdate(final Context context, final String version, final String url) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.younew_update_now, version), option -> {
            dialog.closeDialog();
            download(context, version, url);
        }));
        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.younew_update_later), option -> dialog.closeDialog()));
        dialog.showDialog(context.getString(R.string.younew_update_available, version));
    }

    // ------------------------------------------------------------------ download and install

    private static void download(final Context context, final String version, final String url) {
        if (sBusy) {
            return;
        }
        sBusy = true;

        sHandler.post(() -> MessageHelpers.showLongMessage(context, context.getString(R.string.younew_update_downloading, 0)));

        new Thread(() -> {
            File file = null;
            try {
                file = updateFile(context);
                if (file.exists()) {
                    file.delete();
                }

                downloadTo(context, url, file);

                if (file.length() < 1_000_000) {
                    throw new IllegalStateException("Downloaded file is too small: " + file.length());
                }

                if (!sameKey(context, file)) {
                    sHandler.post(() -> MessageHelpers.showLongMessage(context, R.string.younew_update_other_key));
                    return;
                }

                final File apk = file;
                sHandler.post(() -> install(context, apk));
            } catch (Exception e) {
                Log.e(TAG, "Update download error: %s", e.getMessage());
                if (file != null) {
                    file.delete();
                }
                sHandler.post(() -> MessageHelpers.showLongMessage(context, R.string.younew_update_error));
            } finally {
                sBusy = false;
            }
        }, "younew-update-download").start();
    }

    private static File updateFile(Context context) {
        File base = context.getExternalCacheDir(); // readable by the installer on Android 5-6
        if (base == null) {
            base = context.getCacheDir();
        }

        File dir = new File(base, "updates");
        dir.mkdirs();
        return new File(dir, "YouTvPlus-update.apk");
    }

    private static void downloadTo(Context context, String url, File target) throws Exception {
        HttpURLConnection connection = open(url);
        try {
            long total = connection.getContentLength(); // -1 when unknown
            int lastShown = 0;
            long done = 0;

            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    done += read;

                    if (total > 0) {
                        int percent = (int) (done * 100 / total);
                        if (percent >= lastShown + 25) { // 25%, 50%, 75%
                            lastShown = percent / 25 * 25;
                            final int shown = lastShown;
                            sHandler.post(() -> MessageHelpers.showMessage(context, context.getString(R.string.younew_update_downloading, shown)));
                        }
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Android only accepts an update signed with the same key as the installed app.
     * Returns false when the keys differ (then the installer would just say "App not installed").
     * If the file's key cannot be read, it returns true and lets the installer decide.
     */
    @SuppressWarnings("deprecation")
    private static boolean sameKey(Context context, File apk) {
        try {
            PackageManager pm = context.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
            PackageInfo downloaded = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);

            Signature[] mine = signaturesOf(installed);
            Signature[] theirs = signaturesOf(downloaded);

            if (mine == null || theirs == null || mine.length == 0 || theirs.length == 0) {
                return true;
            }

            for (Signature signature : theirs) {
                for (Signature own : mine) {
                    if (own.equals(signature)) {
                        return true;
                    }
                }
            }

            return false;
        } catch (Exception e) {
            Log.e(TAG, "Key check error: %s", e.getMessage());
            return true;
        }
    }

    @SuppressWarnings("deprecation")
    private static Signature[] signaturesOf(PackageInfo info) {
        if (info == null) {
            return null;
        }

        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            return info.signingInfo.getApkContentsSigners();
        }

        return info.signatures;
    }

    private static void install(Context context, File apk) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
                // Android 8+: the user must allow this app to install apps, once
                MessageHelpers.showLongMessage(context, R.string.younew_update_allow);
                Intent allow = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.getPackageName()));
                allow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(allow);
                return;
            }

            Uri uri = Build.VERSION.SDK_INT >= 24
                    ? FileProvider.getUriForFile(context, context.getPackageName() + ".updates", apk)
                    : Uri.fromFile(apk);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Install error: %s", e.getMessage());
            MessageHelpers.showLongMessage(context, R.string.younew_update_error);
        }
    }

    // ------------------------------------------------------------------ network

    private static String readText(String url) throws Exception {
        HttpURLConnection connection = open(url);
        try (InputStream in = connection.getInputStream(); Scanner scanner = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "YouTvPlus-updater");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        return connection;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
