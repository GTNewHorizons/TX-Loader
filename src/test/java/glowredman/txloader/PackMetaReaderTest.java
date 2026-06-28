package glowredman.txloader;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackMetaReaderTest {

    private static final String FORCE_TOP = "{\"pack\":{\"pack_format\":1},\"txloader\":{\"forceLoad\":true,\"priority\":\"top\"}}";

    @Test
    void readsFolderPack(@TempDir Path tmp) throws IOException {
        Path pack = Files.createDirectory(tmp.resolve("FolderPack"));
        Files.write(pack.resolve("pack.mcmeta"), FORCE_TOP.getBytes(StandardCharsets.UTF_8));

        Optional<ForceLoadMeta> meta = PackMetaReader.read(pack);

        assertTrue(meta.isPresent());
        assertTrue(meta.get().forceLoad());
        assertEquals(ForceLoadMeta.Priority.TOP, meta.get().priority());
    }

    @Test
    void readsZipPackAndDefaultsPriorityToBottom(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("ZipPack.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write("{\"txloader\":{\"forceLoad\":true}}".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        Optional<ForceLoadMeta> meta = PackMetaReader.read(zip);

        assertTrue(meta.isPresent());
        assertTrue(meta.get().forceLoad());
        assertEquals(ForceLoadMeta.Priority.BOTTOM, meta.get().priority());
    }

    @Test
    void emptyWhenNoTxloaderSection(@TempDir Path tmp) throws IOException {
        Path pack = Files.createDirectory(tmp.resolve("Plain"));
        Files.write(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":1}}".getBytes(StandardCharsets.UTF_8));

        assertFalse(PackMetaReader.read(pack).isPresent());
    }

    @Test
    void emptyWhenMalformedJson(@TempDir Path tmp) throws IOException {
        Path pack = Files.createDirectory(tmp.resolve("Broken"));
        Files.write(pack.resolve("pack.mcmeta"), "{not json".getBytes(StandardCharsets.UTF_8));

        assertFalse(PackMetaReader.read(pack).isPresent());
    }

    @Test
    void emptyWhenNoMcmeta(@TempDir Path tmp) throws IOException {
        Path pack = Files.createDirectory(tmp.resolve("NoMeta"));

        assertFalse(PackMetaReader.read(pack).isPresent());
    }

    @Test
    void emptyWhenZipHasNoMcmeta(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("Empty.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("other.txt"));
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertFalse(PackMetaReader.read(zip).isPresent());
    }

    @Test
    void priorityFromStringIsCaseInsensitiveAndDefaultsBottom() {
        assertEquals(ForceLoadMeta.Priority.TOP, ForceLoadMeta.Priority.fromString("top"));
        assertEquals(ForceLoadMeta.Priority.TOP, ForceLoadMeta.Priority.fromString("TOP"));
        assertEquals(ForceLoadMeta.Priority.BOTTOM, ForceLoadMeta.Priority.fromString(null));
        assertEquals(ForceLoadMeta.Priority.BOTTOM, ForceLoadMeta.Priority.fromString("other"));
    }
}
