package restorechatlinks.fabric;

import net.fabricmc.api.ModInitializer;
import net.minecraftforge.api.ModLoadingContext;
import net.minecraftforge.api.fml.event.config.ModConfigEvents;
import net.minecraftforge.fml.config.ModConfig;
import restorechatlinks.RestoreChatLinks;
import restorechatlinks.fabric.config.Config;

public class RestoreChatLinksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RestoreChatLinks.init();

        com.electronwill.nightconfig.core.Config.setInsertionOrderPreserved(true);
        ModLoadingContext.registerConfig(RestoreChatLinks.MOD_ID, ModConfig.Type.CLIENT, Config.clientSpec);
        ModConfigEvents.loading(RestoreChatLinks.MOD_ID).register(this::onConfigLoad);
        ModConfigEvents.reloading(RestoreChatLinks.MOD_ID).register(this::onConfigReload);
    }

    private void onConfigLoad(ModConfig config) {
        Config.reloadConfig();
    }

    private void onConfigReload(ModConfig config) {
        Config.reloadConfig();
    }

}
