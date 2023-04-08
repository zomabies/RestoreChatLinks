package restorechatlinks.fabric;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import fuzs.forgeconfigapiport.api.config.v2.ModConfigEvents;
import net.minecraftforge.fml.config.ModConfig;
import restorechatlinks.RestoreChatLinks;
import restorechatlinks.fabric.config.Config;

public class ConfigHelper {

    public static void RegisterConfig() {
        com.electronwill.nightconfig.core.Config.setInsertionOrderPreserved(true);
        ModConfigEvents.loading(RestoreChatLinks.MOD_ID).register(ConfigHelper::onConfigLoad);
        // "ModConfigEvents.loading" is register first since it immediately invoke "loading" after this statement
        ForgeConfigRegistry.INSTANCE.register(RestoreChatLinks.MOD_ID, ModConfig.Type.CLIENT, Config.clientSpec);
        ModConfigEvents.reloading(RestoreChatLinks.MOD_ID).register(ConfigHelper::onConfigReload);
    }

    private static void onConfigLoad(ModConfig config) {
        Config.reloadConfig();
    }

    private static void onConfigReload(ModConfig config) {
        Config.reloadConfig();
    }

}
