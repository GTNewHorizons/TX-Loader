package glowredman.txloader;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import glowredman.txloader.Asset.Source;

public class AssetBuilder {

    private final Asset asset;

    AssetBuilder(String resourceLocation) {
        this.asset = new Asset(resourceLocation, RemoteHandler.latestRelease, Source.ASSET);
    }

    /**
     *
     * @param resourceLocationOverride The ResourceLocation used to copy the asset to. Defaults the same
     *                                 ResourceLocation used by Mojang. Example: <code>minecraft/lang/en_US.lang</code>
     * @return This {@link AssetBuilder} object to allow chaining of method calls
     * @author glowredman
     */
    public AssetBuilder setOverride(String resourceLocationOverride) {
        this.asset.resourceLocationOverride = resourceLocationOverride;
        return this;
    }

    /**
     * Marks this {@link Asset} as 'forced'. Minecraft will prioritize this asset over any other with the same
     * ResourceLocation.
     * 
     * @return This {@link AssetBuilder} object to allow chaining of method calls
     * @author glowredman
     */
    public AssetBuilder setForced() {
        this.asset.forceLoad = true;
        return this;
    }

    /**
     *
     * @param version The Minecraft version in which the asset can be found. Defaults to the latest release.
     * @return This {@link AssetBuilder} object to allow chaining of method calls
     * @author glowredman
     */
    public AssetBuilder setVersion(String version) {
        this.asset.version = version;
        return this;
    }

    /**
     * Define this {@link Asset}'s source. Default is {@link Source#ASSET}. {@link Source#CLIENT} and
     * {@link Source#SERVER} will be cached.
     * 
     * @param source
     * @return This {@link AssetBuilder} object to allow chaining of method calls
     * @author glowredman
     */
    public AssetBuilder setSource(Source source) {
        this.asset.source = source;
        return this;
    }

    /**
     * Queues this {@link Asset} to be fetched as soon as possible (if it doesn't already exist).
     * 
     * @deprecated Use {@link #fetch()} instead.
     * @author glowredman
     */
    @Deprecated
    public void add() {
        this.fetch();
    }

    /**
     * Queues this {@link Asset} to be fetched as soon as possible (if it doesn't already exist).
     * <p>
     * <b>Note:</b> The asset may not be available when it's needed. This depends on various factors, for example when
     * this method is called, how many {@link Asset}s are queued or how fast the player's Internet connection is. Mods
     * are expected to use the returned {@link CompletableFuture} to {@link CompletableFuture#join() block} the (main)
     * thread before using the asset. To reduce the time the (main) thread is blocked, call {@link #fetch()} as soon as
     * possible.
     * 
     * @return A {@link CompletableFuture} which can be used to block the (main) thread.
     * @since 1.9.0
     * @author glowredman
     */
    @Nonnull
    public CompletableFuture<Void> fetch() {
        return RemoteHandler.fetchAsset(this.asset);
    }
}
