package restorechatlinks.fabric;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatLog;
import net.minecraft.client.multiplayer.chat.ChatTrustLevel;
import net.minecraft.client.multiplayer.chat.LoggedChatMessage;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import restorechatlinks.ChatHooks;
import restorechatlinks.JarValidator;
import restorechatlinks.RestoreChatLinks;
import restorechatlinks.fabric.mixin.RCLMixinPlugin;

import java.nio.file.Path;
import java.time.Instant;

public class RestoreChatLinksFabric implements ModInitializer {

    public static final String MOD_SIGNATURE = "@signature@";
    public static final boolean IS_SIGNED = !MOD_SIGNATURE.replace('@', '\0').contains("signature");

    private static Minecraft client = null;
    private static final Logger LOGGER = LogManager.getLogger(RestoreChatLinksFabric.class);

    @Override
    public void onInitialize() {

        RestoreChatLinks.init();

        if (FabricLoader.getInstance().isModLoaded("forgeconfigapiport")) {
            ConfigHelper.RegisterConfig();
        }

        if (RCLMixinPlugin.HAS_CHAT_HEADS.get() && RCLMixinPlugin.LOAD_LEGACY_IMPL == null) {
            LOGGER.info("Chat Heads is present, enabling mixin version by default");
            return;
        }

        if (meetMinFabricApiRequirement()) {

            if (Boolean.TRUE.equals(RCLMixinPlugin.LOAD_LEGACY_IMPL)) {
                LOGGER.warn("\"rcl.loadLegacyMixin\" is incompatible with fabric-api version, skipping event register");
            } else {
                client = Minecraft.getInstance();
                // new API
                ClientReceiveMessageEvents.MODIFY_GAME.register(RestoreChatLinksFabric::onModifiableGameMessage);
                ClientReceiveMessageEvents.ALLOW_CHAT.register(RestoreChatLinksFabric::onAllowChatMessage);
            }

        } else if (RCLMixinPlugin.LOAD_LEGACY_IMPL == null || !RCLMixinPlugin.LOAD_LEGACY_IMPL) {
            LOGGER.error("Chat links processing is not available!");
            if (FabricLoader.getInstance().isModLoaded("fabric-api")) {
                LOGGER.error("Installed fabric-api does not meet min requirement, update Fabric or use -Drcl.loadLegacyMixin=true");
            } else {
                LOGGER.error("For using non fabric-api version: \"-Drcl.loadLegacyMixin=true\"");
            }
        }
    }

    private static boolean onAllowChatMessage(
            Component text,
            @Nullable PlayerChatMessage signedMessage,
            @Nullable GameProfile gameProfile,
            ChatType.Bound parameters,
            Instant instant) {

        // Profiless message ("/say" in command block)
        if (signedMessage == null && gameProfile == null) {
            // "emulate" net.minecraft.client.network.message.MessageHandler.onProfilelessMessage
            Component a = ChatHooks.processMessage(text);
            client.gui.getChat().addMessage(a);
            client.getNarrator().sayChat(a);
            ChatLog chatLog = client.getReportingContext().chatLog();
            chatLog.push(LoggedChatMessage.system(a, instant));

            return false;
        }
        // Player message (includes /say)
        if (gameProfile != null) {
            text = ChatHooks.processMessage(text);
            // "emulate" net.minecraft.client.network.message.MessageHandler.processChatMessageInternal
            // to preserve signing information
            if (signedMessage == null) {
                signedMessage = PlayerChatMessage.unsigned(gameProfile.getId(), text.getString());
            }
            final ChatTrustLevel status = ChatTrustLevel.evaluate(signedMessage, text, instant);
            client.gui.getChat().addMessage(text, signedMessage.signature(), status.createTag(signedMessage));
            client.getNarrator().sayNow(parameters.decorateNarration(signedMessage.decoratedContent()));

            ChatLog chatLog = client.getReportingContext().chatLog();
            chatLog.push(LoggedChatMessage.player(gameProfile, signedMessage, status));
            return false;
        }
        return true;
    }

    private static Component onModifiableGameMessage(Component message, boolean overlay) {
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

    static {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() && IS_SIGNED) {
            ModContainer container = FabricLoader.getInstance().getModContainer(RestoreChatLinks.MOD_ID).orElse(null);
            if (container != null) {
                final ModOrigin origin = container.getOrigin();
                if (origin.getKind() == ModOrigin.Kind.PATH) {
                    final Path modFile = origin.getPaths().get(0);
                    if (IS_SIGNED) {
                        JarValidator validator = JarValidator.of(modFile).validate();
                        validator.throwIfInvalid(MOD_SIGNATURE);
                    }
                }
            }
        }
    }
}
