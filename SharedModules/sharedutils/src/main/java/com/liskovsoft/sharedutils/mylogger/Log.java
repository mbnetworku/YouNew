package com.liskovsoft.sharedutils.mylogger;

import android.content.Context;

public class Log {
    public static final String LOG_TYPE_FILE = "log_type_file";
    public static final String LOG_TYPE_SYSTEM = "log_type_system";

    private static MyLogger sLogger = new SystemLogger();

    public static void d(String tag, Object msg, Object... formatArgs) {
        if (!isVerbose()) {
            return;
        }

        sLogger.d(tag, formatMsg(msg, formatArgs));
    }

    public static void i(String tag, Object msg, Object... formatArgs) {
        if (!isVerbose()) {
            return;
        }

        sLogger.i(tag, formatMsg(msg, formatArgs));
    }

    public static void w(String tag, Object msg, Object... formatArgs) {
        sLogger.w(tag, formatMsg(msg, formatArgs));
    }

    public static void e(String tag, Object msg, Object... formatArgs) {
        sLogger.e(tag, formatMsg(msg, formatArgs));
    }

    public static void i(String tag, Object msg, Throwable ex) {
        i(tag, msg + " " + ex.getMessage());
    }

    public static void e(String tag, Object msg, Throwable ex) {
        if (msg != null && ex != null) {
            e(tag, msg + " " + ex.getMessage());
        }
    }

    public static void d(String tag, Object msg, Throwable ex) {
        d(tag, msg + " " + ex.getMessage());
    }

    public static void w(String tag, Object msg, Throwable ex) {
        w(tag, msg + " " + ex.getMessage());
    }

    /**
     * In case of file, flushes all data to disk
     */
    public static void flush() {
        sLogger.flush();
    }

    public static void init(Context context, String logType, String customLabel) {
        if (sLogger.getLogType().equals(logType)) {
            return;
        }

        switch (logType) {
            case LOG_TYPE_FILE:
                sLogger = new FileLogger(context, customLabel);
                break;
            case LOG_TYPE_SYSTEM:
                sLogger = new SystemLogger();
                break;
        }
    }

    public static String getLogType() {
        if (sLogger != null) {
            return sLogger.getLogType();
        }

        return LOG_TYPE_SYSTEM;
    }

    /**
     * YouNew: debug and info lines are only produced when file logging is on (for diagnostics).
     * Formatting thousands of them costs real CPU time on a slow TV box, and nobody reads them in normal use.
     */
    private static boolean isVerbose() {
        return !LOG_TYPE_SYSTEM.equals(sLogger.getLogType());
    }

    private static String formatMsg(Object msg, Object... formatArgs) {
        String result = null;

        if (msg != null && formatArgs != null && formatArgs.length > 0) {
            result = String.format(msg.toString(), formatArgs);
        } else if (msg != null) {
            result = msg.toString();
        }

        return result;
    }
}
