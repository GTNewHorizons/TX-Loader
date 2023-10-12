package glowredman.txloader;

import java.util.List;

import net.minecraft.client.resources.IResourcePack;

@SuppressWarnings("unused")
public class MinecraftHook {

    public static List<IResourcePack> insertForcePack(List<IResourcePack> resourcePackList) {
        resourcePackList.add(new TXResourcePack.Force());
        return resourcePackList;
    }
}
