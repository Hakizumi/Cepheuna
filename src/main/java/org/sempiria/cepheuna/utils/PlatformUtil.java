package org.sempiria.cepheuna.utils;

import org.jspecify.annotations.NonNull;

/**
 * Get the system platform and cpu platform。
 * In order to load the dynamic link library in advance,
 * so that the JVM does not throw a dynamic link library fatal exception at runtime。
 *
 * @since 1.2.3
 * @version 1.0.0
 * @author Sempiria
 */
public class PlatformUtil {
    public static @NonNull String getPlatform() {
        return getOS() + "-" + getArch();
    }

    public static @NonNull String getOS() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return "win";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "mac";
        }
        if (os.contains("linux")) {
            return "linux";
        }
        return os.replaceAll("\\s+", "");
    }

    public static String getArch() {
        String arch = System.getProperty("os.arch").toLowerCase();

        return switch (arch) {
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            case "x86_64", "amd64", "x64" -> "x64";
            case "aarch64", "arm64" -> "aarch64";
            case "arm", "armv7", "armv7l" -> "arm";
            default -> arch;
        };
    }
}
