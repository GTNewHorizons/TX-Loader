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
    static final Executor EXECUTOR_IO = new ThreadPoolExecutor(
            512,
            512,
            10,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder().setNameFormat("TX Loader IO #%d").setDaemon(true).build());
    static final Executor EXECUTOR_NET = new ThreadPoolExecutor(
            32,
            32,
            10,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder().setNameFormat("TX Loader NET #%d").setDaemon(true).build());
    static File modFile;
    static Path mcLocation;
    static Path configDir;
    static Path resourcesDir;
    static Path forceResourcesDir;

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

        try {
            Files.createDirectories(resourcesDir);
            Files.createDirectories(forceResourcesDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create resource directories!", e);
            return;
        }

        if (FMLLaunchHandler.side().isServer()) {
            ServerLangHelper.load();
            return;
        }

        if (JarHandler.initCache()) {
            RemoteHandler.versionsStage.complete(JVersionManifest.DUMMY);
            return;
        }

        ((ThreadPoolExecutor) EXECUTOR_IO).allowCoreThreadTimeOut(true);
        ((ThreadPoolExecutor) EXECUTOR_NET).allowCoreThreadTimeOut(true);

        RemoteHandler.versionsStage = CompletableFuture.runAsync(ConfigHandler::moveRLAssets, EXECUTOR_IO)
                .thenCombineAsync(
                        CompletableFuture.supplyAsync(RemoteHandler::fetchVersions, EXECUTOR_NET),
                        (void_, manifest) -> {
                            ConfigHandler.load();
                            return manifest;
                        },
                        EXECUTOR_IO);
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
