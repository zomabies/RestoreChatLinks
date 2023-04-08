package restorechatlinks.fabric;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.report.log.ChatLog;
import net.minecraft.client.report.log.ReceivedMessage;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import restorechatlinks.ChatHooks;
import restorechatlinks.RestoreChatLinks;

import java.time.Instant;

public class RestoreChatLinksFabric implements ModInitializer {

    public static MinecraftClient client = null;

    @Override
    public void onInitialize() {
        RestoreChatLinks.init();

        if (FabricLoader.getInstance().isModLoaded("forgeconfigapiport")) {
            ConfigHelper.RegisterConfig();
        }

        if (FabricLoader.getInstance().isModLoaded("fabric-api")) {

            client = MinecraftClient.getInstance();
            // new API
            ClientReceiveMessageEvents.MODIFY_GAME.register(RestoreChatLinksFabric::onModifiableGameMessage);
            ClientReceiveMessageEvents.ALLOW_CHAT.register(RestoreChatLinksFabric::onAllowChatMessage);
        }
    }

    private static boolean onAllowChatMessage(
            Text text,
            @Nullable SignedMessage signedMessage,
            @Nullable GameProfile gameProfile,
            MessageType.Parameters parameters,
            Instant instant) {

        // Profiless message ("/say" in command block)
        if (signedMessage == null && gameProfile == null) {
            // "emulate" net.minecraft.client.network.message.MessageHandler.onProfilelessMessage
            Text a = ChatHooks.processMessage(text);
            client.inGameHud.getChatHud().addMessage(a);
            client.getNarratorManager().narrateChatMessage(a);
            ChatLog chatLog = client.getAbuseReportContext().getChatLog();
            chatLog.add(ReceivedMessage.of(a, instant));

            return false;
        }
        return true;
    }

    private static Text onModifiableGameMessage(Text message, boolean overlay) {
        return !overlay ? ChatHooks.processMessage(message) : message;
    }

}
