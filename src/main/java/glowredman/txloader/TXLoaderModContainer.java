package glowredman.txloader;

import java.io.File;
import java.util.jar.JarFile;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.MetadataCollection;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.versioning.VersionParser;
import cpw.mods.fml.common.versioning.VersionRange;
import glowredman.txloader.progress.ProgressBarProxy;

public class TXLoaderModContainer extends DummyModContainer {

    public TXLoaderModContainer() {
        super(getModMetadata());
    }

    private static ModMetadata getModMetadata() {
        try (JarFile jar = new JarFile(TXLoaderCore.modFile)) {
            return MetadataCollection.from(jar.getInputStream(jar.getEntry("mcmod.info")), "TX Loader")
                    .getMetadataForId("txloader", null);
        } catch (Exception e) {
            TXLoaderCore.LOGGER.warn("Failed to get mod metadata", e);
        }
        ModMetadata fallback = new ModMetadata();
        fallback.description = "This is a fallback description! Something went wrong while getting this mod's metadata. Refer to the log and report this issue to the mod's author!";
        fallback.modId = "txloader";
        fallback.name = "TX Loader";
        fallback.version = "0.0-FALLBACK";
        return fallback;
    }

    @Override
    public VersionRange acceptableMinecraftVersionRange() {
        return VersionParser.parseRange("[1.7.10]");
    }

    @Override
    public File getSource() {
        return TXLoaderCore.modFile;
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        bus.register(this);
        return true;
    }

    @Subscribe
    public void preInit(FMLPreInitializationEvent event) {
        ProgressBarProxy.isBLSLoaded = Loader.isModLoaded("betterloadingscreen");
    }

    @Subscribe
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandTX());
    }
}
