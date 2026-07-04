package glowredman.txloader;

import java.io.BufferedReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.google.common.reflect.TypeToken;

class ConfigHandler {

    private static Path configFile;
    // loaded from the config, Assets created via AssetBuilder are not stored
    static final List<Asset> ASSETS = Collections.synchronizedList(new ArrayList<>());
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
            synchronized (ASSETS) {
                ASSETS.addAll(TXLoaderCore.GSON.fromJson(reader, TYPE));
            }
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to read config file!", e);
            return;
        }

        TXLoaderCore.LOGGER.info("Successfully read config file.");

        synchronized (ASSETS) {
            ASSETS.forEach(RemoteHandler::fetchAsset);
        }
    }

    static boolean save() {
        try {
            synchronized (ASSETS) {
                Files.write(configFile, TXLoaderCore.GSON.toJson(ASSETS, TYPE).getBytes(StandardCharsets.UTF_8));
            }
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

            final BooleanHolder success = new BooleanHolder(true);

            try (Stream<Path> files = Files.walk(resources).filter(Files::isRegularFile)) {
                files.forEach(p -> {
                    Path targetRelative = resources.relativize(p);
                    Path target = TXLoaderCore.resourcesDir.resolve(targetRelative);
                    try {
                        Files.createDirectories(target.getParent());
                        Files.move(p, target);
                        TXLoaderCore.LOGGER.debug(
                                "Successfully moved {} to ./config/txloader/load/",
                                targetRelative.getFileName());
                    } catch (Exception e) {
                        TXLoaderCore.LOGGER
                                .warn("Failed to move {} to ./config/txloader/load/", targetRelative.getFileName(), e);
                        success.value = false;
                    }
                });
            } catch (Exception e) {
                TXLoaderCore.LOGGER.warn("Failed to iterate over files in {}", resources, e);
                success.value = false;
            }

            if (success.value) {
                try (Stream<Path> files = Files.walk(resources)) {
                    files.sorted(Comparator.reverseOrder()).forEachOrdered(p -> {
                        try {
                            Files.delete(p);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (Exception e) {
                    TXLoaderCore.LOGGER.warn("Failed to delete ./resources/", e);
                }
            }
        }

        if (Files.exists(oresources)) {
            TXLoaderCore.LOGGER.info("Attempting to move assets from ./resources/ to ./config/txloader/forceload/ ...");

            final BooleanHolder success = new BooleanHolder(true);

            try (Stream<Path> files = Files.walk(oresources).filter(Files::isRegularFile)) {
                files.forEach(p -> {
                    Path targetRelative = oresources.relativize(p);
                    Path target = TXLoaderCore.forceResourcesDir.resolve(targetRelative);
                    try {
                        Files.createDirectories(target.getParent());
                        Files.move(p, target);
                        TXLoaderCore.LOGGER.debug(
                                "Successfully moved {} to ./config/txloader/forceload/",
                                targetRelative.getFileName());
                    } catch (Exception e) {
                        TXLoaderCore.LOGGER.warn(
                                "Failed to move {} to ./config/txloader/forceload/",
                                targetRelative.getFileName(),
                                e);
                        success.value = false;
                    }
                });
            } catch (Exception e) {
                TXLoaderCore.LOGGER.warn("Failed to iterate over files in {}", oresources, e);
                success.value = false;
            }

            if (success.value) {
                try (Stream<Path> files = Files.walk(oresources)) {
                    files.sorted(Comparator.reverseOrder()).forEachOrdered(p -> {
                        try {
                            Files.delete(p);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (Exception e) {
                    TXLoaderCore.LOGGER.warn("Failed to delete ./oresources/", e);
                }
            }
        }
    }

    private static class BooleanHolder {

        private boolean value;

        private BooleanHolder(boolean initialValue) {
            this.value = initialValue;
        }
    }
}
