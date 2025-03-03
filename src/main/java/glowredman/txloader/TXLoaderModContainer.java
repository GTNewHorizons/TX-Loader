package glowredman.txloader;

import java.io.File;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.MetadataCollection;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.versioning.VersionParser;
import cpw.mods.fml.common.versioning.VersionRange;
import glowredman.txloader.progress.ProgressBarProxy;

public class TXLoaderModContainer extends DummyModContainer {

    public TXLoaderModContainer() {
        super(
                MetadataCollection.from(TXLoaderModContainer.class.getResourceAsStream("/mcmod.info"), "TX Loader")
                        .getMetadataForId("txloader", null));
    }

    @Override
    public VersionRange acceptableMinecraftVersionRange() {
        return VersionParser.parseRange("[1.7.10]");
    }

    @Override
    public Class<?> getCustomResourcePackClass() {
        return TXResourcePack.Normal.class;
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
