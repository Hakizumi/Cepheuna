package org.hakizumi.cepheuna.utils;

import org.jspecify.annotations.NonNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Utility class about {@link Path} and {@link File}.
 *
 * @since 1.1.0
 * @author Hakizumi
 *
 * @see Path
 * @see File
 * @see Files
 */
public class PathUtils {
    /** Utility class */
    private PathUtils() {}

    /**
     * Ensure a path is existing and is a regular file.
     *
     * @param path Target path
     *
     * @throws IllegalStateException If the target path is not existing or is not a regular file.
     *
     * @since 1.1.0
     *
     * @see Files#exists(Path, LinkOption...)
     * @see Files#isRegularFile(Path, LinkOption...)
     */
    public static void ensureRegularFile(@NonNull Path path) {
        if (!Files.exists(path)) {
            throw new IllegalStateException("Path does not exist: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Path is not a regular file: " + path);
        }
    }

    /**
     * Ensure a path is existing and is a directory.
     *
     * @param path Target path
     *
     * @throws IllegalStateException If the target path is not existing or is not a directory.
     *
     * @since 1.1.0
     *
     * @see Files#exists(Path, LinkOption...)
     * @see Files#isDirectory(Path, LinkOption...)
     */
    public static void ensureDirectory(@NonNull Path path) {
        if (!Files.exists(path)) {
            throw new IllegalStateException("Directory does not exist: " + path);
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException("Path is not a directory: " + path);
        }
    }
}
