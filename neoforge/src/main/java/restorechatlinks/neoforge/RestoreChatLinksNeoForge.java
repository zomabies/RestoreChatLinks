package restorechatlinks.neoforge;

import cpw.mods.jarhandling.SecureJar;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforgespi.locating.IModFile;
import restorechatlinks.ChatHooks;
import restorechatlinks.JarValidator;
import restorechatlinks.RestoreChatLinks;
import restorechatlinks.neoforge.config.Config;

import java.security.CodeSigner;

@Mod(RestoreChatLinks.MOD_ID)
public class RestoreChatLinksNeoForge {

    public static final String MOD_SIGNATURE = "@signature@";
    public static final boolean IS_SIGNED = !MOD_SIGNATURE.replace('@', '\0').contains("signature");

    public RestoreChatLinksNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        if (IS_SIGNED && FMLLoader.isProduction()) {
            JarValidator.of(ModList.get().getModFileById(RestoreChatLinks.MOD_ID).getFile().getFilePath())
                    .validate()
                    .throwIfInvalid(MOD_SIGNATURE);
        }

        RestoreChatLinks.init();

        modEventBus.addListener(this::onClientEvent);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.clientSpec);

        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigChange);
    }

    private void onClientEvent(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, this::onChatReceived);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, this::onSystemChatReceived);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, this::onPlayerChatReceived);
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        Config.reloadConfig();
    }

    private void onConfigChange(ModConfigEvent.Reloading event) {
        Config.reloadConfig();
    }

    private void onChatReceived(ClientChatReceivedEvent chat) {
        if (chat.isSystem() && !(chat instanceof ClientChatReceivedEvent.System)) {
            // Profiless message
            chat.setMessage(ChatHooks.processMessage(chat.getMessage()));
        }
    }

    private void onSystemChatReceived(ClientChatReceivedEvent.System chat) {
        chat.setMessage(ChatHooks.processMessage(chat.getMessage()));
    }

    private void onPlayerChatReceived(ClientChatReceivedEvent.Player chat) {
        chat.setMessage(ChatHooks.processMessage(chat.getMessage()));
    }

    static {
        final IModFile modFile = ModList.get().getModFileById(RestoreChatLinks.MOD_ID).getFile();

        SecureJar.Status status = IS_SIGNED
                ? IntegrityVerifier.selfVerify(modFile, FMLLoader.isProduction())
                : SecureJar.Status.NONE;

        switch (status) {

            case VERIFIED: {
                if (FMLLoader.isProduction()) {
                    CodeSigner[] signers = modFile.getSecureJar().getManifestSigners();
                    boolean match = JarValidator.hasSignersMatch(MOD_SIGNATURE, signers);
                    if (match) {
                        //System.out.println("Success verify in static constructor!");
                    } else {
                        throw new SecurityException("JAR fingerprint not expected");
                    }
                }
                break;
            }
            case NONE:
            case INVALID:
            case UNVERIFIED:
            default: {
                if (IS_SIGNED && FMLLoader.isProduction()) {
                    throw new SecurityException("JAR file is tampered! " + modFile.getFileName());
                } else {
                    System.out.println("DEV mode, ignoring jar sign status");
                }
            }
        }
    }

}
