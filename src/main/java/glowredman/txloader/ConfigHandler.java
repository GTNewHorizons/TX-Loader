package glowredman.txloader;

import java.io.BufferedReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.reflect.TypeToken;

class ConfigHandler {

    private static Path configFile;
    private static final Type TYPE = new TypeToken<List<Asset>>() {

        private static final long serialVersionUID = 1L;
    }.getType();

    static void load() {
        configFile = TXLoaderCore.configDir.resolve("config.json");

        if (Files.notExists(configFile)) {
            try {
                Files.write(configFile, TXLoaderCore.GSON.toJson(new ArrayList<>()).getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                TXLoaderCore.LOGGER.error("Failed to create config file!", e);
            }
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            TXLoaderCore.REMOTE_ASSETS.addAll(TXLoaderCore.GSON.fromJson(reader, TYPE));
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to read config file!", e);
            return;
        }

        TXLoaderCore.LOGGER.info("Successfully read config file.");
    }

    static boolean save() {
        try {
            Files.write(
                    configFile,
                    TXLoaderCore.GSON.toJson(
                            TXLoaderCore.REMOTE_ASSETS.parallelStream().filter(a -> !a.addedByMod)
                                    .collect(Collectors.toList()),
                            TYPE).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed saving config!", e);
            return false;
        }
    }

    static void moveRLAssets() {
        Path resources = TXLoaderCore.mcLocation.resolve("resources");
        Path oresources = TXLoaderCore.mcLocation.resolve("oresources");

        if (Files.exists(resources)) {
            TXLoaderCore.LOGGER.info("Attempting to move assets from ./resources/ to ./config/txloader/load/ ...");

            try (Stream<Path> files = Files.list(resources).filter(Files::isRegularFile)) {
                files.forEach(p -> {
                    Path target = resources.relativize(p);
                    try {
                        Files.move(p, TXLoaderCore.resourcesDir.resolve(target));
                        TXLoaderCore.LOGGER
                                .debug("Successfully moved {} to ./config/txloader/load/", target.getFileName());
                    } catch (Exception e) {
                        TXLoaderCore.LOGGER
                                .warn("Failed to move {} to ./config/txloader/load/", target.getFileName(), e);
                    }
                });

                try {
                    Files.delete(resources);
                } catch (Exception e) {
                    TXLoaderCore.LOGGER.warn("Failed to delete ./resources/", e);
                }
            } catch (Exception e) {
                TXLoaderCore.LOGGER.warn("Failed to iterate over files in {}", resources, e);
            }
        }

        if (Files.exists(oresources)) {
            TXLoaderCore.LOGGER
                    .info("Attempting to move assets from ./oresources/ to ./config/txloader/forceload/ ...");

            try (Stream<Path> files = Files.list(oresources).filter(Files::isRegularFile)) {
                files.forEach(p -> {
                    Path target = oresources.relativize(p);
                    try {
                        Files.move(p, TXLoaderCore.forceResourcesDir.resolve(target));
                        TXLoaderCore.LOGGER
                                .debug("Successfully moved {} to ./config/txloader/forceload/", target.getFileName());
                    } catch (Exception e) {
                        TXLoaderCore.LOGGER
                                .warn("Failed to move {} to ./config/txloader/forceload/", target.getFileName(), e);
                    }
                });

                try {
                    Files.delete(oresources);
                } catch (Exception e) {
                    TXLoaderCore.LOGGER.warn("Failed to delete ./oresources/", e);
                }
            } catch (Exception e) {
                TXLoaderCore.LOGGER.warn("Failed to iterate over files in {}", resources, e);
            }
        }
    }
}
