package uz.sentinel.ac.compatibility;

import java.io.IOException;
import java.util.Properties;

/** Each release JAR is built and verified for one exact Paper version. */
public final class ServerCompatibility {
    private ServerCompatibility() {}
    private static final String TARGET = loadTarget();
    private static String loadTarget() {
        try (var input = ServerCompatibility.class.getResourceAsStream("/sentinel-target.properties")) {
            if (input == null) throw new IllegalStateException("Build target metadata is missing");
            var properties = new Properties(); properties.load(input);
            String target = properties.getProperty("minecraft.version");
            if (target == null || target.isBlank() || target.contains("${"))
                throw new IllegalStateException("Build target metadata is invalid");
            return target;
        } catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }
    public static String target() { return TARGET; }
    public static boolean supports(String version) { return TARGET.equals(version); }
}
