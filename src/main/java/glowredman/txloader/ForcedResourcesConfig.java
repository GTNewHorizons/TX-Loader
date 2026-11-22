package glowredman.txloader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

final class ForcedResourcesConfig {

    private static final String CATEGORY = "forceload";
    private static final Configuration CONFIG = new Configuration(
            TXLoaderCore.configDir.resolve("forceload.cfg").toFile());
    static final Set<String> DISABLED_DOMAINS = Collections.unmodifiableSet(
            CONFIG.getCategory(CATEGORY).getValues().entrySet().stream()
                    .filter(entry -> !entry.getValue().getBoolean(true)).map(Map.Entry::getKey)
                    .collect(Collectors.toSet()));

    static List<Property> getFolderSettings() {
        List<Property> settings = new ArrayList<>();
        try (Stream<Path> paths = Files.list(TXLoaderCore.forceResourcesDir)) {
            paths.filter(Files::isDirectory).map(path -> path.getFileName().toString()).sorted()
                    .map(name -> CONFIG.get(CATEGORY, name, true).setRequiresMcRestart(true)).forEach(settings::add);
        } catch (IOException e) {
            TXLoaderCore.LOGGER.error("Failed to list forced resource folders", e);
        }
        return settings;
    }

    static void save() {
        CONFIG.save();
    }
}
