package com.mealmenu;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Crash-safe writes for the app's local data files. */
final class SafeFiles {

    private SafeFiles() {
    }

    /**
     * Writes to a temporary file next to the target, then swaps it in, so a crash or
     * full disk mid-write leaves the previous file intact instead of a truncated one.
     */
    static void storeProperties(Path target, Properties properties, String comment) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temp)) {
            properties.store(output, comment);
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
