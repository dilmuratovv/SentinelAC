package uz.sentinel.ac.compatibility;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ServerCompatibilityTest {
    @Test void supportedVersionsAreExactlyThePinnedMatrixAndOthersAreRejected() throws Exception {
        var matrix = JsonParser.parseString(Files.readString(Path.of("../tools/server-matrix.json"))).getAsJsonObject();
        var expected = new ArrayList<String>();
        for (var value : matrix.getAsJsonArray("servers")) expected.add(value.getAsJsonObject().get("version").getAsString());
        assertEquals(expected, ServerCompatibility.supportedVersions(), "JAR metadata differs from the server matrix");
        for (String version : expected) assertTrue(ServerCompatibility.supports(version), version);
        assertEquals("1.21 - 26.3", ServerCompatibility.range());
        assertFalse(ServerCompatibility.supports(null));
        assertFalse(ServerCompatibility.supports("1.20.6"));
        assertFalse(ServerCompatibility.supports("1.21.2"));
        assertFalse(ServerCompatibility.supports("26.1"));
        assertFalse(ServerCompatibility.supports("26.4"));
    }
}
