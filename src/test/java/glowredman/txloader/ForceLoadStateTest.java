package glowredman.txloader;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForceLoadStateTest {

    @Test
    void roundTripsRecords(@TempDir Path tmp) {
        Path file = tmp.resolve("forceloaded.json");

        ForceLoadState state = ForceLoadState.load(file);
        assertFalse(state.contains("Pack.zip"));
        state.record("Pack.zip", "2026-06-27T00:00:00Z");
        state.save();

        ForceLoadState reloaded = ForceLoadState.load(file);
        assertTrue(reloaded.contains("Pack.zip"));
    }

    @Test
    void emptyWhenFileMissing(@TempDir Path tmp) {
        ForceLoadState state = ForceLoadState.load(tmp.resolve("nope.json"));
        assertFalse(state.contains("anything"));
    }

    @Test
    void emptyWhenFileCorrupt(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("forceloaded.json");
        Files.write(file, "{not valid".getBytes(StandardCharsets.UTF_8));

        ForceLoadState state = ForceLoadState.load(file);
        assertFalse(state.contains("anything"));
        // still usable
        state.record("X", "t");
        state.save();
        assertTrue(ForceLoadState.load(file).contains("X"));
    }
}
