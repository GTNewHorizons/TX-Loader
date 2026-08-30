package glowredman.txloader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.annotation.Nonnull;

import glowredman.txloader.Asset.Source;
import glowredman.txloader.CompletableFutureWrapper.State;

class RemoteHandler {

    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final String RESOURCES_URL = "https://resources.download.minecraft.net/";
    private static final int CONNECT_TIMEOUT;
    private static final int READ_TIMEOUT;

    static final CompletableFuture<JVersionManifest> VERSIONS_STAGE = new CompletableFuture<>();
    static final CompletableFuture<Void> LOAD_STAGE = new CompletableFuture<>();
    private static final CompletableFuture<Void> FILE_EXISTS = CompletableFuture.completedFuture(null);
    private static final CompletableFutureWrapper<Void> FILE_EXISTS_WRAPPER = new CompletableFutureWrapper<>(
            CompletableFuture.completedFuture(null),
            State.FILE_EXISTS);
    private static final Map<String, CompletableFuture<JVersionDetails>> DETAILS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Map<String, JAsset>>> ASSET_INDICES = new ConcurrentHashMap<>();
    static final Set<CompletableFuture<Void>> BLOCKING_FUTURES = new HashSet<>();
    private static final Map<Path, CompletableFuture<Void>> PATHS = new HashMap<>();

    static {
        // get arguments
        int connectTimeout = Integer.getInteger("txloader.timeout.connect", 5000);
        int readTimeout = Integer.getInteger("txloader.timeout.read", 10000);

        // check for invalid values
        if (connectTimeout < 0) {
            TXLoaderCore.LOGGER
                    .warn("-Dtxloader.timeout.connect must be non-negative ({}), ignoring argument", connectTimeout);
            connectTimeout = 5000;
        }
        if (readTimeout < 0) {
            TXLoaderCore.LOGGER
                    .warn("-Dtxloader.timeout.read must be non-negative ({}), ignoring argument", readTimeout);
            readTimeout = 10000;
        }

        CONNECT_TIMEOUT = connectTimeout;
        READ_TIMEOUT = readTimeout;
    }

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

        if (manifest == null || manifest.versions == null) {
            return JVersionManifest.DUMMY;
        }

        Map<String, String> urls = new ConcurrentHashMap<>(manifest.versions.size(), 1.0f);
        for (JVersion version : manifest.versions) {
            urls.put(version.id, version.url);
        }
        manifest.urls = urls;

        return manifest;
    }

    static CompletableFutureWrapper<Void> fetchAsset(@Nonnull Asset asset) {
        Path path = asset.getPath();
        String version = asset.getVersion();
        Source source = asset.getSource();

        synchronized (PATHS) {
            CompletableFuture<Void> existingFuture = PATHS.get(path);
            if (existingFuture != null) {
                TXLoaderCore.LOGGER.warn(
                        "Duplicate asset defined for {}, skipping {} on version {} for source {}",
                        asset.getResourceLocation(),
                        asset.resourceLocation,
                        version,
                        source);
                return new CompletableFutureWrapper<>(existingFuture, State.DUPLICATE_ASSET);
            }

            if (Files.exists(path)) {
                PATHS.put(path, FILE_EXISTS);
                return FILE_EXISTS_WRAPPER;
            }

            if (source == Source.ASSET) {
                CompletableFuture<Map<String, JAsset>> assetIndexBaseStage = ASSET_INDICES
                        .computeIfAbsent(version, v -> {
                            CompletableFuture<JVersionDetails> detailsBaseStage = DETAILS
                                    .computeIfAbsent(version, RemoteHandler::verifyDetailsExist);

                            return detailsBaseStage
                                    .whenComplete(
                                            (details, t) -> resetOnFailure(DETAILS, detailsBaseStage, version, details))
                                    .thenApplyAsync(
                                            details -> verifyAssetIndexExists(v, details),
                                            TXLoaderCore.EXECUTOR_NET);
                        });

                CompletableFuture<Void> future = assetIndexBaseStage.whenComplete(
                        (assetIndex, t) -> resetOnFailure(ASSET_INDICES, assetIndexBaseStage, version, assetIndex))
                        .thenAcceptAsync(
                                assetIndex -> fetchDirect(assetIndex, asset, path, version),
                                TXLoaderCore.EXECUTOR_NET);

                synchronized (BLOCKING_FUTURES) {
                    BLOCKING_FUTURES.add(future);
                }

                return new CompletableFutureWrapper<Void>(future, State.NEW);
            }

            // asset from client/server jar:
            boolean isClient = source == Source.CLIENT;
            Map<String, CompletableFuture<Path>> cachedJars = isClient ? JarHandler.CACHED_CLIENT_JARS
                    : JarHandler.CACHED_SERVER_JARS;

            CompletableFuture<Path> baseFuture = cachedJars.computeIfAbsent(
                    version,
                    v -> CompletableFuture
                            .supplyAsync(() -> JarHandler.searchJar(v, isClient), TXLoaderCore.EXECUTOR_IO)
                            .thenComposeAsync(jarPath -> {
                                if (jarPath == null) {
                                    CompletableFuture<JVersionDetails> detailsBaseStage = DETAILS
                                            .computeIfAbsent(v, RemoteHandler::verifyDetailsExist);

                                    return detailsBaseStage.whenComplete(
                                            (details, t) -> resetOnFailure(DETAILS, detailsBaseStage, v, details))
                                            .thenApplyAsync(
                                                    details -> downloadJar(details, v, isClient),
                                                    TXLoaderCore.EXECUTOR_NET);
                                }
                                return CompletableFuture.completedFuture(jarPath);
                            }, TXLoaderCore.EXECUTOR_IO));

            CompletableFuture<Void> future = baseFuture
                    .whenComplete((jarPath, t) -> resetOnFailure(cachedJars, baseFuture, version, jarPath))
                    .thenAcceptAsync(jarPath -> fetchFromJar(asset, jarPath, path), TXLoaderCore.EXECUTOR_IO);

            synchronized (BLOCKING_FUTURES) {
                BLOCKING_FUTURES.add(future);
            }

            return new CompletableFutureWrapper<Void>(future, State.NEW);
        }
    }

    private static CompletableFuture<JVersionDetails> verifyDetailsExist(String version) {
        return VERSIONS_STAGE.thenApplyAsync(manifest -> {
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

    private static Path downloadJar(JVersionDetails details, String version, boolean isClient) {
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
            copyWithTempFile(() -> jarFile.getInputStream(jarEntry), targetPath);
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to extract asset from JAR! Path: {}", asset.resourceLocation, e);
            return;
        }

        TXLoaderCore.LOGGER.debug("Successfully fetched {}", asset.resourceLocation);
    }

    private static void download(String url, Path path) throws Exception {
        TXLoaderCore.LOGGER.info("Downloading {} to {}", url, path);
        copyWithTempFile(() -> {
            URLConnection connection = new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            return connection.getInputStream();
        }, path);
    }

    private static void copyWithTempFile(Callable<InputStream> in, Path path) throws Exception {
        Path temp = Files.createTempFile(path.getParent(), null, null);
        try {
            try (InputStream is = in.call()) {
                Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                // try again, without ATOMIC_MOVE
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            // if any of the above fails, the temp file remains
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private static <T> void resetOnFailure(Map<String, CompletableFuture<T>> map, CompletableFuture<T> future,
            String key, T result) {
        if (result == null) {
            map.remove(key, future);
        }
    }

    /**
     * Ensures that all futures are completed
     */
    static void ensureNoBlocking() {
        // early exit
        synchronized (BLOCKING_FUTURES) {
            if (LOAD_STAGE.isDone() && BLOCKING_FUTURES.isEmpty()) {
                return;
            }
        }

        // copying old ResourceLoader files, fetching version manifest, loading config
        if (!LOAD_STAGE.isDone()) {
            TXLoaderCore.LOGGER.info("Awaiting startup tasks...");
            try {
                LOAD_STAGE.join();
            } catch (Exception e) {
                TXLoaderCore.LOGGER.warn("A future completed exceptionally!", e);
            }
        }

        // fetch assets (directly or from JARs), this implicitly includes downloads of version details, asset indices
        // and JARs
        while (true) {
            Set<CompletableFuture<Void>> snapshot;
            synchronized (BLOCKING_FUTURES) {
                if (BLOCKING_FUTURES.isEmpty()) {
                    break;
                }
                snapshot = new HashSet<>(BLOCKING_FUTURES);
                BLOCKING_FUTURES.clear();
            }

            TXLoaderCore.LOGGER.info("Awaiting {} assets...", snapshot.size());

            for (CompletableFuture<Void> future : snapshot) {
                try {
                    future.join();
                } catch (Exception e) {
                    TXLoaderCore.LOGGER.warn("A future completed exceptionally!", e);
                }
            }
        }

        TXLoaderCore.LOGGER.info("Done!");
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

        Path downloadJar(String version, String fileName) throws Exception {
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

        void download(Path path) throws Exception {
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
