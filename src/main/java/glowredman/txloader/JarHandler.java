package glowredman.txloader;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Pair;

class JarHandler {

    static final Map<String, CompletableFuture<Path>> CACHED_CLIENT_JARS = new ConcurrentHashMap<>();
    static final Map<String, CompletableFuture<Path>> CACHED_SERVER_JARS = new ConcurrentHashMap<>();
    private static final List<Pair<Path, String>> CLIENT_LOCATIONS = Collections.synchronizedList(new ArrayList<>());
    private static final List<Pair<Path, String>> SERVER_LOCATIONS = Collections.synchronizedList(new ArrayList<>());

    static Path txloaderCache;
    static Path versions;
    static Path assetIndex;

    /**
     * @return {@code true} if either the {@link #versions} or {@link #assetIndex} directory don't exist.
     */
    static boolean initCache() {
        setCachePath();
        createJarLocations();

        versions = txloaderCache.resolve("versions");
        assetIndex = txloaderCache.resolve("assetIndex");

        try {
            Files.createDirectories(versions);
            Files.createDirectories(assetIndex);
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("An exception occured during cache initialization!", e);
            return !Files.isDirectory(versions) || !Files.isDirectory(assetIndex);
        }
        return false;
    }

    static Path searchJar(String version, boolean isClient) {
        List<Pair<Path, String>> locations = isClient ? CLIENT_LOCATIONS : SERVER_LOCATIONS;
        for (Pair<Path, String> location : locations) {
            Path path = findJar(location.getLeft(), location.getRight(), isClient, version);
            if (path != null) {
                return path;
            }
        }
        return null;
    }

    private static void setCachePath() {
        final String userHome = System.getProperty("user.home");
        final String system = System.getProperty("os.name").toLowerCase();

        try {
            if (system.contains("win")) {
                String temp = System.getenv("TEMP");
                String localAppData = System.getenv("LOCALAPPDATA");
                if (temp != null) {
                    txloaderCache = Paths.get(temp, "txloader");
                } else if (localAppData != null) {
                    txloaderCache = Paths.get(localAppData, "Temp", "txloader");
                } else {
                    txloaderCache = Paths.get(userHome, "AppData", "Local", "Temp", "txloader");
                }
            } else if (system.contains("mac")) {
                txloaderCache = Paths.get(userHome, "Library", "Caches", "txloader");
            } else {
                String xdgCacheHome = System.getenv("XDG_CACHE_HOME");
                if (xdgCacheHome == null) {
                    txloaderCache = Paths.get(userHome, ".cache", "txloader");
                } else {
                    txloaderCache = Paths.get(xdgCacheHome, "txloader");
                }
            }
        } catch (InvalidPathException e) {
            if (system.contains("win")) {
                txloaderCache = Paths.get(userHome, "AppData", "Local", "Temp", "txloader");
            } else {
                txloaderCache = Paths.get(userHome, ".cache", "txloader");
            }
            TXLoaderCore.LOGGER.warn(
                    "An error occurred while the TXLoader cache path was created. The environment variable TEMP or LOCALAPPDATA could be set incorrectly.",
                    txloaderCache,
                    e);
        }

        TXLoaderCore.LOGGER.debug("Cache location is {}", txloaderCache);
    }

    private static void createJarLocations() {
        final String userHome = System.getProperty("user.home");

        // client
        CLIENT_LOCATIONS.add(Pair.of(txloaderCache, "client.jar"));
        CLIENT_LOCATIONS.add(Pair.of(Paths.get(userHome, "AppData", "Roaming", ".minecraft", "versions"), "%s.jar"));
        CLIENT_LOCATIONS.add(
                Pair.of(
                        Paths.get(userHome, ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions"),
                        "client.jar"));
        CLIENT_LOCATIONS.add(
                Pair.of(
                        Paths.get(userHome, ".gradle", "caches", "minecraft", "net", "minecraft", "minecraft"),
                        "minecraft-%s.jar"));
        CLIENT_LOCATIONS.add(
                Pair.of(Paths.get(userHome, ".gradle", "caches", "retro_futura_gradle", "mc-vanilla"), "client.jar"));

        // server
        SERVER_LOCATIONS.add(Pair.of(txloaderCache, "server.jar"));
        SERVER_LOCATIONS.add(
                Pair.of(
                        Paths.get(userHome, ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions"),
                        "server.jar"));
        SERVER_LOCATIONS.add(
                Pair.of(
                        Paths.get(userHome, ".gradle", "caches", "minecraft", "net", "minecraft", "minecraft_server"),
                        "minecraft_server-%s.jar"));
        SERVER_LOCATIONS.add(
                Pair.of(Paths.get(userHome, ".gradle", "caches", "retro_futura_gradle", "mc-vanilla"), "server.jar"));
    }

    private static Path findJar(Path start, String fileName, boolean isClient, String version) {
        if (!Files.isDirectory(start)) {
            return null;
        }

        FileVisitor visitor = new FileVisitor(start, fileName, isClient, version);
        try {
            Files.walkFileTree(start, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 2, visitor);
        } catch (Exception e) {
            TXLoaderCore.LOGGER.debug("Cannot walk cache directory {}", start, e);
        }

        return visitor.result;
    }

    private static class FileVisitor extends SimpleFileVisitor<Path> {

        private final Path start;
        private final String fileName;
        private final boolean isClient;
        private final String targetVersion;
        Path result;

        public FileVisitor(Path start, String fileName, boolean isClient, String targetVersion) {
            this.start = start;
            this.fileName = fileName;
            this.isClient = isClient;
            this.targetVersion = targetVersion;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (!Files.isSameFile(dir, this.start) && !dir.getFileName().toString().equals(this.targetVersion)) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Path parent = file.getParent();
            if (Files.isSameFile(parent, this.start) || !attrs.isRegularFile() || attrs.size() <= 16384) {
                return FileVisitResult.CONTINUE;
            }

            String version = parent.getFileName().toString();
            if (!String.format(this.fileName, version).equals(file.getFileName().toString())) {
                return FileVisitResult.CONTINUE;
            }

            this.result = file;

            TXLoaderCore.LOGGER
                    .debug("Found {} JAR for version {} at {}", this.isClient ? "client" : "server", version, file);
            return FileVisitResult.TERMINATE;
        }
    }
}
