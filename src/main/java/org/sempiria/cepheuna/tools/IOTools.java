package org.sempiria.cepheuna.tools;

import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * File system tools exposed to AI agents.
 *
 * <p>This tool provides constrained file I/O operations for agent use, including:
 * <ul>
 *     <li>Reading text files</li>
 *     <li>Reading binary files as Base64</li>
 *     <li>Writing text files</li>
 *     <li>Writing binary files from Base64</li>
 *     <li>Creating directories and files</li>
 *     <li>Listing directory trees</li>
 *     <li>Checking path accessibility</li>
 * </ul>
 *
 * <p>Access control is derived automatically from configuration:
 * <ul>
 *     <li>If only whitelist is configured, only whitelisted paths are accessible.</li>
 *     <li>If only blacklist is configured, all paths except blacklisted paths are accessible.</li>
 *     <li>If neither is configured, all paths are accessible.</li>
 *     <li>If both are configured, a path must match whitelist and must not match blacklist.</li>
 * </ul>
 *
 * <p>The special configured path {@code /} means "all paths".</p>
 *
 * <p>All input paths are normalized to absolute paths before access checks to reduce
 * path traversal issues.</p>
 *
 * @since 1.1.0
 * @author Sempiria
 */
@Component
public class IOTools implements AgentTool {

    private final @NonNull List<Path> whitelistPaths;
    private final @NonNull List<Path> blacklistPaths;
    private final boolean whitelistAll;
    private final boolean blacklistAll;

    /**
     * Creates a new IO tools instance with auto-derived access policy.
     *
     * @throws IllegalArgumentException if duplicate configured paths exist
     */
    public IOTools(IOToolsProperties props) {
        List<String> safeWhitelist = props.whitelist == null ? List.of() : props.whitelist;
        List<String> safeBlacklist = props.blacklist == null ? List.of() : props.blacklist ;

        checkDuplicateRawConfig(safeWhitelist, "whitelist-paths");
        checkDuplicateRawConfig(safeBlacklist, "blacklist-paths");

        this.whitelistAll = safeWhitelist.stream().map(String::trim).anyMatch("/"::equals);
        this.blacklistAll = safeBlacklist.stream().map(String::trim).anyMatch("/"::equals);

        this.whitelistPaths = normalizeRoots(safeWhitelist);
        this.blacklistPaths = normalizeRoots(safeBlacklist);

        checkDuplicateNormalizedConfig(this.whitelistPaths, "whitelist-paths");
        checkDuplicateNormalizedConfig(this.blacklistPaths, "blacklist-paths");
        checkCrossDuplicates(this.whitelistPaths, this.blacklistPaths);
    }

    /**
     * Reads a text file using UTF-8 by default.
     *
     * @param filePath target file path
     * @param charsetName optional charset name, defaults to UTF-8 if null or blank
     * @return file content as text
     */
    @Tool(name = "read_file_text", description = "Read a text file from an accessible path.")
    public @NonNull String readFileText(
            @ToolParam(description = "Path to the text file") @NonNull String filePath,
            @ToolParam(description = "Optional charset name such as UTF-8 or GBK", required = false) @Nullable String charsetName
    ) {
        Path path = resolveAndCheck(filePath);
        ensureRegularFile(path);

        Charset charset = parseCharsetOrDefault(charsetName);
        try {
            return Files.readString(path, charset);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read text file: " + path + ", reason: " + e.getMessage(), e);
        }
    }

    /**
     * Reads a binary file and returns its content as a Base64 string.
     *
     * @param filePath target file path
     * @return Base64-encoded file content
     */
    @Tool(name = "read_file_binary", description = "Read a binary file from an accessible path and return Base64 content.")
    public @NonNull String readFileBinary(
            @ToolParam(description = "Path to the binary file") @NonNull String filePath
    ) {
        Path path = resolveAndCheck(filePath);
        ensureRegularFile(path);

        try {
            return Base64.getEncoder().encodeToString(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read binary file: " + path + ", reason: " + e.getMessage(), e);
        }
    }

    /**
     * Writes text content to a file.
     *
     * @param filePath target file path
     * @param content text content
     * @param overwrite whether to overwrite when the file already exists
     * @param charsetName optional charset name, defaults to UTF-8 if null or blank
     * @return success message
     */
    @Tool(name = "write_file_text", description = "Write text content to a file under an accessible path.")
    public @NonNull String writeFileText(
            @ToolParam(description = "Path to the text file to write") @NonNull String filePath,
            @ToolParam(description = "Text content to write") @NonNull String content,
            @ToolParam(description = "Whether to overwrite an existing file") boolean overwrite,
            @ToolParam(description = "Optional charset name such as UTF-8 or GBK", required = false) @Nullable String charsetName
    ) {
        Path path = resolveAndCheck(filePath);
        Charset charset = parseCharsetOrDefault(charsetName);

        try {
            createParentDirectoriesIfNecessary(path);

            if (Files.exists(path) && !overwrite) {
                throw new IllegalStateException("File already exists and overwrite=false: " + path);
            }

            Files.writeString(path, content, charset);
            return "Text file written successfully: " + path;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write text file: " + path + ", reason: " + e.getMessage(), e);
        }
    }

    /**
     * Writes binary content to a file from a Base64 string.
     *
     * @param filePath target file path
     * @param base64Content Base64-encoded binary content
     * @param overwrite whether to overwrite when the file already exists
     * @return success message
     */
    @Tool(name = "write_file_binary", description = "Write Base64-encoded binary content to a file under an accessible path.")
    public @NonNull String writeFileBinary(
            @ToolParam(description = "Path to the binary file to write") @NonNull String filePath,
            @ToolParam(description = "Base64-encoded binary content") String base64Content,
            @ToolParam(description = "Whether to overwrite an existing file") boolean overwrite
    ) {
        Path path = resolveAndCheck(filePath);

        try {
            createParentDirectoriesIfNecessary(path);

            if (Files.exists(path) && !overwrite) {
                throw new IllegalStateException("File already exists and overwrite=false: " + path);
            }

            byte[] bytes = Base64.getDecoder().decode(base64Content);
            Files.write(path, bytes);
            return "Binary file written successfully: " + path;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid Base64 content for file: " + path, e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write binary file: " + path + ", reason: " + e.getMessage(), e);
        }
    }

    /**
     * Lists a directory tree up to the specified depth.
     *
     * @param dirPath target directory path
     * @param maxDepth max traversal depth, must be greater than or equal to 1
     * @return human-readable directory tree text
     */
    @Tool(name = "tree_dir", description = "List a directory tree for an accessible folder.")
    public @NonNull String treeDir(
            @ToolParam(description = "Path to the directory") @NonNull String dirPath,
            @ToolParam(description = "Maximum traversal depth, minimum is 1") int maxDepth
    ) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be >= 1");
        }

        Path root = resolveAndCheck(dirPath);
        ensureDirectory(root);

        StringBuilder sb = new StringBuilder();
        sb.append(root).append(System.lineSeparator());

        try (Stream<Path> stream = Files.walk(root, maxDepth)) {
            List<Path> paths = stream
                    .filter((p) -> !p.equals(root))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();

            for (@NonNull Path p : paths) {
                Path relative = root.relativize(p);
                int depth = relative.getNameCount();
                sb.append("  ".repeat(depth))
                        .append(Files.isDirectory(p) ? "[D] " : "[F] ")
                        .append(relative)
                        .append(System.lineSeparator());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list directory tree: " + root + ", reason: " + e.getMessage(), e);
        }

        return sb.toString();
    }

    /**
     * Creates a directory and all missing parent directories.
     *
     * @param dirPath target directory path
     * @return success message
     */
    @Tool(name = "make_dir", description = "Create a directory under an accessible path.")
    public @NonNull String makeDir(
            @ToolParam(description = "Path of the directory to create") @NonNull String dirPath
    ) {
        Path path = resolveAndCheck(dirPath);

        try {
            Files.createDirectories(path);
            return "Directory created successfully: " + path;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create directory: " + path + ", reason: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an empty file.
     *
     * @param filePath target file path
     * @param overwrite whether to truncate if the file already exists
     * @return success message
     */
    @Tool(name = "make_file", description = "Create an empty file under an accessible path.")
    public @NonNull String makeFile(
            @ToolParam(description = "Path of the file to create") @NonNull String filePath,
            @ToolParam(description = "Whether to overwrite an existing file") boolean overwrite
    ) {
        Path path = resolveAndCheck(filePath);

        try {
            createParentDirectoriesIfNecessary(path);

            if (Files.exists(path)) {
                if (!overwrite) {
                    return "File already exists: " + path;
                }
                Files.write(path, new byte[0]);
                return "Existing file truncated successfully: " + path;
            }

            Files.createFile(path);
            return "File created successfully: " + path;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create file: " + path + ", reason: " + e.getMessage(), e);
        }
    }

    /**
     * Checks whether a path is accessible under the derived access policy.
     *
     * @param filePath target path
     * @return true if accessible, otherwise false
     */
    @Tool(name = "is_accessible", description = "Check whether a path is accessible according to the configured whitelist and blacklist.")
    public boolean isAccessible(
            @ToolParam(description = "Path to validate") @NonNull String filePath
    ) {
        try {
            Path path = normalizePath(filePath);
            return isAccessible(path);
        } catch (Exception e) {
            return false;
        }
    }

    private @NonNull List<Path> normalizeRoots(@NonNull List<String> roots) {
        return roots.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter((s) -> !s.isEmpty())
                .filter((s) -> !"/".equals(s))
                .map(this::normalizePath)
                .distinct()
                .toList();
    }

    private void checkDuplicateRawConfig(@NonNull List<String> rawPaths, String fieldName) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();

        for (@Nullable String raw : rawPaths) {
            if (raw == null) {
                continue;
            }
            String value = raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!seen.add(value)) {
                duplicates.add(value);
            }
        }

        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate entries found in " + fieldName + ": " + duplicates);
        }
    }

    private void checkDuplicateNormalizedConfig(@NonNull List<Path> paths, String fieldName) {
        Set<Path> seen = new LinkedHashSet<>();
        Set<Path> duplicates = new LinkedHashSet<>();

        for (Path path : paths) {
            if (!seen.add(path)) {
                duplicates.add(path);
            }
        }

        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate normalized entries found in " + fieldName + ": " + duplicates);
        }
    }

    private void checkCrossDuplicates(@NonNull List<Path> whitelist, @NonNull List<Path> blacklist) {
        Set<Path> intersection = new LinkedHashSet<>(whitelist);
        intersection.retainAll(new LinkedHashSet<>(blacklist));

        if (!intersection.isEmpty()) {
            throw new IllegalArgumentException("Duplicate paths found in both whitelist-paths and blacklist-paths: " + intersection);
        }

        if (whitelistAll && blacklistAll) {
            throw new IllegalArgumentException("Path '/' cannot appear in both whitelist-paths and blacklist-paths at the same time");
        }
    }

    private @NonNull Path resolveAndCheck(@NonNull String rawPath) {
        Path path = normalizePath(rawPath);
        if (!isAccessible(path)) {
            throw new SecurityException("Access denied for path: " + path);
        }
        return path;
    }

    private @NonNull Path normalizePath(@NonNull String rawPath) {
        try {
            return Paths.get(rawPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid path: " + rawPath, e);
        }
    }

    /**
     * Returns whether the given normalized path is accessible.
     *
     * <p>Derived policy:
     * <ul>
     *     <li>No whitelist and no blacklist: allow all</li>
     *     <li>Whitelist only: must match whitelist</li>
     *     <li>Blacklist only: must not match blacklist</li>
     *     <li>Both configured: must match whitelist and must not match blacklist</li>
     * </ul>
     *
     * @param path normalized absolute path
     * @return true if accessible
     */
    public boolean isAccessible(@NonNull Path path) {
        boolean hasWhitelist = whitelistAll || !whitelistPaths.isEmpty();
        boolean hasBlacklist = blacklistAll || !blacklistPaths.isEmpty();

        if (!hasWhitelist && !hasBlacklist) {
            return true;
        }

        boolean whitelistMatched = whitelistAll || whitelistPaths.stream().anyMatch(path::startsWith);
        boolean blacklistMatched = blacklistAll || blacklistPaths.stream().anyMatch(path::startsWith);

        if (hasWhitelist && hasBlacklist) {
            return whitelistMatched && !blacklistMatched;
        }

        if (hasWhitelist) {
            return whitelistMatched;
        }

        return !blacklistMatched;
    }

    private void ensureRegularFile(@NonNull Path path) {
        if (!Files.exists(path)) {
            throw new IllegalStateException("Path does not exist: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Path is not a regular file: " + path);
        }
    }

    private void ensureDirectory(@NonNull Path path) {
        if (!Files.exists(path)) {
            throw new IllegalStateException("Directory does not exist: " + path);
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException("Path is not a directory: " + path);
        }
    }

    private void createParentDirectoriesIfNecessary(@NonNull Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            if (!isAccessible(parent)) {
                throw new SecurityException("Access denied for parent path: " + parent);
            }
            Files.createDirectories(parent);
        }
    }

    private @NonNull Charset parseCharsetOrDefault(@Nullable String charsetName) {
        if (charsetName == null || charsetName.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(charsetName);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unsupported charset: " + charsetName, e);
        }
    }

    @ConfigurationProperties("cepheuna.tools.io-tools")
    @Setter
    public static class IOToolsProperties {
        private @Nullable List<String> whitelist = new ArrayList<>();
        private @Nullable List<String> blacklist = new ArrayList<>();
    }
}
