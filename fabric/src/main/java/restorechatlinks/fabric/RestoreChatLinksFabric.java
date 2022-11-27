package restorechatlinks.fabric;

import net.fabricmc.api.ModInitializer;
import restorechatlinks.RestoreChatLinks;

public class RestoreChatLinksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RestoreChatLinks.init();
    }
}
