package restorechatlinks.fabric;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.report.log.ChatLog;
import net.minecraft.client.report.log.ReceivedMessage;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import restorechatlinks.ChatHooks;
import restorechatlinks.RestoreChatLinks;
import restorechatlinks.fabric.mixin.RCLMixinPlugin;

import java.time.Instant;

public class RestoreChatLinksFabric implements ModInitializer {

    private static MinecraftClient client = null;
    private static final Logger LOGGER = LogManager.getLogger(RestoreChatLinksFabric.class);

    @Override
    public void onInitialize() {
        RestoreChatLinks.init();

        if (FabricLoader.getInstance().isModLoaded("forgeconfigapiport")) {
            ConfigHelper.RegisterConfig();
        }

        if (meetMinFabricApiRequirement()) {

            if (RCLMixinPlugin.LOAD_LEGACY_IMPL) {
                LOGGER.warn("\"rcl.loadLegacyMixin\" is incompatible with fabric-api version, skipping event register");
            } else {
                client = MinecraftClient.getInstance();
                // new API
                ClientReceiveMessageEvents.MODIFY_GAME.register(RestoreChatLinksFabric::onModifiableGameMessage);
                ClientReceiveMessageEvents.ALLOW_CHAT.register(RestoreChatLinksFabric::onAllowChatMessage);
            }

        } else if (!RCLMixinPlugin.LOAD_LEGACY_IMPL) {
            LOGGER.error("Chat links processing is not available!");
            if (FabricLoader.getInstance().isModLoaded("fabric-api")) {
                LOGGER.error("Installed fabric-api does not meet min requirement, update Fabric or use -Drcl.loadLegacyMixin=true");
            } else {
                LOGGER.error("Limited support for using non fabric-api version: \"-Drcl.loadLegacyMixin=true\"");
            }
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

    private static boolean meetMinFabricApiRequirement() {
        if (!FabricLoader.getInstance().isModLoaded("fabric-api")) {
            return false;
        }

        ModContainer fApi = FabricLoader.getInstance().getModContainer("fabric-api").orElse(null);
        Version minApiVersion = null;
        try {
            minApiVersion = Version.parse("0.75.0");
            if (fApi != null) {
                final Version version = fApi.getMetadata().getVersion();
                return version.compareTo(minApiVersion) >= 0;
            }
        } catch (VersionParsingException ignore) {
        }
        return false;
    }

}
