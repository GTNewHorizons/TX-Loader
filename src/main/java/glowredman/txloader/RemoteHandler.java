package glowredman.txloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import org.apache.commons.io.IOUtils;

import com.google.gson.JsonSyntaxException;

import glowredman.txloader.Asset.Source;

class RemoteHandler {

    static volatile String latestRelease;
    static final Map<String, JVersion> VERSIONS = new LinkedHashMap<>();
    private static final Map<JVersionDetails, Map<String, JAsset>> ASSETS = new HashMap<>();
    private static final Map<String, JVersionDetails> VERSION_DETAILS_CACHE = new HashMap<>();

    static boolean getVersions() {
        JVersionManifest manifest;

        try {
            manifest = downloadManifest();
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to get Minecraft versions!", e);
            return false;
        }

        latestRelease = manifest.latest.release;

        synchronized (VERSIONS) {
            manifest.versions.forEach(JVersion::cache);
        }

        TXLoaderCore.LOGGER.info("Successfully fetched Minecraft versions.");

        return true;
    }

    static void getAsset(Asset asset) {
        Path path = asset.getPath();
        if (Files.exists(path)) {
            return;
        }

        String version = asset.getVersion();

        JVersionDetails versionDetails = VERSION_DETAILS_CACHE.computeIfAbsent(version, RemoteHandler::downloadDetails);
        if (versionDetails == null) {
            TXLoaderCore.LOGGER
                    .error("Failed to get details for version {}! Path: {}", version, asset.resourceLocation);
            return;
        }

        Source source = asset.getSource();

        if (source == Source.ASSET) {
            JAsset jAsset = ASSETS.computeIfAbsent(versionDetails, JVersionDetails::getAssets)
                    .get(asset.resourceLocation);

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
            return;
        }

        // asset from client/server jar:
        Path jarPath = source == Source.CLIENT ? JarHandler.CACHED_CLIENT_JARS.get(version)
                : JarHandler.CACHED_SERVER_JARS.get(version);
        if (jarPath == null) {
            if (source == Source.CLIENT) {
                try {
                    jarPath = versionDetails.downloads.client.downloadJar(version, "client.jar");
                    JarHandler.CACHED_CLIENT_JARS.put(version, jarPath);
                } catch (Exception e) {
                    TXLoaderCore.LOGGER.error("Failed to download client jar and no cached jar was found", e);
                    return;
                }
            } else {
                try {
                    jarPath = versionDetails.downloads.server.downloadJar(version, "server.jar");
                    JarHandler.CACHED_SERVER_JARS.put(version, jarPath);
                } catch (Exception e) {
                    TXLoaderCore.LOGGER.error("Failed to download server jar and no cached jar was found", e);
                    return;
                }
            }
        }

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            InputStream is = jarFile.getInputStream(jarFile.getJarEntry("assets/" + asset.resourceLocation));
            Files.createDirectories(path.getParent());
            Files.copy(is, path);
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
