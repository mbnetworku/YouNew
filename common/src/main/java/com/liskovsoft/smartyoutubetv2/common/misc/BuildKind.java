package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

/**
 * YouNew: which build of the app this is. The same source makes three kinds of file:
 * the normal (legacy) build, the "modern" build (Android 8+), and the "Fast" test build (own app id, installs next to
 * the others; shrunk code, faster video start, no self-update). The app module sets the bool resources.
 */
public final class BuildKind {
    private BuildKind() {
    }

    public static boolean isModern(Context context) {
        return flag(context, "younew_modern");
    }

    public static boolean isFast(Context context) {
        return flag(context, "younew_fast");
    }

    private static boolean flag(Context context, String name) {
        try {
            int id = context.getResources().getIdentifier(name, "bool", context.getPackageName());
            return id != 0 && context.getResources().getBoolean(id);
        } catch (Exception e) {
            return false;
        }
    }
}
