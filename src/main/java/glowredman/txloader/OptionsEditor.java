package glowredman.txloader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

class OptionsEditor {

    private static final String KEY = "resourcePacks:";
    // Minecraft writes this line compactly; match that format.
    private static final Gson COMPACT = new Gson();

    static boolean enable(Path optionsFile, List<String> names, ForceLoadMeta.Priority placement) {
        if (names == null || names.isEmpty()) return true;
        try {
            List<String> lines = Files.isRegularFile(optionsFile)
                    ? new ArrayList<>(Files.readAllLines(optionsFile, StandardCharsets.UTF_8))
                    : new ArrayList<>();

            int index = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(KEY)) {
                    index = i;
                    break;
                }
            }

            List<String> packs = index >= 0 ? parse(lines.get(index).substring(KEY.length())) : new ArrayList<>();

            List<String> toAdd = new ArrayList<>();
            for (String name : names) {
                if (!packs.contains(name)) {
                    toAdd.add(name);
                }
            }

            if (placement == ForceLoadMeta.Priority.TOP) {
                packs.addAll(toAdd);
            } else {
                packs.addAll(0, toAdd);
            }

            String newLine = KEY + COMPACT.toJson(packs);
            if (index >= 0) {
                lines.set(index, newLine);
            } else {
                lines.add(newLine);
            }

            Files.write(optionsFile, lines, StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to update {}", optionsFile, e);
            return false;
        }
    }

    private static List<String> parse(String json) {
        try {
            List<String> parsed = COMPACT.fromJson(json.trim(), new TypeToken<List<String>>() {}.getType());
            return parsed != null ? new ArrayList<>(parsed) : new ArrayList<>();
        } catch (Exception e) {
            TXLoaderCore.LOGGER.warn("Could not parse existing resourcePacks line, treating as empty: {}", json, e);
            return new ArrayList<>();
        }
    }
}
