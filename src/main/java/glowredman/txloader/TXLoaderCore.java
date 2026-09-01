package glowredman.txloader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.Name;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.SortingIndex;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions;
import glowredman.txloader.RemoteHandler.JVersionManifest;

@Name("TX Loader Core")
@TransformerExclusions({ "glowredman.txloader.TXLoaderCore", "glowredman.txloader.MinecraftClassTransformer" })
@SortingIndex(1001)
@MCVersion("1.7.10")
public class TXLoaderCore implements IFMLLoadingPlugin {

    static final Logger LOGGER = LogManager.getLogger("TX Loader");
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final Executor EXECUTOR_IO;
    static final Executor EXECUTOR_NET;
    static File modFile;
    static Path mcLocation;
    static Path configDir;
    static Path resourcesDir;
    static Path forceResourcesDir;
    static Path tempDir;

    static {
        // get arguments
        int poolSizeIO = Integer.getInteger("txloader.poolsize.io", 32);
        int poolSizeNet = Integer.getInteger("txloader.poolsize.net", 16);
        long keepAliveIO = Long.getLong("txloader.keepalive.io", 10000);
        long keepAliveNet = Long.getLong("txloader.keepalive.net", 10000);

        // check for invalid values
        if (poolSizeIO < 1) {
            LOGGER.warn("-Dtxloader.poolsize.io must be positive ({}), ignoring argument", poolSizeIO);
            poolSizeIO = 32;
        }
        if (poolSizeNet < 1) {
            LOGGER.warn("-Dtxloader.poolsize.net must be positive ({}), ignoring argument", poolSizeNet);
            poolSizeNet = 16;
        }
        if (keepAliveIO < 1) {
            LOGGER.warn("-Dtxloader.keepalive.io must be positive ({}), ignoring argument", keepAliveIO);
            keepAliveIO = 10000;
        }
        if (keepAliveNet < 1) {
            LOGGER.warn("-Dtxloader.keepalive.net must be positive ({}), ignoring argument", keepAliveNet);
            keepAliveNet = 10000;
        }

        // construct executors
        EXECUTOR_IO = new ThreadPoolExecutor(
                poolSizeIO,
                poolSizeIO,
                keepAliveIO,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactoryBuilder().setNameFormat("TX Loader IO #%d").setDaemon(true).build());

        EXECUTOR_NET = new ThreadPoolExecutor(
                poolSizeNet,
                poolSizeNet,
                keepAliveNet,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactoryBuilder().setNameFormat("TX Loader NET #%d").setDaemon(true).build());

        ((ThreadPoolExecutor) EXECUTOR_IO).allowCoreThreadTimeOut(true);
        ((ThreadPoolExecutor) EXECUTOR_NET).allowCoreThreadTimeOut(true);
    }

    @Override
    public String[] getASMTransformerClass() {
        if (FMLLaunchHandler.side().isClient()) {
            return new String[] { "glowredman.txloader.MinecraftClassTransformer" };
        }
        return null;
    }

    @Override
    public String getModContainerClass() {
        return "glowredman.txloader.TXLoaderModContainer";
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        modFile = (File) data.get("coremodLocation");
        mcLocation = ((File) data.get("mcLocation")).toPath();
        configDir = mcLocation.resolve("config").resolve("txloader");
        resourcesDir = configDir.resolve("load");
        forceResourcesDir = configDir.resolve("forceload");
        tempDir = configDir.resolve("temp");

        if (preStartup()) {
            postStartup();
            return;
        }

        CompletableFuture.allOf(
                CompletableFuture.runAsync(ConfigHandler::moveRLAssets, EXECUTOR_IO)
                        .thenRunAsync(ConfigHandler::load, EXECUTOR_IO),
                CompletableFuture.supplyAsync(RemoteHandler::fetchVersions, EXECUTOR_NET)
                        .thenAccept(RemoteHandler.VERSIONS_STAGE::complete))
                .whenComplete((void_, t) -> {
                    // ensure that VERSIONS_STAGE is completed no matter what
                    // config is loaded now -> complete LOAD_STAGE to unblock RemoteHandler.ensureNoBlocking()
                    postStartup();
                    // log error if one occured
                    if (t != null) {
                        LOGGER.error("An error occured in any of the startup tasks", t);
                    }
                });
    }

    /**
     * @return {@code true} if the startup stages should be completed immediately (instead of actually fetching the
     *         version manifest etc.)
     */
    private static boolean preStartup() {
        try {
            Files.createDirectories(resourcesDir);
            Files.createDirectories(forceResourcesDir);
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create resource directories!", e);
            return true;
        }

        try {
            FileUtils.cleanDirectory(tempDir.toFile());
        } catch (Exception e) {
            // This is not a condition to skip normal startup, so only log the exception and proceed
            LOGGER.warn("Failed to clean {}", tempDir, e);
        }

        if (FMLLaunchHandler.side().isServer()) {
            ServerLangHelper.load();
            return true;
        }

        return JarHandler.initCache();
    }

    /**
     * Ensures that both startup stages are completed
     */
    private static void postStartup() {
        RemoteHandler.VERSIONS_STAGE.complete(JVersionManifest.DUMMY);
        RemoteHandler.LOAD_STAGE.complete(null);
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    /**
     *
     * @deprecated {@link #getAssetBuilder(String, String)} should be used instead. This method exists for backwards
     *             compatibility. It assumes version 26.2 as "latest" version.
     * @param resourceLocation The ResourceLocation used to identify the asset on Mojang's side. Example:
     *                         <code>minecraft/lang/en_us.lang</code>
     * @return An {@link AssetBuilder} object to specify further properties
     * @author glowredman
     */
    @Deprecated
    public static AssetBuilder getAssetBuilder(String resourceLocation) {
        return new AssetBuilder(resourceLocation, Asset.LATEST_VERSION);
    }

    /**
     *
     * @param resourceLocation The ResourceLocation used to identify the asset on Mojang's side. Example:
     *                         <code>minecraft/lang/en_us.lang</code>
     * @return An {@link AssetBuilder} object to specify further properties
     * @since 1.9.0
     * @author glowredman
     */
    public static AssetBuilder getAssetBuilder(String resourceLocation, String version) {
        return new AssetBuilder(resourceLocation, version);
    }
}
