package glowredman.txloader;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository.Entry;

@SuppressWarnings("unused")
public class MinecraftHook {

    public static List<IResourcePack> insertPacks(List<IResourcePack> resourcePackList) {
        List<Entry> assignedPacks = Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntries();
        IResourcePack pack = new TXResourcePack("TX Loader Resources", TXLoaderCore.resourcesDir);

        if (assignedPacks.isEmpty()) {
            resourcePackList.add(pack);
        } else {
            // inject before user assigned resource packs
            int index = resourcePackList.indexOf(assignedPacks.get(0).getResourcePack());
            resourcePackList.add(index, pack);
        }

        resourcePackList.add(new TXResourcePack("TX Loader Forced Resources", TXLoaderCore.forceResourcesDir));
        return resourcePackList;
    }
}
