package glowredman.txloader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
@TransformerExclusions("glowredman.txloader")
@SortingIndex(1001)
@MCVersion("1.7.10")
public class TXLoaderCore implements IFMLLoadingPlugin {

    static final Logger LOGGER = LogManager.getLogger("TX Loader");
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final List<Asset> REMOTE_ASSETS = new ArrayList<>();
    static File modFile;
    static File mcLocation;
    static File configDir;
    static File resourcesDir;
    static File forceResourcesDir;
    static boolean isRemoteReachable;

    @Override
    public String[] getASMTransformerClass() {
        if (FMLLaunchHandler.side().isClient()) {
            return new String[] { MinecraftClassTransformer.class.getName() };
        }
        return null;
    }

    @Override
    public String getModContainerClass() {
        if (FMLLaunchHandler.side().isClient()) {
            return "glowredman.txloader.TXLoaderModContainer";
        }
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        if (FMLLaunchHandler.side().isServer()) {
            return;
        }

        modFile = (File) data.get("coremodLocation");
        mcLocation = (File) data.get("mcLocation");
        configDir = new File(mcLocation, "config" + File.separator + "txloader");
        resourcesDir = new File(configDir, "load");
        resourcesDir.mkdirs();
        forceResourcesDir = new File(configDir, "forceload");
        forceResourcesDir.mkdirs();

        isRemoteReachable = RemoteHandler.getVersions();
        JarHandler.indexJars();
        ConfigHandler.load();
        ConfigHandler.moveRLAssets();
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
