package glowredman.txloader;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.filefilter.DirectoryFileFilter;

import cpw.mods.fml.common.ModContainer;

public class TXResourcePack implements IResourcePack {

    private final String name;
    private final Path dir;

    private TXResourcePack(String name, Path dir) {
        this.name = name;
        this.dir = dir;
    }

    @Override
    public InputStream getInputStream(ResourceLocation rl) throws IOException {
        return new FileInputStream(this.getResourcePath(rl).toFile());
    }

    @Override
    public boolean resourceExists(ResourceLocation rl) {
        try {
            return Files.exists(this.getResourcePath(rl));
        } catch (InvalidPathException e) {
            /*
             * Some mods load resources dynamically by id. (example: java.nio.file.InvalidPathException: Illegal char
             * <:> at index 30: textures/blocks/bw_(extrautils:golden_bag)_n.png.mcmeta)
             */
            if (rl.getResourcePath().contains(":")) {
                return false;
            }
            throw e;
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Set getResourceDomains() {
        if (TXLoaderCore.isRemoteReachable) {
            RemoteHandler.getAssets();
        }

        File[] subDirs = this.dir.toFile().listFiles((FileFilter) DirectoryFileFilter.DIRECTORY);
        Set<String> resourceDomains = new HashSet<>();
        for (File f : subDirs) {
            resourceDomains.add(f.getName());
        }
        return resourceDomains;
    }

    @Override
    public IMetadataSection getPackMetadata(IMetadataSerializer p_135058_1_, String p_135058_2_) throws IOException {
        return null;
    }

    @Override
    public BufferedImage getPackImage() {
        return null;
    }

    @Override
    public String getPackName() {
        return this.name;
    }

    private Path getResourcePath(ResourceLocation rl) {
        return this.dir.resolve(rl.getResourceDomain()).resolve(rl.getResourcePath());
    }

    public static class Normal extends TXResourcePack {

        public Normal(ModContainer modContainer) {
            super("TX Loader Resources", TXLoaderCore.resourcesDir.toPath());
            TXLoaderCore.resourcesDir.mkdir();
        }
    }

    public static class Force extends TXResourcePack {

        public Force() {
            super("TX Loader Forced Resources", TXLoaderCore.forceResourcesDir.toPath());
            TXLoaderCore.forceResourcesDir.mkdir();
        }
    }
}
