package glowredman.txloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

import javax.annotation.Nonnull;

import org.apache.commons.io.IOUtils;

import com.google.gson.JsonSyntaxException;

import glowredman.txloader.Asset.Source;

class RemoteHandler {

    static CompletableFuture<JVersionManifest> versionsStage;
    private static final Map<String, CompletableFuture<JVersionDetails>> DETAILS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Map<String, JAsset>>> ASSET_INDICES = new ConcurrentHashMap<>();
    static final Set<CompletableFuture<Void>> BLOCKING_FUTURES = new HashSet<>();
    private static final Set<Path> PATHS = new HashSet<>();

    static JVersionManifest fetchVersions() {
        JVersionManifest manifest;

        try {
            manifest = downloadManifest();
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to get version manifest!", e);
            manifest = new JVersionManifest();
            manifest.urls = Collections.emptyMap();
            return manifest;
        }

        Map<String, String> urls = new ConcurrentHashMap<>(manifest.versions.size(), 1.0f);
        for (JVersion version : manifest.versions) {
            urls.put(version.id, version.url);
        }
        manifest.urls = urls;

        TXLoaderCore.LOGGER.info("Successfully fetched Minecraft versions.");
        return manifest;
    }

    private static JVersionManifest downloadManifest() throws JsonSyntaxException, IOException {
        final URL manifestURL = new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json");
        return TXLoaderCore.GSON
                .fromJson(IOUtils.toString(manifestURL, StandardCharsets.UTF_8), JVersionManifest.class);
    }

    static CompletableFuture<Void> fetchAsset(@Nonnull Asset asset) {
        Path path = asset.getPath();
        String version = asset.getVersion();
        Source source = asset.getSource();

        if (PATHS.contains(path)) {
            TXLoaderCore.LOGGER.warn(
                    "Duplicate asset defined for {}, skipping {} on version {} for source {}",
                    asset.getResourceLocation(),
                    asset.resourceLocation,
                    version,
                    source);
            return CompletableFuture.completedFuture(null);
        }

        PATHS.add(path);

        if (Files.exists(path)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<JVersionDetails> detailsStage = DETAILS
                .computeIfAbsent(version, RemoteHandler::verifyDetailsExist);

        if (source == Source.ASSET) {
            CompletableFuture<Void> future = ASSET_INDICES.computeIfAbsent(
                    version,
                    v -> detailsStage
                            .thenApplyAsync(details -> verifyAssetIndexExists(v, details), TXLoaderCore.EXECUTOR_NET))
                    .thenAcceptAsync(
                            assetIndex -> fetchDirect(assetIndex, asset, path, version),
                            TXLoaderCore.EXECUTOR_NET);
            BLOCKING_FUTURES.add(future);
            return future;
        }

        // asset from client/server jar:
        boolean isClient = source == Source.CLIENT;
        CompletableFuture<Void> future = (isClient ? JarHandler.CACHED_CLIENT_JARS : JarHandler.CACHED_SERVER_JARS)
                .computeIfAbsent(
                        version,
                        v -> detailsStage.thenApplyAsync(
                                details -> verifyJarExists(details, v, isClient),
                                TXLoaderCore.EXECUTOR_NET))
                .thenAcceptAsync(jarPath -> fetchFromJar(asset, jarPath, path), TXLoaderCore.EXECUTOR_IO);
        BLOCKING_FUTURES.add(future);
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

            try {
                return TXLoaderCore.GSON.fromJson(Files.newBufferedReader(path), JVersionDetails.class);
            } catch (Exception e) {
                TXLoaderCore.LOGGER.error("Failed to get version details for version {}", e);
                return null;
            }
        }, TXLoaderCore.EXECUTOR_NET);
    }

    private static Map<String, JAsset> verifyAssetIndexExists(String version, JVersionDetails details) {
        Path path = JarHandler.assetIndex.resolve(version + ".json");

        if (Files.notExists(path)) {
            if (details == null) {
                return Collections.emptyMap();
            }

            try {
                download(details.assetIndex.url, path);
            } catch (Exception e) {
                TXLoaderCore.LOGGER.error("Failed to download asset index for version {}!", version, e);
                return Collections.emptyMap();
            }
        }

        try {
            return TXLoaderCore.GSON.fromJson(Files.newBufferedReader(path), JAssetIndex.class).objects;
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to get asset index for version {}!", version, e);
            return Collections.emptyMap();
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
        JAsset jAsset = assets.get(asset.resourceLocation);

        if (jAsset == null) {
            TXLoaderCore.LOGGER.error("Failed to find asset {} for version {}!", asset.resourceLocation, version);
            return;
        }

        try {
            jAsset.download(path);
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to get asset! Path: {}", asset.resourceLocation, e);
        }

        TXLoaderCore.LOGGER.debug("Successfully fetched {}", asset.resourceLocation);
    }

    private static void fetchFromJar(Asset asset, Path jarPath, Path targetPath) {
        if (jarPath == null) {
            return;
        }

        try (JarFile jarFile = new JarFile(jarPath.toFile());
                InputStream is = jarFile.getInputStream(jarFile.getJarEntry("assets/" + asset.resourceLocation))) {
            Files.createDirectories(targetPath.getParent());
            Files.copy(is, targetPath);
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to extract asset from jar! Path: {}", asset.resourceLocation, e);
            return;
        }

        TXLoaderCore.LOGGER.debug("Successfully fetched {}", asset.resourceLocation);
    }

    private static void download(String url, Path path) throws IOException {
        TXLoaderCore.LOGGER.info("Downloading {} to {}", url, path);
        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(10000);
        try (InputStream is = connection.getInputStream()) {
            Files.copy(is, path);
        }
    }

    /**
     * Ensures that no assets are currently being fetched (from any {@link Source})
     */
    static void ensureNoBlocking() {
        synchronized (BLOCKING_FUTURES) {
            for (CompletableFuture<Void> future : BLOCKING_FUTURES) {
                if (future.isDone()) {
                    continue;
                }
                try {
                    future.join();
                } catch (Exception e) {
                    TXLoaderCore.LOGGER.warn("", e);
                }
            }
            BLOCKING_FUTURES.clear();
        }
    }

    /*
     * JSON templates
     */

    static class JVersionManifest {

        JLatest latest;
        List<JVersion> versions;
        transient Map<String, String> urls;
    }

    static class JLatest {

        String release;
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
            sb.append("https://resources.download.minecraft.net/");
            sb.append(this.hash, 0, 2);
            sb.append('/');
            sb.append(this.hash);
            RemoteHandler.download(sb.toString(), path);
        }
    }
}
