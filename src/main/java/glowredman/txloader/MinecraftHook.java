package glowredman.txloader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository.Entry;

@SuppressWarnings("unused")
public class MinecraftHook {

    private static final Map<String, int[]> bootResourceChecks = new HashMap<>();
    private static final AtomicInteger bootIndexedResourceChecks = new AtomicInteger();
    private static volatile boolean loadComplete;
    private static volatile boolean bootProfileLogged;

    public static List<IResourcePack> insertPacks(List<IResourcePack> resourcePackList) {
        List<Entry> assignedPacks = Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntries();
        TXResourcePack pack = new TXResourcePack("TX Loader Resources", TXLoaderCore.resourcesDir);

        if (assignedPacks.isEmpty()) {
            resourcePackList.add(pack);
        } else {
            // inject before user assigned resource packs
            int index = resourcePackList.indexOf(assignedPacks.get(0).getResourcePack());
            resourcePackList.add(index, pack);
        }

        TXResourcePack forcedPack = new TXResourcePack("TX Loader Forced Resources", TXLoaderCore.forceResourcesDir);
        resourcePackList.add(forcedPack);
        return resourcePackList;
    }

    static void recordIndexedResourceCheck() {
        if (!bootProfileLogged) bootIndexedResourceChecks.incrementAndGet();
    }

    static synchronized void recordFileExistsCheck(String pack, String resource, boolean exists) {
        if (bootProfileLogged) return;
        bootResourceChecks.computeIfAbsent(pack + ": " + resource, ignored -> new int[2])[exists ? 0 : 1]++;
    }

    static void onLoadComplete() {
        loadComplete = true;
    }

    public static synchronized void finishResourceReload() {
        if (!loadComplete || bootProfileLogged) return;
        bootProfileLogged = true;

        int total = 0;
        int hits = 0;
        int repeatedPaths = 0;
        List<Map.Entry<String, int[]>> repeated = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : bootResourceChecks.entrySet()) {
            int[] counts = entry.getValue();
            int checks = counts[0] + counts[1];
            total += checks;
            hits += counts[0];
            if (checks > 1) {
                repeatedPaths++;
                repeated.add(entry);
            }
        }

        int indexed = bootIndexedResourceChecks.get();
        TXLoaderCore.LOGGER.info(
                "Pack boot TX resource checks: {} total, {} indexed, {} File.exists fallbacks",
                indexed + total,
                indexed,
                total);
        TXLoaderCore.LOGGER.info(
                "File.exists fallbacks: {} unique, {} duplicate across {} paths, {} hits, {} misses",
                bootResourceChecks.size(),
                total - bootResourceChecks.size(),
                repeatedPaths,
                hits,
                total - hits);

        repeated.sort(Comparator.comparingInt(entry -> -(entry.getValue()[0] + entry.getValue()[1])));
        for (int i = 0; i < Math.min(10, repeated.size()); i++) {
            Map.Entry<String, int[]> entry = repeated.get(i);
            int[] counts = entry.getValue();
            TXLoaderCore.LOGGER
                    .info("{}x ({} hits, {} misses): {}", counts[0] + counts[1], counts[0], counts[1], entry.getKey());
        }
        bootResourceChecks.clear();
    }
}
