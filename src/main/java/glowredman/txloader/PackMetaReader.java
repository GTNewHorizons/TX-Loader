package glowredman.txloader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonObject;

class PackMetaReader {

    private static final String META = "pack.mcmeta";

    static Optional<ForceLoadMeta> read(Path pack) {
        try {
            if (Files.isDirectory(pack)) {
                Path meta = pack.resolve(META);
                if (!Files.isRegularFile(meta)) return Optional.empty();
                try (InputStream in = Files.newInputStream(meta)) {
                    return parse(in);
                }
            }
            if (pack.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                try (ZipFile zip = new ZipFile(pack.toFile())) {
                    ZipEntry entry = zip.getEntry(META);
                    if (entry == null) return Optional.empty();
                    try (InputStream in = zip.getInputStream(entry)) {
                        return parse(in);
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            TXLoaderCore.LOGGER.warn("Failed to read pack.mcmeta of {}", pack, e);
            return Optional.empty();
        }
    }

    private static Optional<ForceLoadMeta> parse(InputStream in) {
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = TXLoaderCore.GSON.fromJson(r, JsonObject.class);
            if (root == null || !root.has("txloader") || !root.get("txloader").isJsonObject()) {
                return Optional.empty();
            }
            JsonObject tx = root.getAsJsonObject("txloader");
            boolean force = tx.has("forceLoad") && tx.get("forceLoad").getAsBoolean();
            String priority = tx.has("priority") ? tx.get("priority").getAsString() : null;
            return Optional.of(new ForceLoadMeta(force, ForceLoadMeta.Priority.fromString(priority)));
        } catch (Exception e) {
            TXLoaderCore.LOGGER.warn("Failed to parse pack.mcmeta", e);
            return Optional.empty();
        }
    }
}
