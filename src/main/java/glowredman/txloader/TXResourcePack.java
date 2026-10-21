package glowredman.txloader;

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

public class TXResourcePack implements IResourcePack {

    static volatile TXResourcePack instanceNormal;
    static volatile TXResourcePack instanceForce;
    volatile boolean dirty;

    private final String name;
    private final Path dir;
    private volatile Set<ResourceLocation> resources;

    public TXResourcePack(String name, Path dir) {
        this.name = name;
        this.dir = dir;
    }

    @Override
    public InputStream getInputStream(ResourceLocation rl) throws IOException {
        return new FileInputStream(this.getResourcePath(rl).toFile());
    }

    @Override
    public boolean resourceExists(ResourceLocation rl) {
        RemoteHandler.ensureNoBlocking();

        if (this.dirty) {
            this.indexResources();
            this.dirty = false;
        }

        Set<ResourceLocation> indexedResources = this.resources;
        if (indexedResources != null) {
            return indexedResources.contains(rl);
        }

        try {
            return getResourcePath(rl).toFile().exists();
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

    @Override
    public Set<String> getResourceDomains() {
        RemoteHandler.ensureNoBlocking();
        this.indexResources();

        Set<String> resourceDomains = new HashSet<>();
        try (Stream<Path> dirs = Files.list(this.dir)) {
            dirs.filter(Files::isDirectory).forEach(p -> resourceDomains.add(p.getFileName().toString()));
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to get resource domains of directory {}", this.dir, e);
        }
        return resourceDomains;
    }

    private void indexResources() {
        Set<ResourceLocation> indexedResources = new HashSet<>();
        String separator = this.dir.getFileSystem().getSeparator();
        int rootLength = this.dir.toString().length() + separator.length();
        try {
            /*
             * Walking everything with FOLLOW_LINKS checks each directory against all its parents for loops, which is
             * slow on Windows. Only symlinked directories need that.
             */
            Files.walkFileTree(this.dir, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
                    if (!path.equals(TXResourcePack.this.dir)) {
                        addResource(indexedResources, path, separator, rootLength);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    addResource(indexedResources, path, separator, rootLength);
                    if (attrs.isSymbolicLink() && Files.isDirectory(path)) {
                        try (Stream<Path> linked = Files.walk(path, FileVisitOption.FOLLOW_LINKS)) {
                            linked.skip(1).forEach(p -> addResource(indexedResources, p, separator, rootLength));
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            this.resources = indexedResources;
        } catch (Exception e) {
            this.resources = null;
            TXLoaderCore.LOGGER.error("Failed to index resources of directory {}", this.dir, e);
        }
    }

    private static void addResource(Set<ResourceLocation> indexedResources, Path path, String separator,
            int rootLength) {
        String pathString = path.toString();
        int separatorIndex = pathString.indexOf(separator, rootLength);
        if (separatorIndex < 0) return; // skipping domain paths, can't build a RL with it

        String resourcePath = pathString.substring(separatorIndex + separator.length());
        if (!separator.equals("/")) resourcePath = resourcePath.replace(separator, "/");
        indexedResources.add(new ResourceLocation(pathString.substring(rootLength, separatorIndex), resourcePath));
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
}
