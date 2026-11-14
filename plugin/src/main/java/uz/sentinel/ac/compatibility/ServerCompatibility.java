package uz.sentinel.ac.compatibility;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * One JAR serves every published Paper version in {@code minecraft.versions}. It is compiled against the
 * oldest of them, and CI links it against and boots it on each one before a release. Versions that Paper
 * never published (such as 1.21.2 or 26.1) and versions newer than the list are refused at startup.
 */
public final class ServerCompatibility {
    private ServerCompatibility() {}
    private static final List<String> SUPPORTED = loadSupported();
    private static List<String> loadSupported() {
        try (var input = ServerCompatibility.class.getResourceAsStream("/sentinel-target.properties")) {
            if (input == null) throw new IllegalStateException("Build target metadata is missing");
            var properties = new Properties(); properties.load(input);
            String versions = properties.getProperty("minecraft.versions");
            if (versions == null || versions.isBlank() || versions.contains("${"))
                throw new IllegalStateException("Build target metadata is invalid");
            return List.of(versions.trim().split("\\s*,\\s*"));
        } catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }
    /** Every Minecraft version this JAR supports, oldest first. */
    public static List<String> supportedVersions() { return SUPPORTED; }
    public static boolean supports(String version) { return version != null && SUPPORTED.contains(version); }
    /** Human readable range for messages, e.g. "1.21 - 26.3". */
    public static String range() { return SUPPORTED.get(0) + " - " + SUPPORTED.get(SUPPORTED.size() - 1); }
}
