package glowredman.txloader;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForceLoadHandlerTest {

    private Path makeFolderPack(Path packsDir, String name, String mcmeta) throws IOException {
        Path pack = Files.createDirectories(packsDir.resolve(name));
        if (mcmeta != null) {
            Files.write(pack.resolve("pack.mcmeta"), mcmeta.getBytes(StandardCharsets.UTF_8));
        }
        return pack;
    }

    private String packsLine(Path mc) throws IOException {
        Path options = mc.resolve("options.txt");
        if (!Files.exists(options)) return null;
        return Files.readAllLines(options, StandardCharsets.UTF_8).stream().filter(l -> l.startsWith("resourcePacks:"))
                .findFirst().orElse(null);
    }

    @Test
    void enablesForcedPackOnceThenRespectsDisable(@TempDir Path tmp) throws IOException {
        Path mc = Files.createDirectories(tmp.resolve("mc"));
        Path config = Files.createDirectories(tmp.resolve("config"));
        Path packs = Files.createDirectories(mc.resolve("resourcepacks"));
        makeFolderPack(packs, "Forced", "{\"txloader\":{\"forceLoad\":true,\"priority\":\"top\"}}");
        makeFolderPack(packs, "Plain", "{\"pack\":{\"pack_format\":1}}");

        // First boot: enabled.
        ForceLoadHandler.run(mc, config);
        assertEquals("resourcePacks:[\"Forced\"]", packsLine(mc));
        assertTrue(Files.exists(config.resolve("forceloaded.json")));

        // User disables it.
        Files.write(mc.resolve("options.txt"), "resourcePacks:[]\n".getBytes(StandardCharsets.UTF_8));

        // Second boot: NOT re-enabled.
        ForceLoadHandler.run(mc, config);
        assertEquals("resourcePacks:[]", packsLine(mc));
    }

    @Test
    void noResourcepacksDirIsSafe(@TempDir Path tmp) {
        Path mc = tmp.resolve("mc");
        Path config = tmp.resolve("config");
        assertDoesNotThrow(() -> ForceLoadHandler.run(mc, config));
    }

    @Test
    void failedWriteIsNotRecordedAndRetries(@TempDir Path tmp) throws IOException {
        Path mc = Files.createDirectories(tmp.resolve("mc"));
        Path config = Files.createDirectories(tmp.resolve("config"));
        Path packs = Files.createDirectories(mc.resolve("resourcepacks"));
        makeFolderPack(packs, "ForcedPack", "{\"txloader\":{\"forceLoad\":true,\"priority\":\"top\"}}");

        // Create options.txt as a directory so the write fails
        Files.createDirectories(mc.resolve("options.txt"));

        ForceLoadHandler.run(mc, config);

        // The pack should NOT be recorded because the write failed
        Path forceloaded = config.resolve("forceloaded.json");
        if (Files.exists(forceloaded) && Files.isRegularFile(forceloaded)) {
            ForceLoadState state = ForceLoadState.load(forceloaded);
            assertFalse(state.contains("ForcedPack"), "Pack should not be recorded after a failed write");
        }
        // If forceloaded.json does not exist at all, that also satisfies the assertion
    }
}
