package glowredman.txloader;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.reflect.TypeToken;

class ForceLoadState {

    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, String>>() {}.getType();

    private final Path file;
    private final Map<String, String> records;

    private ForceLoadState(Path file, Map<String, String> records) {
        this.file = file;
        this.records = records;
    }

    static ForceLoadState load(Path file) {
        Map<String, String> records = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, String> loaded = TXLoaderCore.GSON.fromJson(r, MAP_TYPE);
                if (loaded != null) records.putAll(loaded);
            } catch (Exception e) {
                TXLoaderCore.LOGGER.warn("Failed to read {}, starting fresh", file, e);
            }
        }
        return new ForceLoadState(file, records);
    }

    boolean contains(String filename) {
        return records.containsKey(filename);
    }

    void record(String filename, String isoTimestamp) {
        records.put(filename, isoTimestamp);
    }

    void save() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                TXLoaderCore.GSON.toJson(records, w);
            }
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to save {}", file, e);
        }
    }
}
