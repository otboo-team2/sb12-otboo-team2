package com.otboo.clothes.extraction;

import com.sun.jna.Native;
import java.io.File;
import java.io.IOException;
import org.opencv.core.Core;

final class OpenCvNativeLibraryLoader {

    private static boolean loaded;

    private OpenCvNativeLibraryLoader() {}

    static synchronized void load() {
        if (loaded) {
            return;
        }

        try {
            File nativeLibrary = Native.extractFromResourcePath(Core.NATIVE_LIBRARY_NAME);
            System.load(nativeLibrary.getAbsolutePath());
            loaded = true;
        } catch (IOException exception) {
            UnsatisfiedLinkError error = new UnsatisfiedLinkError(
                    "Failed to extract the bundled OpenCV native library");
            error.initCause(exception);
            throw error;
        }
    }
}
