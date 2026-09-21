package restorechatlinks.forge;

import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.SystemMessageReceivedEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.forgespi.locating.IModFile;
import restorechatlinks.ChatHooks;
import restorechatlinks.JarValidator;
import restorechatlinks.RestoreChatLinks;
import restorechatlinks.forge.config.Config;

import java.util.UUID;

@Mod(RestoreChatLinks.MOD_ID)
public class RestoreChatLinksForge {

    public static final String MOD_SIGNATURE = "@signature@";
    public static final boolean IS_SIGNED = !MOD_SIGNATURE.replace('@', '\0').contains("signature");

    // from net.minecraft.util.Util, not exists in 1.21.11
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public RestoreChatLinksForge(FMLJavaModLoadingContext context) {
        if (IS_SIGNED && FMLLoader.isProduction()) {
            JarValidator.of(ModList.getModFileById(RestoreChatLinks.MOD_ID).getFile().getFilePath())
                    .validate()
                    .throwIfInvalid(MOD_SIGNATURE);
        }

        // Submit our event bus to let architectury register our content on the right time
        //EventBuses.registerModEventBus(RestoreChatLinks.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        RestoreChatLinks.init();

        context.registerConfig(ModConfig.Type.CLIENT, Config.clientSpec);

        BusGroup modBusGroup = context.getModBusGroup();

        FMLClientSetupEvent.getBus(modBusGroup).addListener(this::onClientEvent);

        ModConfigEvent.Loading.getBus(modBusGroup).addListener(this::onConfigLoad);
        ModConfigEvent.Reloading.getBus(modBusGroup).addListener(this::onConfigChange);
    }

    private void onClientEvent(FMLClientSetupEvent event) {
        ClientChatReceivedEvent.BUS.addListener(this::onChatReceived);
        ClientChatReceivedEvent.Player.BUS.addListener(this::onPlayerChatReceived);
        SystemMessageReceivedEvent.BUS.addListener(this::onSystemChatReceived);
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        Config.reloadConfig();
    }

    private void onConfigChange(ModConfigEvent.Reloading event) {
        Config.reloadConfig();
    }

    private void onChatReceived(ClientChatReceivedEvent chat) {
        // check manually, ClientChatReceivedEvent::isSystem is deprecated
        if (chat.getSender().equals(NIL_UUID)) {
            // Profiless message
            chat.setMessage(ChatHooks.processMessage(chat.getMessage()));
        }
    }

    private void onSystemChatReceived(SystemMessageReceivedEvent chat) {
        chat.setMessage(ChatHooks.processMessage(chat.getMessage()));
    }

    private void onPlayerChatReceived(ClientChatReceivedEvent.Player chat) {
        chat.setMessage(ChatHooks.processMessage(chat.getMessage()));
    }

    static {
        final IModFile modFile = ModList.getModFileById(RestoreChatLinks.MOD_ID).getFile();

        SecureJar.Status status = IS_SIGNED
                ? IntegrityVerifier.selfVerify(modFile, FMLLoader.isProduction())
                : SecureJar.Status.NONE;

        if (IS_SIGNED) {
            IntegrityVerifier.handleStatus(status, modFile);
        }

    }

}
