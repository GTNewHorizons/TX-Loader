package glowredman.txloader;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import glowredman.txloader.Asset.Source;

public class AssetBuilder {

    private final Asset asset;

    AssetBuilder(String resourceLocation, String version) {
        this.asset = new Asset(resourceLocation, version, Source.ASSET);
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
     * @deprecated Not needed anymore, version is now a required argument of {@link #AssetBuilder(String, String)}.
     * @param version The Minecraft version in which the asset can be found. Defaults to the latest release.
     * @return This {@link AssetBuilder} object to allow chaining of method calls
     * @author glowredman
     * @see TXLoaderCore#getAssetBuilder(String, String)
     */
    @Deprecated
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
     * @author glowredman
     * @see #fetch()
     */
    public void add() {
        this.fetch();
    }

    /**
     * Queues this {@link Asset} to be fetched as soon as possible (if it doesn't already exist). Unlike {@link #add()},
     * this method returns a {@link CompletableFuture}. It can be used to ensure an {@link Asset} has been fetched by
     * blocking the main thread (using {@link CompletableFuture#join() join()}). This is usually only necessary if this
     * method is called after all resources were reloaded.
     * 
     * @return A {@link CompletableFuture} which can be used to block the (main) thread.
     * @since 1.9.0
     * @author glowredman
     * @see #add()
     */
    @Nonnull
    public CompletableFuture<Void> fetch() {
        return RemoteHandler.fetchAsset(this.asset).future;
    }
}
