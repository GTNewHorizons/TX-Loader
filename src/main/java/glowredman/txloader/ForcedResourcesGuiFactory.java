package glowredman.txloader;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;

import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ForcedResourcesGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraft) {
        FMLCommonHandler.instance().bus().register(this);
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return Screen.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }

    @SubscribeEvent
    public void onConfigChanged(OnConfigChangedEvent event) {
        if ("txloader".equals(event.modID) && "forceload".equals(event.configID)) {
            ForcedResourcesConfig.save();
        }
    }

    public static class Screen extends GuiConfig {

        public Screen(GuiScreen parent) {
            super(parent, folderSettings(), "txloader", "forceload", false, true, "TX Loader: forceload");
        }

        @SuppressWarnings("rawtypes")
        private static List<IConfigElement> folderSettings() {
            return ForcedResourcesConfig.getFolderSettings().stream().map(ConfigElement<Boolean>::new)
                    .collect(Collectors.toList());
        }
    }
}
