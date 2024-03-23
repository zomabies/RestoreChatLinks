package restorechatlinks.fabric;

import fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeModConfigEvents;
import net.minecraftforge.fml.config.ModConfig;
import restorechatlinks.RestoreChatLinks;
import restorechatlinks.fabric.config.Config;

public class ConfigHelper {

    public static void RegisterConfig() {
        com.electronwill.nightconfig.core.Config.setInsertionOrderPreserved(true);
        ForgeModConfigEvents.loading(RestoreChatLinks.MOD_ID).register(ConfigHelper::onConfigLoad);
        // "ModConfigEvents.loading" is register first since it immediately invoke "loading" after this statement
        ForgeConfigRegistry.INSTANCE.register(RestoreChatLinks.MOD_ID, ModConfig.Type.CLIENT, Config.clientSpec);
        ForgeModConfigEvents.reloading(RestoreChatLinks.MOD_ID).register(ConfigHelper::onConfigReload);
    }

    private static void onConfigLoad(ModConfig config) {
        Config.reloadConfig();
    }

    private static void onConfigReload(ModConfig config) {
        Config.reloadConfig();
    }

}
