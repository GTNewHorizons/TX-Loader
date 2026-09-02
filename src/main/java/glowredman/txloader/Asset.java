package glowredman.txloader;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.google.common.base.Objects;

public class Asset {

    // setting the version is no longer optional, this is for backwards compatibility
    static final String LATEST_VERSION = "26.2";

    String resourceLocation;
    String resourceLocationOverride;
    boolean forceLoad;
    String version;
    Source source;

    Asset(String resourceLocation, String version, Source source) {
        this.resourceLocation = resourceLocation;
        this.version = version;
        this.source = source;
    }

    String getResourceLocation() {
        return this.resourceLocationOverride == null ? this.resourceLocation : this.resourceLocationOverride;
    }

    Path getPath() {
        Path path = this.forceLoad ? TXLoaderCore.forceResourcesDir : TXLoaderCore.resourcesDir;
        return path.resolve(this.getResourceLocation());
    }

    String getVersion() {
        return this.version == null ? LATEST_VERSION : this.version;
    }

    Source getSource() {
        return this.source == null ? Source.ASSET : this.source;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("resourceLocation", this.resourceLocation)
                .add("resourceLocationOverride", this.resourceLocationOverride).add("forceLoad", this.forceLoad)
                .add("version", this.version).add("source", this.source).toString();
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
