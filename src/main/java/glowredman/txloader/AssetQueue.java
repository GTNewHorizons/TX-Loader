package glowredman.txloader;

import java.util.ArrayDeque;
import java.util.Queue;

import cpw.mods.fml.client.FMLClientHandler;

class AssetQueue extends Thread {

    private final Queue<Asset> assetQueue = new ArrayDeque<>();
    volatile boolean preInitReached;
    volatile boolean errored;

    AssetQueue() {
        super("TX Loader thread");
    }

    @Override
    public void run() {
        try {
            TXLoaderCore.isRemoteReachable = RemoteHandler.getVersions();
            JarHandler.indexJars();
        } catch (Exception e) {
            this.errored = true;
            return;
        }

        while (!this.preInitReached || this.isMcRunning()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}

            synchronized (this.assetQueue) {
                while (!this.assetQueue.isEmpty()) {
                    RemoteHandler.getAsset(this.assetQueue.poll());
                }
            }
        }
    }

    private boolean isMcRunning() {
        return FMLClientHandler.instance().getClient().running;
    }

    void add(Asset asset) {
        synchronized (this.assetQueue) {
            this.assetQueue.add(asset);
        }
    }

    private boolean isEmpty() {
        synchronized (this.assetQueue) {
            return this.assetQueue.isEmpty();
        }
    }

    void waitForEmptyQueue() {
        if (this.errored || this.isEmpty()) {
            return;
        }
        TXLoaderCore.LOGGER.info("Waiting for asset queue to finish...");
        while (!this.isEmpty() && !this.errored) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
        }
        TXLoaderCore.LOGGER.info("Asset queue finished! Continuing execution.");
    }
}
