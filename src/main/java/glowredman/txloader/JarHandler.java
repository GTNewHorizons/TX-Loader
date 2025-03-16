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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.base.Stopwatch;

import cpw.mods.fml.relauncher.Side;

class JarHandler {

    static final Map<String, Path> CACHED_CLIENT_JARS = new HashMap<>();
    static final Map<String, Path> CACHED_SERVER_JARS = new HashMap<>();

    static Path txloaderCache;

    static void indexJars() {
        String userHome = System.getProperty("user.home");
        String system = System.getProperty("os.name").toLowerCase();
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
                    "An error occurred while the TXLoader cache path was created. The environment variable TEMP or LOCALAPPDATA could be set incorrectly. Using the default cache location: {}",
                    txloaderCache,
                    e);
        }
        List<Pair<Path, String>> clientLocations = new ArrayList<>();
        clientLocations.add(Pair.of(txloaderCache, "client.jar"));
        clientLocations.add(Pair.of(Paths.get(userHome, "AppData", "Roaming", ".minecraft", "versions"), "%s.jar"));
        clientLocations.add(
                Pair.of(
                        Paths.get(userHome, ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions"),
                        "client.jar"));
        clientLocations.add(
                Pair.of(
                        Paths.get(userHome, ".gradle", "caches", "minecraft", "net", "minecraft", "minecraft"),
                        "minecraft-%s.jar"));
        clientLocations.add(
                Pair.of(Paths.get(userHome, ".gradle", "caches", "retro_futura_gradle", "mc-vanilla"), "client.jar"));

        List<Pair<Path, String>> serverLocations = new ArrayList<>();
        serverLocations.add(Pair.of(txloaderCache, "server.jar"));
        serverLocations.add(
                Pair.of(
                        Paths.get(userHome, ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions"),
                        "server.jar"));
        serverLocations.add(
                Pair.of(
                        Paths.get(userHome, ".gradle", "caches", "minecraft", "net", "minecraft", "minecraft_server"),
                        "minecraft_server-%s.jar"));
        serverLocations.add(
                Pair.of(Paths.get(userHome, ".gradle", "caches", "retro_futura_gradle", "mc-vanilla"), "server.jar"));

        Stopwatch stopwatch = Stopwatch.createStarted();
        for (Pair<Path, String> location : clientLocations) {
            collect(location.getLeft(), location.getRight(), Side.CLIENT);
        }
        for (Pair<Path, String> location : serverLocations) {
            collect(location.getLeft(), location.getRight(), Side.SERVER);
        }
        TXLoaderCore.LOGGER.debug("Scan for jars took {}ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    private static void collect(Path start, String fileName, Side side) {
        if (!Files.isDirectory(start)) return;
        try {
            Files.walkFileTree(start, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 2, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!Files.isSameFile(dir, start)
                            && !RemoteHandler.VERSIONS.containsKey(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path parent = file.getParent();
                    if (Files.isSameFile(parent, start) || !attrs.isRegularFile() || attrs.size() <= 1024) {
                        return FileVisitResult.CONTINUE;
                    }

                    String version = parent.getFileName().toString();
                    if (!String.format(fileName, version).equals(file.getFileName().toString())) {
                        return FileVisitResult.CONTINUE;
                    }

                    if (side.isClient()) CACHED_CLIENT_JARS.put(version, file);
                    else CACHED_SERVER_JARS.put(version, file);
                    TXLoaderCore.LOGGER.debug("Found {} jar for version {} at {}", side, version, file);
                    return FileVisitResult.SKIP_SIBLINGS;
                }
            });
        } catch (IOException e) {
            TXLoaderCore.LOGGER.debug("Cannot walk cache directory {}", start, e);
        }
    }

}
