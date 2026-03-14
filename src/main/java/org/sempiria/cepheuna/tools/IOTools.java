package org.sempiria.cepheuna.tools;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File system IO tools exposed to AI agents through Spring AI tool calling.
 *
 * <p>This tool supports:
 * <ul>
 *     <li>Reading and writing text files</li>
 *     <li>Reading and writing binary files as Base64 strings</li>
 *     <li>Creating directories and files</li>
 *     <li>Listing directory trees</li>
 *     <li>Path accessibility checks through whitelist/blacklist strategies</li>
 * </ul>
 *
 * <p><b>Security model:</b>
 * <ul>
 *     <li>All paths are normalized to absolute paths before access checks</li>
 *     <li>Access is controlled by {@link IOMode}</li>
 *     <li>In whitelist mode, only configured paths are allowed</li>
 *     <li>In blacklist mode, all paths are allowed except blocked paths</li>
 * </ul>
 *
 * <p><b>Binary convention:</b>
 * Binary content is exchanged as Base64-encoded strings to remain tool-call friendly.
 *
 * <p><b>Recommended configuration example:</b>
 * <pre>{@code
 * cepheuna.tools.io-tools.io-mode=WHITELIST
 * cepheuna.tools.io-tools.whitelist-paths=/data/agent,/tmp/agent
 * cepheuna.tools.io-tools.blacklist-paths=/etc,/root,/var/lib
 * }</pre>
 */
@Component
public class IOTools implements AgentTool {
    private final List<Path> whitelistPaths;
    private final List<Path> blacklistPaths;
    private final IOMode ioMode;

    /**
     * Creates the IO tool bean with path-based access control.
     *
     * @param whitelistPaths configured allowed root paths, used in {@link IOMode#WHITELIST}
     * @param blacklistPaths configured blocked root paths, used in {@link IOMode#BLACKLIST}
     * @param ioMode access control mode
     */
    public IOTools(
            @Value("${cepheuna.tools.io-tools.whitelist-paths:}") List<String> whitelistPaths,
            @Value("${cepheuna.tools.io-tools.blacklist-paths:}") List<String> blacklistPaths,
            @Value("${cepheuna.tools.io-tools.io-mode:WHITELIST}") IOMode ioMode) {
        this.whitelistPaths = normalizePathList(whitelistPaths);
        this.blacklistPaths = normalizePathList(blacklistPaths);
        this.ioMode = ioMode == null ? IOMode.WHITELIST : ioMode;
    }

    /**
     * Reads a UTF-8 text file and returns its content.
     *
     * @param path file path to read
     * @return file content
     * @throws IllegalArgumentException if the path is not accessible
     * @throws RuntimeException if reading fails
     */
    @Tool(name = "read_file_text", description = "Read a UTF-8 text file from an accessible path.")
    public String readFileText(
            @ToolParam(description = "Absolute or relative file path to read") String path) {

        Path target = validateAccessibleFile(path, false);
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read text file: " + target + ", reason: " + e.getMessage(), e);
        }
    }

    /**
     * Reads a binary file and returns its content as a Base64 string.
     *
     * @param path file path to read
     * @return Base64-encoded binary content
     * @throws IllegalArgumentException if the path is not accessible
     * @throws RuntimeException if reading fails
     */
    @Tool(name = "read_file_binary", description = "Read a binary file from an accessible path and return Base64 content.")
    public String readFileBinary(
            @ToolParam(description = "Absolute or relative file path to read") String path) {

        Path target = validateAccessibleFile(path, false);
        try {
            byte[] bytes = Files.readAllBytes(target);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read binary file: " + target + ", reason: " + e.getMessage(), e);
        }
    }

    /**
     * Writes UTF-8 text content to a file.
     *
     * @param path target file path
     * @param content text content to write
     * @param overwrite whether to overwrite an existing file
     * @return success message
     * @throws IllegalArgumentException if the path is not accessible
     * @throws RuntimeException if writing fails
     */
    @Tool(name = "write_file_text", description = "Write UTF-8 text content to a file in an accessible path.")
    public String writeFileText(
            @ToolParam(description = "Absolute or relative target file path") String path,
            @ToolParam(description = "UTF-8 text content to write") String content,
            @ToolParam(description = "Whether to overwrite the file if it already exists") boolean overwrite) {

        Path target = validateAccessibleFile(path, true);
        ensureParentDirectoryExists(target);

        try {
            if (Files.exists(target) && !overwrite) {
                throw new IllegalArgumentException("File already exists and overwrite=false: " + target);
            }

            OpenOption[] options = overwrite
                    ? new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE}
                    : new OpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};

            Files.writeString(target, content, StandardCharsets.UTF_8, options);
            return "OK: text file written to " + target;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write text file: " + target + ", reason: " + e.getMessage(), e);
        }
    }

    /**
     * Writes binary content to a file using a Base64-encoded input string.
     *
     * @param path target file path
     * @param base64Content Base64-encoded binary content
     * @param overwrite whether to overwrite an existing file
     * @return success message
     * @throws IllegalArgumentException if the path is not accessible or Base64 is invalid
     * @throws RuntimeException if writing fails
     */
    @Tool(name = "write_file_binary", description = "Write Base64-encoded binary content to a file in an accessible path.")
    public String writeFileBinary(
            @ToolParam(description = "Absolute or relative target file path") String path,
            @ToolParam(description = "Base64-encoded binary content") String base64Content,
            @ToolParam(description = "Whether to overwrite the file if it already exists") boolean overwrite) {

        Path target = validateAccessibleFile(path, true);
        ensureParentDirectoryExists(target);

        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Content);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 content", e);
        }

        try {
            if (Files.exists(target) && !overwrite) {
                throw new IllegalArgumentException("File already exists and overwrite=false: " + target);
            }

            OpenOption[] options = overwrite
                    ? new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE}
                    : new OpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};

            Files.write(target, bytes, options);
            return "OK: binary file written to " + target;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write binary file: " + target + ", reason: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a tree-style listing of a directory up to a maximum depth.
     *
     * @param path root directory path
     * @param maxDepth maximum traversal depth, where 1 means only direct children
     * @return human-readable directory tree
     * @throws IllegalArgumentException if the path is not accessible or not a directory
     * @throws RuntimeException if traversal fails
     */
    @Tool(name = "tree_dir", description = "List a directory tree within an accessible path.")
    public String treeDir(
            @ToolParam(description = "Directory path to inspect") String path,
            @ToolParam(description = "Maximum traversal depth, minimum 1") int maxDepth) {

        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be >= 1");
        }

        Path root = resolveAndNormalize(path);
        if (!isAccessible(root)) {
            throw new IllegalArgumentException("Path is not accessible: " + root);
        }
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Path does not exist: " + root);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Path is not a directory: " + root);
        }

        try (Stream<Path> stream = Files.walk(root, maxDepth)) {
            return stream
                    .sorted()
                    .map(p -> formatTreeLine(root, p))
                    .collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to list directory tree: " + root + ", reason: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a directory and any missing parent directories.
     *
     * @param path target directory path
     * @return success message
     * @throws IllegalArgumentException if the path is not accessible
     * @throws RuntimeException if creation fails
     */
    @Tool(name = "make_dir", description = "Create a directory recursively in an accessible path.")
    public String makeDir(
            @ToolParam(description = "Directory path to create") String path) {

        Path target = resolveAndNormalize(path);
        if (!isAccessible(target)) {
            throw new IllegalArgumentException("Path is not accessible: " + target);
        }

        try {
            Files.createDirectories(target);
            return "OK: directory created at " + target;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + target + ", reason: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an empty file. Parent directories can optionally be created automatically.
     *
     * @param path target file path
     * @param createParents whether missing parent directories should be created
     * @param overwrite whether to overwrite an existing file
     * @return success message
     * @throws IllegalArgumentException if the path is not accessible
     * @throws RuntimeException if creation fails
     */
    @Tool(name = "make_file", description = "Create an empty file in an accessible path.")
    public String makeFile(
            @ToolParam(description = "File path to create") String path,
            @ToolParam(description = "Whether to create missing parent directories") boolean createParents,
            @ToolParam(description = "Whether to overwrite the file if it already exists") boolean overwrite) {

        Path target = validateAccessibleFile(path, true);

        try {
            if (createParents) {
                ensureParentDirectoryExists(target);
            }

            if (Files.exists(target)) {
                if (!overwrite) {
                    throw new IllegalArgumentException("File already exists and overwrite=false: " + target);
                }
                Files.write(target, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
                return "OK: file overwritten at " + target;
            }

            Files.createFile(target);
            return "OK: file created at " + target;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create file: " + target + ", reason: " + e.getMessage(), e);
        }
    }

    /**
     * Checks whether a path is accessible under the configured whitelist/blacklist policy.
     *
     * @param path path to check
     * @return {@code true} if accessible, otherwise {@code false}
     */
    @Tool(name = "is_accessible", description = "Check whether a path is accessible according to the configured IO access policy.")
    public boolean isAccessible(
            @ToolParam(description = "Path to check") String path) {

        return isAccessible(resolveAndNormalize(path));
    }

    /**
     * Checks whether a normalized path is accessible.
     *
     * @param normalizedPath normalized absolute path
     * @return {@code true} if the path is accessible
     */
    public boolean isAccessible(Path normalizedPath) {
        Objects.requireNonNull(normalizedPath, "normalizedPath must not be null");

        return switch (ioMode) {
            case WHITELIST -> whitelistPaths.stream().anyMatch(normalizedPath::startsWith);
            case BLACKLIST -> blacklistPaths.stream().noneMatch(normalizedPath::startsWith);
        };
    }

    private @NonNull Path validateAccessibleFile(String path, boolean allowNonExisting) {
        Path target = resolveAndNormalize(path);

        if (!isAccessible(target)) {
            throw new IllegalArgumentException("Path is not accessible: " + target);
        }

        if (!allowNonExisting && !Files.exists(target)) {
            throw new IllegalArgumentException("Path does not exist: " + target);
        }

        if (!allowNonExisting && Files.isDirectory(target)) {
            throw new IllegalArgumentException("Path is a directory, expected a file: " + target);
        }

        return target;
    }

    @Contract("null -> fail")
    private @NonNull Path resolveAndNormalize(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        return Paths.get(path).toAbsolutePath().normalize();
    }

    private void ensureParentDirectoryExists(@NonNull Path file) {
        Path parent = file.getParent();
        if (parent == null) {
            return;
        }

        if (!isAccessible(parent)) {
            throw new IllegalArgumentException("Parent path is not accessible: " + parent);
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create parent directories: " + parent, e);
        }
    }

    private List<Path> normalizePathList(List<String> rawPaths) {
        if (rawPaths == null) {
            return List.of();
        }

        return rawPaths.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter((s) -> !s.isEmpty())
                .map((p) -> Paths.get(p).toAbsolutePath().normalize())
                .distinct()
                .toList();
    }

    private @NonNull String formatTreeLine(@NonNull Path root, Path current) {
        if (root.equals(current)) {
            return current.getFileName() != null ? current.getFileName().toString() : current.toString();
        }

        Path relative = root.relativize(current);
        int depth = relative.getNameCount();
        String indent = "  ".repeat(Math.max(0, depth - 1));
        String name = current.getFileName() == null ? current.toString() : current.getFileName().toString();
        if (Files.isDirectory(current)) {
            name += "/";
        }
        return indent + "- " + name;
    }

    /**
     * IO access control mode.
     */
    public enum IOMode {
        /**
         * Only paths under configured whitelist roots are accessible.
         */
        WHITELIST,

        /**
         * All paths are accessible except those under configured blacklist roots.
         */
        BLACKLIST
    }
}
