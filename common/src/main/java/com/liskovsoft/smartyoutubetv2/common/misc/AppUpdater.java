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
    private static volatile boolean sBusy; // set on the main thread, cleared on a worker

    // YouNew: what the update is doing now, for the progress bar in Settings > About
    public static final int STATE_IDLE = 0;
    public static final int STATE_CHECKING = 1;
    public static final int STATE_DOWNLOADING = 2;
    public static final int STATE_INSTALLING = 3;
    public static final int STATE_FAILED = 4;
    public static final int STATE_UP_TO_DATE = 5;
    /** A newer version was found from Settings > About: the panel asks "Update now / Later" in place (no extra screen). */
    public static final int STATE_AVAILABLE = 6;

    /** Told on the main thread each time the state or the percent changes; read the values with the getters. */
    public interface ProgressListener {
        void onUpdateProgress();
    }

    private static final java.util.List<ProgressListener> sListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static volatile int sState = STATE_IDLE;
    private static volatile int sPercent = -1; // -1: size unknown
    private static volatile String sVersion = "";
    private static volatile String sUrl = "";

    private AppUpdater() {
    }

    public static int getState() {
        return sState;
    }

    /** 0..100 while downloading, -1 when the size is not known. */
    public static int getPercent() {
        return sPercent;
    }

    public static String getVersion() {
        return sVersion;
    }

    /** "Update now" in Settings > About: downloads the version that was found, then opens the installer. */
    public static void startUpdate(Context context) {
        if (context == null || sUrl.isEmpty()) {
            return;
        }

        Context app = context.getApplicationContext();
        if (askForInstallPermission(app)) { // ask before the download, not after it
            setProgress(STATE_IDLE, -1);
            return;
        }
        download(app, sVersion, sUrl);
    }

    /** "Later" in Settings > About. */
    public static void dismissUpdate() {
        setProgress(STATE_IDLE, -1);
    }

    public static void addProgressListener(ProgressListener listener) {
        if (listener != null && !sListeners.contains(listener)) {
            sListeners.add(listener);
        }
    }

    public static void removeProgressListener(ProgressListener listener) {
        sListeners.remove(listener);
    }

    private static void setProgress(int state, int percent) {
        if (sState == state && sPercent == percent) {
            return;
        }

        sState = state;
        sPercent = percent;

        sHandler.post(() -> {
            for (ProgressListener listener : sListeners) {
                listener.onUpdateProgress();
            }
        });
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
        if (context == null || !isAutoCheckEnabled(context) || BuildKind.isFast(context)) { // the Fast test build never updates itself
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
        if (context != null && BuildKind.isFast(context)) {
            MessageHelpers.showLongMessage(context, R.string.younew_fast_no_update);
            return;
        }

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
            setProgress(STATE_CHECKING, -1);
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
                        setProgress(STATE_UP_TO_DATE, -1);
                        sHandler.post(() -> MessageHelpers.showLongMessage(context, R.string.update_not_found));
                    }
                    return;
                }

                String url = pickAsset(context, release.optJSONArray("assets"));
                if (url == null) {
                    throw new IllegalStateException("No APK in release " + tag);
                }

                final String version = tag.startsWith("v") ? tag.substring(1) : tag;
                sVersion = version;
                sUrl = url;
                if (userAsked && !sListeners.isEmpty()) {
                    // Settings > About is open: ask there, in the page (a second screen had no way back with a mouse)
                    setProgress(STATE_AVAILABLE, -1);
                    return;
                }
                if (userAsked) {
                    setProgress(STATE_IDLE, -1); // the question comes next
                }
                sHandler.post(() -> askToUpdate(context, version, url));
            } catch (Exception e) {
                Log.e(TAG, "Update check error: %s", e.getMessage());
                if (userAsked) {
                    setProgress(STATE_FAILED, -1);
                    sHandler.post(() -> MessageHelpers.showLongMessage(context, R.string.younew_update_error));
                }
            } finally {
                sBusy = false;
            }
        }, "younew-update-check").start();
    }

    /** True in the "modern" build (Android 8.0 and newer): it updates to the modern files. */
    private static boolean isModernBuild(Context context) {
        return BuildKind.isModern(context);
    }

    /**
     * The file for this device: the first CPU type it supports (64-bit or 32-bit ARM), else the universal one.
     * The modern build looks for YouTvPlus-modern-* first (and falls back to the normal files of older releases);
     * the normal (legacy) build uses YouTvPlus-*.
     */
    private static String pickAsset(Context context, JSONArray assets) {
        if (assets == null) {
            return null;
        }

        String[] abis = Build.VERSION.SDK_INT >= 21 ? Build.SUPPORTED_ABIS : new String[]{Build.CPU_ABI};
        String[] prefixes = isModernBuild(context) ? new String[]{"YouTvPlus-modern-", "YouTvPlus-"} : new String[]{"YouTvPlus-"};
        String[] universals = isModernBuild(context) ? new String[]{"YouTvPlus-modern.apk", "YouTvPlus.apk"} : new String[]{"YouTvPlus.apk"};

        for (int p = 0; p < prefixes.length; p++) {
            for (String abi : abis) {
                String url = assetUrl(assets, prefixes[p] + abi + ".apk");
                if (url != null) {
                    return url;
                }
            }

            String url = assetUrl(assets, universals[p]);
            if (url != null) {
                return url;
            }
        }

        return null;
    }

    private static String assetUrl(JSONArray assets, String name) {
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && name.equals(asset.optString("name"))) {
                return asset.optString("browser_download_url", null);
            }
        }
        return null;
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
            if (askForInstallPermission(context)) {
                return; // ask before the download, not after it (the file would be fetched for nothing)
            }
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

        sVersion = version;
        setProgress(STATE_DOWNLOADING, 0);

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
                    setProgress(STATE_FAILED, -1);
                    sHandler.post(() -> MessageHelpers.showLongMessage(context, R.string.younew_update_other_key));
                    return;
                }

                final File apk = file;
                setProgress(STATE_INSTALLING, 100);
                sHandler.post(() -> install(context, apk));
            } catch (Exception e) {
                Log.e(TAG, "Update download error: %s", e.getMessage());
                if (file != null) {
                    file.delete();
                }
                setProgress(STATE_FAILED, -1);
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
            long done = 0;

            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    done += read;

                    setProgress(STATE_DOWNLOADING, total > 0 ? (int) Math.min(100, done * 100 / total) : -1);
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

    /**
     * Android 8+: the user must allow this app to install apps, once.
     * Returns true when the permission is missing and Android's page for it was opened.
     */
    private static boolean askForInstallPermission(Context context) {
        if (Build.VERSION.SDK_INT < 26 || context.getPackageManager().canRequestPackageInstalls()) {
            return false;
        }

        try {
            Intent allow = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.getPackageName()));
            allow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(allow);
        } catch (Exception e) { // some TV boxes have no such page: go on and let Android's installer ask
            Log.e(TAG, "Install permission page error: %s", e.getMessage());
            return false;
        }

        MessageHelpers.showLongMessage(context, R.string.younew_update_allow);
        return true;
    }

    private static void install(Context context, File apk) {
        try {
            if (askForInstallPermission(context)) {
                setProgress(STATE_IDLE, -1);
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
            setProgress(STATE_FAILED, -1);
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
