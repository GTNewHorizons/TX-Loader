package glowredman.txloader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.annotation.Nonnull;

import glowredman.txloader.Asset.Source;

class RemoteHandler {

    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final String RESOURCES_URL = "https://resources.download.minecraft.net/";
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;

    // dummy value, prevents NPEs
    static volatile CompletableFuture<JVersionManifest> versionsStage = new CompletableFuture<>();

    private static final Map<String, CompletableFuture<JVersionDetails>> DETAILS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Map<String, JAsset>>> ASSET_INDICES = new ConcurrentHashMap<>();
    static final Set<CompletableFuture<Void>> BLOCKING_FUTURES = new HashSet<>();
    private static final Set<Path> PATHS = ConcurrentHashMap.newKeySet();

    static JVersionManifest fetchVersions() {
        Path path = JarHandler.txloaderCache.resolve("version_manifest.json");

        try {
            // always try to update the manifest because it changes regularly
            download(MANIFEST_URL, path);
            TXLoaderCore.LOGGER.info("Successfully fetched Minecraft versions.");
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to update Minecraft versions! Attempting to use cached manifest...", e);
        }

        JVersionManifest manifest = null;

        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                manifest = TXLoaderCore.GSON.fromJson(reader, JVersionManifest.class);
            } catch (Exception e) {
                TXLoaderCore.LOGGER.error("Manifest file could not be parsed!", e);
            }
        } else {
            TXLoaderCore.LOGGER.error("No cached manifest found!");
        }

        if (manifest == null) {
            return JVersionManifest.DUMMY;
        }

        Map<String, String> urls = new ConcurrentHashMap<>(manifest.versions.size(), 1.0f);
        for (JVersion version : manifest.versions) {
            urls.put(version.id, version.url);
        }
        manifest.urls = urls;

        return manifest;
    }

    static CompletableFuture<Void> fetchAsset(@Nonnull Asset asset) {
        Path path = asset.getPath();
        String version = asset.getVersion();
        Source source = asset.getSource();

        if (!PATHS.add(path)) {
            TXLoaderCore.LOGGER.warn(
                    "Duplicate asset defined for {}, skipping {} on version {} for source {}",
                    asset.getResourceLocation(),
                    asset.resourceLocation,
                    version,
                    source);
            return CompletableFuture.completedFuture(null);
        }

        if (Files.exists(path)) {
            return CompletableFuture.completedFuture(null);
        }

        FutureWrapper<JVersionDetails> detailsWrapper = new FutureWrapper<>();
        CompletableFuture<JVersionDetails> detailsStage = DETAILS
                .computeIfAbsent(version, RemoteHandler::verifyDetailsExist)
                .whenComplete((details, t) -> resetOnFailure(DETAILS, detailsWrapper, version, details));
        detailsWrapper.reference = detailsStage;

        if (source == Source.ASSET) {
            CompletableFuture<Void> future = ASSET_INDICES.computeIfAbsent(version, v -> {
                FutureWrapper<Map<String, JAsset>> assetIndexWrapper = new FutureWrapper<>();
                CompletableFuture<Map<String, JAsset>> assetIndexStage = detailsStage
                        .thenApplyAsync(details -> verifyAssetIndexExists(v, details), TXLoaderCore.EXECUTOR_NET)
                        .whenComplete((index, t) -> resetOnFailure(ASSET_INDICES, assetIndexWrapper, v, index));
                assetIndexWrapper.reference = assetIndexStage;
                return assetIndexStage;
            }).thenAcceptAsync(assetIndex -> fetchDirect(assetIndex, asset, path, version), TXLoaderCore.EXECUTOR_NET);

            synchronized (BLOCKING_FUTURES) {
                BLOCKING_FUTURES.add(future);
            }

            return future;
        }

        // asset from client/server jar:
        boolean isClient = source == Source.CLIENT;

        Map<String, CompletableFuture<Path>> cachedJars = isClient ? JarHandler.CACHED_CLIENT_JARS
                : JarHandler.CACHED_SERVER_JARS;
        CompletableFuture<Void> future = cachedJars.computeIfAbsent(version, v -> {
            FutureWrapper<Path> jarWrapper = new FutureWrapper<>();
            CompletableFuture<Path> jarStage = detailsStage
                    .thenApplyAsync(details -> verifyJarExists(details, v, isClient), TXLoaderCore.EXECUTOR_NET)
                    .whenComplete((jarPath, t) -> resetOnFailure(cachedJars, jarWrapper, v, jarPath));
            jarWrapper.reference = jarStage;
            return jarStage;
        }).thenAcceptAsync(jarPath -> fetchFromJar(asset, jarPath, path), TXLoaderCore.EXECUTOR_IO);

        synchronized (BLOCKING_FUTURES) {
            BLOCKING_FUTURES.add(future);
        }

        return future;
    }

    private static CompletableFuture<JVersionDetails> verifyDetailsExist(String version) {
        return versionsStage.thenApplyAsync(manifest -> {
            Path path = JarHandler.versions.resolve(version + ".json");

            if (Files.notExists(path)) {
                String url = manifest.urls.get(version);

                if (url == null) {
                    TXLoaderCore.LOGGER.error("Version details URL for version {} not known!", version);
                    return null;
                }

                try {
                    download(url, path);
                } catch (Exception e) {
                    TXLoaderCore.LOGGER.error(
                            "An error occurred while downloading the version details for version {}!",
                            version,
                            e);
                    return null;
                }
            }

            try (BufferedReader reader = Files.newBufferedReader(path)) {
                return TXLoaderCore.GSON.fromJson(reader, JVersionDetails.class);
            } catch (Exception e) {
                TXLoaderCore.LOGGER.error("Failed to get version details for version {}", version, e);
                return null;
            }
        }, TXLoaderCore.EXECUTOR_NET);
    }

    private static Map<String, JAsset> verifyAssetIndexExists(String version, JVersionDetails details) {
        Path path = JarHandler.assetIndex.resolve(version + ".json");

        if (Files.notExists(path)) {
            if (details == null) {
                return null;
            }

            try {
                download(details.assetIndex.url, path);
            } catch (Exception e) {
                TXLoaderCore.LOGGER.error("Failed to download asset index for version {}!", version, e);
                return null;
            }
        }

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return TXLoaderCore.GSON.fromJson(reader, JAssetIndex.class).objects;
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to get asset index for version {}!", version, e);
            return null;
        }
    }

    private static Path verifyJarExists(JVersionDetails details, String version, boolean isClient) {
        Path path = JarHandler.searchJar(version, isClient);

        if (path == null) {
            if (details == null) {
                return null;
            }

            try {
                if (isClient) {
                    return details.downloads.client.downloadJar(version, "client.jar");
                }
                return details.downloads.server.downloadJar(version, "server.jar");
            } catch (Exception e) {
                TXLoaderCore.LOGGER
                        .error("Failed to download {} JAR for version {}!", isClient ? "client" : "server", version, e);
                return null;
            }
        }
        return path;
    }

    private static void fetchDirect(Map<String, JAsset> assets, Asset asset, Path path, String version) {
        if (assets == null) {
            return;
        }

        JAsset jAsset = assets.get(asset.resourceLocation);

        if (jAsset == null) {
            TXLoaderCore.LOGGER.error("Failed to find asset {} for version {}!", asset.resourceLocation, version);
            return;
        }

        try {
            jAsset.download(path);
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to get asset! Path: {}", asset.resourceLocation, e);
            return;
        }

        TXLoaderCore.LOGGER.debug("Successfully fetched {}", asset.resourceLocation);
    }

    private static void fetchFromJar(Asset asset, Path jarPath, Path targetPath) {
        if (jarPath == null) {
            return;
        }

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            JarEntry jarEntry = jarFile.getJarEntry("assets/" + asset.resourceLocation);

            if (jarEntry == null) {
                TXLoaderCore.LOGGER
                        .error("Failed to find asset {} in JAR ({}), skipping!", asset.resourceLocation, jarPath);
                return;
            }

            Files.createDirectories(targetPath.getParent());
            try (InputStream is = jarFile.getInputStream(jarEntry)) {
                Files.copy(is, targetPath);
            }
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to extract asset from JAR! Path: {}", asset.resourceLocation, e);
            return;
        }

        TXLoaderCore.LOGGER.debug("Successfully fetched {}", asset.resourceLocation);
    }

    private static void download(String url, Path path) throws IOException {
        TXLoaderCore.LOGGER.info("Downloading {} to {}", url, path);
        Path temp = Files.createTempFile(path.getParent(), null, null);
        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        try (InputStream is = connection.getInputStream()) {
            Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            // try again, without ATOMIC_MOVE
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static <T> void resetOnFailure(Map<String, CompletableFuture<T>> map, FutureWrapper<T> wrapper, String key,
            T result) {
        if (result == null) {
            map.remove(key, wrapper.reference);
        }
    }

    /**
     * Ensures that all futures are completed
     */
    static void ensureNoBlocking() {
        // copying old ResourceLoader files, fetching version manifest, loading config
        try {
            versionsStage.join();
        } catch (Exception e) {
            TXLoaderCore.LOGGER.warn("A future completed exceptionally!", e);
        }

        // fetch assets (directly or from JARs), this implicitly includes downloads of version details, asset indices
        // and JARs
        synchronized (BLOCKING_FUTURES) {
            for (CompletableFuture<Void> future : BLOCKING_FUTURES) {
                if (future.isDone()) {
                    continue;
                }
                try {
                    future.join();
                } catch (Exception e) {
                    TXLoaderCore.LOGGER.warn("A future completed exceptionally!", e);
                }
            }
            BLOCKING_FUTURES.clear();
        }
    }

    private static class FutureWrapper<T> {

        CompletableFuture<T> reference;

    }

    /*
     * JSON templates
     */

    static class JVersionManifest {

        static final JVersionManifest DUMMY;

        List<JVersion> versions;
        transient Map<String, String> urls; // ignored by GSON

        static {
            DUMMY = new JVersionManifest();
            DUMMY.versions = Collections.emptyList();
            DUMMY.urls = Collections.emptyMap();
        }
    }

    static class JVersion {

        String id;
        String url;
    }

    static class JVersionDetails {

        JSourceDetails assetIndex;
        JDownloads downloads;
    }

    static class JSourceDetails {

        String url;

        Path downloadJar(String version, String fileName) throws IOException {
            Path dir = JarHandler.txloaderCache.resolve(version);
            Files.createDirectories(dir);
            Path jar = dir.resolve(fileName);
            RemoteHandler.download(this.url, jar);
            return jar;
        }
    }

    static class JDownloads {

        JSourceDetails client;
        JSourceDetails server;
    }

    static class JAssetIndex {

        Map<String, JAsset> objects;
    }

    static class JAsset {

        String hash;

        void download(Path path) throws IOException {
            Files.createDirectories(path.getParent());
            StringBuilder sb = new StringBuilder(84);
            sb.append(RESOURCES_URL);
            sb.append(this.hash, 0, 2);
            sb.append('/');
            sb.append(this.hash);
            RemoteHandler.download(sb.toString(), path);
        }
    }
}
