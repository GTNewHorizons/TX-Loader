package glowredman.txloader;

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

public class TXResourcePack implements IResourcePack {

    /**
     * Folders under the load directory are named "<DisplayName>[<domain>]" for readability, but a resource domain must
     * be the bare mod id. This extracts the domain out of the brackets so folder names map to the same domain a
     * {@link ResourceLocation} for that mod actually carries.
     */
    private static final Pattern BRACKETED_DOMAIN = Pattern.compile("\\[([^\\[\\]]+)\\]$");

    private final String name;
    private final Path dir;

    /**
     * Lazily built, and rebuilt on every {@link #getResourceDomains()} call (which Minecraft calls on every resource
     * manager reload), so it stays in sync if folders are added/removed/renamed.
     */
    private volatile Map<String, Path> domainToFolder;

    public TXResourcePack(String name, Path dir) {
        this.name = name;
        this.dir = dir;
    }

    private static String extractDomain(String folderName) {
        Matcher matcher = BRACKETED_DOMAIN.matcher(folderName);
        return matcher.find() ? matcher.group(1) : folderName;
    }

    private Map<String, Path> getDomainToFolder() {
        Map<String, Path> map = this.domainToFolder;
        if (map != null) {
            return map;
        }
        return buildDomainToFolder();
    }

    private Map<String, Path> buildDomainToFolder() {
        Map<String, Path> built = new HashMap<>();
        try (Stream<Path> dirs = Files.list(this.dir).filter(Files::isDirectory)) {
            dirs.forEach(p -> built.put(extractDomain(p.getFileName().toString()), p));
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Failed to build domain map of directory {}", this.dir, e);
        }
        this.domainToFolder = built;
        return built;
    }

    @Override
    public InputStream getInputStream(ResourceLocation rl) throws IOException {
        return new FileInputStream(this.getResourcePath(rl).toFile());
    }

    @Override
    public boolean resourceExists(ResourceLocation rl) {
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
        if (TXLoaderCore.isRemoteReachable) {
            RemoteHandler.getAssets();
        }

        // Minecraft calls this on every resource manager reload, so use it to keep the cached
        // domain -> folder map (used by getResourcePath) in sync too.
        return new HashSet<>(buildDomainToFolder().keySet());
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
        Path base = getDomainToFolder().get(rl.getResourceDomain());
        if (base == null) {
            // Fall back to the old, direct behavior in case the domain -> folder map hasn't been
            // built yet (getResourceDomains() not called) or genuinely has no matching folder.
            base = this.dir.resolve(rl.getResourceDomain());
        }
        return base.resolve(rl.getResourcePath());
    }
}
