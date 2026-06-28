package glowredman.txloader;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OptionsEditorTest {

    private String packsLine(Path options) throws IOException {
        return Files.readAllLines(options, StandardCharsets.UTF_8).stream().filter(l -> l.startsWith("resourcePacks:"))
                .findFirst().orElse(null);
    }

    @Test
    void appendsTopToEnd(@TempDir Path tmp) throws IOException {
        Path options = tmp.resolve("options.txt");
        Files.write(
                options,
                ("fov:1.0\nresourcePacks:[\"existing.zip\"]\nmipmapLevels:4\n").getBytes(StandardCharsets.UTF_8));

        OptionsEditor.enable(options, Collections.singletonList("forced.zip"), ForceLoadMeta.Priority.TOP);

        assertEquals("resourcePacks:[\"existing.zip\",\"forced.zip\"]", packsLine(options));
        // other lines preserved
        assertTrue(Files.readAllLines(options).contains("fov:1.0"));
        assertTrue(Files.readAllLines(options).contains("mipmapLevels:4"));
    }

    @Test
    void prependsBottomToStart(@TempDir Path tmp) throws IOException {
        Path options = tmp.resolve("options.txt");
        Files.write(options, "resourcePacks:[\"existing.zip\"]\n".getBytes(StandardCharsets.UTF_8));

        OptionsEditor.enable(options, Collections.singletonList("forced.zip"), ForceLoadMeta.Priority.BOTTOM);

        assertEquals("resourcePacks:[\"forced.zip\",\"existing.zip\"]", packsLine(options));
    }

    @Test
    void doesNotDuplicate(@TempDir Path tmp) throws IOException {
        Path options = tmp.resolve("options.txt");
        Files.write(options, "resourcePacks:[\"forced.zip\"]\n".getBytes(StandardCharsets.UTF_8));

        OptionsEditor.enable(options, Arrays.asList("forced.zip", "new.zip"), ForceLoadMeta.Priority.TOP);

        assertEquals("resourcePacks:[\"forced.zip\",\"new.zip\"]", packsLine(options));
    }

    @Test
    void addsLineWhenMissing(@TempDir Path tmp) throws IOException {
        Path options = tmp.resolve("options.txt");
        Files.write(options, "fov:1.0\n".getBytes(StandardCharsets.UTF_8));

        OptionsEditor.enable(options, Collections.singletonList("forced.zip"), ForceLoadMeta.Priority.TOP);

        assertEquals("resourcePacks:[\"forced.zip\"]", packsLine(options));
        assertTrue(Files.readAllLines(options).contains("fov:1.0"));
    }

    @Test
    void createsFileWhenMissing(@TempDir Path tmp) throws IOException {
        Path options = tmp.resolve("options.txt");

        OptionsEditor.enable(options, Collections.singletonList("forced.zip"), ForceLoadMeta.Priority.BOTTOM);

        assertEquals("resourcePacks:[\"forced.zip\"]", packsLine(options));
    }

    @Test
    void noOpOnEmptyNames(@TempDir Path tmp) throws IOException {
        Path options = tmp.resolve("options.txt");
        Files.write(options, "resourcePacks:[\"existing.zip\"]\n".getBytes(StandardCharsets.UTF_8));

        OptionsEditor.enable(options, Collections.emptyList(), ForceLoadMeta.Priority.TOP);

        assertEquals("resourcePacks:[\"existing.zip\"]", packsLine(options));
    }
}
