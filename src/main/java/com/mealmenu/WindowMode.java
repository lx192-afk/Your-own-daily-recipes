package com.mealmenu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Desktop: a normal resizable window with a sidebar. Phone: the original phone-sized frame. */
public enum WindowMode {
    DESKTOP, PHONE;

    private static Path file() {
        return UserDataPaths.applicationDirectory().resolve("window-mode");
    }

    /** Command-line --phone / --desktop wins, then the saved choice, then desktop. */
    static WindowMode resolve(List<String> args) {
        if (args.contains("--phone")) {
            return PHONE;
        }
        if (args.contains("--desktop")) {
            return DESKTOP;
        }
        try {
            return valueOf(Files.readString(file()).strip().toUpperCase(Locale.ROOT));
        } catch (IOException | IllegalArgumentException error) {
            return DESKTOP;
        }
    }

    void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), name());
        } catch (IOException ignored) {
            // Falls back to the default next launch.
        }
    }
}
