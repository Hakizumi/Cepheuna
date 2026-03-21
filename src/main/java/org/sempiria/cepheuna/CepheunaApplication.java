package org.sempiria.cepheuna;

import lombok.extern.slf4j.Slf4j;
import org.sempiria.cepheuna.utils.PlatformUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

import java.nio.file.Path;

/**
 * Project introduce:
 * This is a chat-agent springboot project,
 * achieved the sound acquisition/detection pause --LLM for agent-audio model output audio.
 * <p>
 * The disadvantage is that the output of audio will be delayed by 1-2 seconds compared to real realtime,
 * because conversation to speech will cost several seconds.
 *
 * @since 1.0.0
 * @version 1.3.1
 * @author Sempiria
 */
@SpringBootApplication
@ConfigurationPropertiesScan("org.sempiria.cepheuna")
@EnableWebSocket
@Slf4j
public class CepheunaApplication {
    public static void main(String[] args) {
        preloadNativeLibs();
        SpringApplication.run(CepheunaApplication.class, args);
    }

    private static void preloadNativeLibs() {
        try {
            String platform = PlatformUtil.getPlatform();

            String platformLibPath = switch (platform) {
                case "win-x64" -> "windows_x64";
                case "mac-x64","linux-x64" -> "mac_linux_x64";
                case "mac-aarch64","linux-aarch64" -> "mac_linux_aarch64";

                default -> throw new IllegalStateException("Unsupported platform: " + platform);
            };

            Path base = Path.of("lib", platformLibPath).toAbsolutePath().normalize();

            Path ort;
            Path sherpa;

            switch (platform) {
                case "win-x64" -> {
                   ort = base.resolve("onnxruntime.dll");
                   sherpa = base.resolve("sherpa-onnx-jni.dll");
                }
                case "mac-x64","mac-aarch64" -> {
                    ort = base.resolve("libonnxruntime.1.23.2.dylib");
                    sherpa = base.resolve("libsherpa-onnx-jni.dylib");
                }
                case "linux-x64","linux-aarch64" -> {
                    ort = base.resolve("libonnxruntime.so");
                    sherpa = base.resolve("libsherpa-onnx-jni.so");
                }
                default -> throw new IllegalStateException("Unsupported platform: " + platform);
            }

            log.info("Preloading native libs from: {}", base);

            System.load(ort.toString());
            System.load(sherpa.toString());

            log.info("Native libs loaded.");
        } catch (Throwable e) {
            throw new RuntimeException("Failed to preload native libs", e);
        }
    }
}
