package glowredman.txloader;

import glowredman.txloader.Asset.Source;

public class AssetBuilder {

    private final Asset asset;

    AssetBuilder(String resourceLocation) {
        this.asset = new Asset(resourceLocation, RemoteHandler.latestRelease, Source.ASSET);
        this.asset.addedByMod = true;
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
     * Adds this {@link Asset} to the list of remote assets to load.
     * 
     * @author glowredman
     */
    public void add() {
        RemoteHandler.fetchAsset(this.asset);
        TXLoaderCore.REMOTE_ASSETS.add(this.asset);
    }
}
