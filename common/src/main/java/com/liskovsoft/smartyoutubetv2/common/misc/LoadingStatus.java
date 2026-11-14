package com.liskovsoft.smartyoutubetv2.common.misc;

/**
 * YouNew: a short line the loading picture shows under its ring (for example "Trying again... (2 of 8)").
 * When nothing is set, the picture shows its own "Loading..." text, which changes to a slow-connection hint when
 * the wait gets long. Whoever sets a line must clear it when the wait is over.
 */
public final class LoadingStatus {
    private static volatile String sText;

    private LoadingStatus() {
    }

    public static void set(String text) {
        sText = text;
    }

    public static void clear() {
        sText = null;
    }

    public static String get() {
        return sText;
    }
}
