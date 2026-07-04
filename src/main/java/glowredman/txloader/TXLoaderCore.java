package glowredman.txloader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.Name;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.SortingIndex;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions;

@Name("TX Loader Core")
@TransformerExclusions({ "glowredman.txloader.TXLoaderCore", "glowredman.txloader.MinecraftClassTransformer" })
@SortingIndex(1001)
@MCVersion("1.7.10")
public class TXLoaderCore implements IFMLLoadingPlugin {

    static final Logger LOGGER = LogManager.getLogger("TX Loader");
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final Executor EXECUTOR = Executors.newCachedThreadPool();
    static File modFile;
    static Path mcLocation;
    static Path configDir;
    static Path resourcesDir;
    static Path forceResourcesDir;

    static {
        ((ThreadPoolExecutor) EXECUTOR).setKeepAliveTime(10, TimeUnit.SECONDS);
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

        try {
            Files.createDirectories(resourcesDir);
            Files.createDirectories(forceResourcesDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create resource directories!", e);
            return;
        }

        RemoteHandler.versionsStage = CompletableFuture.runAsync(RemoteHandler::fetchVersions, EXECUTOR);
        JarHandler.cacheStage = RemoteHandler.versionsStage.thenRunAsync(JarHandler::indexJars, EXECUTOR)
                .thenRunAsync(ConfigHandler::moveRLAssets, EXECUTOR);
        JarHandler.cacheStage.thenRunAsync(ConfigHandler::load, EXECUTOR);
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    /**
     *
     * @param resourceLocation The ResourceLocation used to identify the asset on Mojang's side. Example:
     *                         <code>minecraft/lang/en_us.lang</code>
     * @return An {@link AssetBuilder} object to specify further properties
     * @author glowredman
     */
    public static AssetBuilder getAssetBuilder(String resourceLocation) {
        return new AssetBuilder(resourceLocation);
    }
}
