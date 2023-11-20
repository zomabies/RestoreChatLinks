package restorechatlinks.forge;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import restorechatlinks.ChatLink;
import restorechatlinks.RestoreChatLinks;
import restorechatlinks.forge.config.Config;

@Mod(RestoreChatLinks.MOD_ID)
public class RestoreChatLinksForge {
    public RestoreChatLinksForge() {
        // Submit our event bus to let architectury register our content on the right time
        //EventBuses.registerModEventBus(RestoreChatLinks.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        RestoreChatLinks.init();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EventPriority.HIGH, this::onClientEvent);

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.clientSpec);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigLoad);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigChange);

    }

    private void onClientEvent(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, this::onChatReceived);
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        Config.reloadConfig();
    }

    private void onConfigChange(ModConfigEvent.Reloading event) {
        Config.reloadConfig();
    }

    private void onChatReceived(ClientChatReceivedEvent chat) {

        final TextContent text = chat.getMessage().copy().getContent();
        if (text instanceof TranslatableTextContent translatableText) {
            final MutableText modified = MutableText.of(translatableText);
            final Object[] args = translatableText.getArgs();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Text txt) {
                    args[i] = ChatLink.newChatWithLinks(txt.getString());
                }
                if (args[i] instanceof String str) {
                    args[i] = ChatLink.newChatWithLinks(str);
                }
            }
            chat.setMessage(modified);
        }
    }


}
