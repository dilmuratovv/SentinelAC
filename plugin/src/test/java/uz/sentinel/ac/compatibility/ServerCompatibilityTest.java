package uz.sentinel.ac.compatibility;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ServerCompatibilityTest {
    @Test void jarTargetExistsInPinnedMatrixAndRejectsOtherServers() throws Exception {
        String target = ServerCompatibility.target();
        var matrix = JsonParser.parseString(Files.readString(Path.of("../tools/server-matrix.json"))).getAsJsonObject();
        boolean found = false;
        for (var value : matrix.getAsJsonArray("servers")) {
            String version = value.getAsJsonObject().get("version").getAsString();
            assertEquals(version.equals(target), ServerCompatibility.supports(version));
            found |= version.equals(target);
        }
        assertTrue(found, "JAR target absent from the server matrix");
        assertFalse(ServerCompatibility.supports(null));
        assertFalse(ServerCompatibility.supports("1.21.2"));
        assertFalse(ServerCompatibility.supports("26.1"));
        assertFalse(ServerCompatibility.supports("26.4"));
    }
}
