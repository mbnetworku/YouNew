package com.liskovsoft.smartyoutubetv2.common.misc;

import androidx.core.content.FileProvider;

/**
 * YouNew: hands the downloaded update file to Android's installer (see AppUpdater).
 * It is a class of its own because another library in the app already declares the plain FileProvider,
 * and two providers with the same name cannot be merged into the manifest.
 */
public class UpdateFileProvider extends FileProvider {
}
