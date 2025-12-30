package restorechatlinks.forge;

import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
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

import java.security.CodeSigner;

@Mod(RestoreChatLinks.MOD_ID)
public class RestoreChatLinksForge {

    public static final String MOD_SIGNATURE = "@signature@";
    public static final boolean IS_SIGNED = !MOD_SIGNATURE.replace('@', '\0').contains("signature");

    public RestoreChatLinksForge() {
        if (IS_SIGNED && FMLLoader.isProduction()) {
            JarValidator.of(ModList.get().getModFileById(RestoreChatLinks.MOD_ID).getFile().getFilePath())
                    .validate()
                    .throwIfInvalid(MOD_SIGNATURE);
        }

        // Submit our event bus to let architectury register our content on the right time
        //EventBuses.registerModEventBus(RestoreChatLinks.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        RestoreChatLinks.init();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EventPriority.HIGH, this::onClientEvent);

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.clientSpec);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigLoad);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigChange);

    }

    private void onClientEvent(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, this::onChatReceived);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, this::onSystemChatReceived);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, this::onPlayerChatReceived);
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
