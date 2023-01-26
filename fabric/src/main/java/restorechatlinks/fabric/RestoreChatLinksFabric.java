package restorechatlinks.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import restorechatlinks.RestoreChatLinks;

public class RestoreChatLinksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RestoreChatLinks.init();

        if (FabricLoader.getInstance().isModLoaded("forgeconfigapiport")) {
            ConfigHelper.RegisterConfig();
        }
    }

}
