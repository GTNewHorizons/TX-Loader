package glowredman.txloader;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Asset {

    String resourceLocation;
    String resourceLocationOverride;
    boolean forceLoad;
    String version;
    Source source;
    transient boolean addedByMod; // ignored by GSON

    Asset(String resourceLocation, String version, Source source) {
        this.resourceLocation = resourceLocation;
        this.version = version;
        this.source = source;
    }

    String getResourceLocation() {
        return this.resourceLocationOverride == null ? this.resourceLocation : this.resourceLocationOverride;
    }

    File getFile() {
        File path = this.forceLoad ? TXLoaderCore.forceResourcesDir : TXLoaderCore.resourcesDir;
        return new File(path, this.getResourceLocation());
    }

    String getVersion() {
        return this.version == null ? RemoteHandler.latestRelease : this.version;
    }

    Source getSource() {
        return this.source == null ? Source.ASSET : this.source;
    }

    public enum Source {

        ASSET,
        CLIENT,
        SERVER;

        static final Iterable<String> NAMES = Arrays.stream(values()).map(Source::name).collect(Collectors.toList());

        static Source get(String name) {
            try {
                return valueOf(name);
            } catch (Exception e) {
                TXLoaderCore.LOGGER.warn("{} is not a valid source identifier!", name);
                return ASSET;
            }
        }
    }
}
