package glowredman.txloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.io.IOUtils;

import com.google.gson.JsonSyntaxException;

import glowredman.txloader.Asset.Source;

class RemoteHandler {

    static volatile @Nullable String latestRelease;
    static @Nullable CompletableFuture<Void> versionsStage;
    static final Map<String, JVersion> VERSIONS = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<JVersionDetails, Map<String, JAsset>> ASSETS = new ConcurrentHashMap<>();
    private static final Map<String, JVersionDetails> VERSION_DETAILS_CACHE = new ConcurrentHashMap<>();
    private static final Object LOCK = new Object();

    static void fetchVersions() {
        JVersionManifest manifest;

        try {
            manifest = downloadManifest();
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to get Minecraft versions!", e);
            throw new CompletionException(e);
        }

        latestRelease = manifest.latest.release;

        synchronized (VERSIONS) {
            manifest.versions.forEach(JVersion::cache);
        }

        TXLoaderCore.LOGGER.info("Successfully fetched Minecraft versions.");
    }

    @Nonnull
    static CompletableFuture<Void> fetchAsset(@Nonnull Asset asset) {
        Path path = asset.getPath();
        if (versionsStage == null || Files.exists(path)) {
            return CompletableFuture.completedFuture(null);
        }

        String version = asset.getVersion();
        Source source = asset.getSource();

        if (source == Source.ASSET) {
            // By always calling thenRunAsync() on the original CompletableFuture, multiple tasks can run concurrently.
            return versionsStage.thenRunAsync(() -> fetchDirect(asset, path, version), TXLoaderCore.EXECUTOR);
        }

        // asset from client/server jar:
        synchronized (LOCK) {
            // By always re-assigning the CompletableFuture, only one JAR will be fetched at a time. We want this
            // because it avoids downloading the same jar multiple times.
            return (source == Source.CLIENT ? JarHandler.CACHED_CLIENT_JARS : JarHandler.CACHED_SERVER_JARS)
                    .computeIfAbsent(version, v -> downloadJar(asset, v, source))
                    .thenAcceptAsync(jarPath -> fetchFromJar(asset, jarPath, path), TXLoaderCore.EXECUTOR);
        }
    }

    private static void fetchDirect(Asset asset, Path path, String version) {
        JVersionDetails versionDetails = VERSION_DETAILS_CACHE.computeIfAbsent(version, RemoteHandler::downloadDetails);

        if (versionDetails == null) {
            TXLoaderCore.LOGGER
                    .error("Failed to get details for version {}! Path: {}", version, asset.resourceLocation);
            return;
        }

        JAsset jAsset = ASSETS.computeIfAbsent(versionDetails, JVersionDetails::getAssets).get(asset.resourceLocation);

        if (jAsset == null) {
            TXLoaderCore.LOGGER.error("Failed to find asset {} for version {}!", asset.resourceLocation, version);
            return;
        }

        try {
            Files.createDirectories(path.getParent());
            jAsset.download(path);
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to get asset! Path: {}", asset.resourceLocation, e);
        }

        TXLoaderCore.LOGGER.debug("Successfully fetched {}", asset.resourceLocation);
    }

    private static CompletableFuture<Path> downloadJar(Asset asset, String version, Source source) {
        return JarHandler.cacheStage.thenApplyAsync(v -> {
            JVersionDetails versionDetails = VERSION_DETAILS_CACHE
                    .computeIfAbsent(version, RemoteHandler::downloadDetails);

            if (versionDetails == null) {
                TXLoaderCore.LOGGER
                        .error("Failed to get details for version {}! Path: {}", version, asset.resourceLocation);
                return null;
            }

            if (source == Source.CLIENT) {
                try {
                    return versionDetails.downloads.client.downloadJar(version, "client.jar");
                } catch (Exception e) {
                    TXLoaderCore.LOGGER.error("Failed to download client jar and no cached jar was found", e);
                    return null;
                }
            }
            try {
                return versionDetails.downloads.server.downloadJar(version, "server.jar");
            } catch (Exception e) {
                TXLoaderCore.LOGGER.error("Failed to download server jar and no cached jar was found", e);
                return null;
            }
        }, TXLoaderCore.EXECUTOR);
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

    private static JVersionManifest downloadManifest() throws JsonSyntaxException, IOException {
        final URL manifestURL = new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json");
        return TXLoaderCore.GSON.get()
                .fromJson(IOUtils.toString(manifestURL, StandardCharsets.UTF_8), JVersionManifest.class);
    }

    private static JVersionDetails downloadDetails(String version) {
        try {
            final URL versionURL;
            synchronized (VERSIONS) {
                versionURL = new URL(VERSIONS.get(version).url);
            }
            return TXLoaderCore.GSON.get()
                    .fromJson(IOUtils.toString(versionURL, StandardCharsets.UTF_8), JVersionDetails.class);
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to get version details", e);
            return null;
        }
    }

    /*
     * JSON templates
     */

    static class JVersionManifest {

        JLatest latest;
        List<JVersion> versions;
    }

    static class JLatest {

        String release;
    }

    static class JVersion {

        String id;
        String url;

        void cache() {
            VERSIONS.put(this.id, this);
        }
    }

    static class JVersionDetails {

        JSourceDetails assetIndex;
        JDownloads downloads;

        Map<String, JAsset> getAssets() {
            try {
                final URL assetsURL = new URL(this.assetIndex.url);
                return TXLoaderCore.GSON.get()
                        .fromJson(IOUtils.toString(assetsURL, StandardCharsets.UTF_8), JAssetIndex.class).objects;
            } catch (Exception e) {
                TXLoaderCore.LOGGER.error("Failed to get asset index", e);
                // don't check this version again...
                return new HashMap<>();
            }
        }
    }

    static class JSourceDetails {

        String url;

        Path downloadJar(String version, String fileName) throws IOException {
            Path dir = JarHandler.txloaderCache.resolve(version);
            Files.createDirectories(dir);
            Path jar = dir.resolve(fileName);
            TXLoaderCore.LOGGER.info("Downloading {} to {}", this.url, jar);
            URLConnection connection = new URL(this.url).openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(10000);
            try (InputStream is = connection.getInputStream()) {
                Files.copy(is, jar);
            }
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
            URL url = this.getURL();
            Files.createDirectories(path.getParent());
            TXLoaderCore.LOGGER.info("Downloading {} to {}", url, path);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(10000);
            try (InputStream is = connection.getInputStream()) {
                Files.copy(is, path);
            }
        }

        URL getURL() throws MalformedURLException {
            StringBuilder sb = new StringBuilder(84);
            sb.append("https://resources.download.minecraft.net/");
            sb.append(this.hash, 0, 2);
            sb.append('/');
            sb.append(this.hash);
            return new URL(sb.toString());
        }
    }
}
