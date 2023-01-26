package restorechatlinks.fabric;

import net.minecraftforge.api.ModLoadingContext;
import net.minecraftforge.api.fml.event.config.ModConfigEvents;
import net.minecraftforge.fml.config.ModConfig;
import restorechatlinks.RestoreChatLinks;
import restorechatlinks.fabric.config.Config;

public class ConfigHelper {

    public static void RegisterConfig() {
        com.electronwill.nightconfig.core.Config.setInsertionOrderPreserved(true);
        ModLoadingContext.registerConfig(RestoreChatLinks.MOD_ID, ModConfig.Type.CLIENT, Config.clientSpec);
        ModConfigEvents.loading(RestoreChatLinks.MOD_ID).register(ConfigHelper::onConfigLoad);
        ModConfigEvents.reloading(RestoreChatLinks.MOD_ID).register(ConfigHelper::onConfigReload);
    }

    private static void onConfigLoad(ModConfig config) {
        Config.reloadConfig();
    }

    private static void onConfigReload(ModConfig config) {
        Config.reloadConfig();
    }

}
