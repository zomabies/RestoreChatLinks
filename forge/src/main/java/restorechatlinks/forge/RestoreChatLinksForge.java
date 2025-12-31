package restorechatlinks.forge;

import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.SystemMessageReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
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

import java.lang.reflect.Method;
import java.security.CodeSigner;
import java.util.UUID;

@Mod(RestoreChatLinks.MOD_ID)
public class RestoreChatLinksForge {

    public static final String MOD_SIGNATURE = "@signature@";
    public static final boolean IS_SIGNED = !MOD_SIGNATURE.replace('@', '\0').contains("signature");

    // from net.minecraft.util.Util, not exists in 1.21.11
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public RestoreChatLinksForge(FMLJavaModLoadingContext context) {
        if (IS_SIGNED && FMLLoader.isProduction()) {
            JarValidator.of(ModList.get().getModFileById(RestoreChatLinks.MOD_ID).getFile().getFilePath())
                    .validate()
                    .throwIfInvalid(MOD_SIGNATURE);
        }

        // Submit our event bus to let architectury register our content on the right time
        //EventBuses.registerModEventBus(RestoreChatLinks.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        RestoreChatLinks.init();

        context.registerConfig(ModConfig.Type.CLIENT, Config.clientSpec);

        if (tryRegisterEB7Event(context)) {
            ChatEventsEB7.LOGGER.debug("Using EventBus 7 to register events");
            return;
        }

        context.getModEventBus().addListener(EventPriority.HIGH, this::onClientEvent);

        context.getModEventBus().addListener(this::onConfigLoad);
        context.getModEventBus().addListener(this::onConfigChange);

    }

    private void onClientEvent(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, this::onChatReceived);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, this::onPlayerChatReceived);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, this::onSystemChatReceived);
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

    private boolean tryRegisterEB7Event(FMLJavaModLoadingContext context) {
        if (!ChatEventsEB7.HAS_NEW_EVENT_BUS) {
            return false;
        }
        try {
            Method getModBusGroupMethod = FMLJavaModLoadingContext.class.getMethod("getModBusGroup");
            Object modBusGroup = getModBusGroupMethod.invoke(context);

            ChatEventsEB7.registerGroupBusEvent(FMLClientSetupEvent.class, modBusGroup, this::onClientEventEB7);
            ChatEventsEB7.registerGroupBusEvent(ModConfigEvent.Loading.class, modBusGroup, this::onConfigLoad);
            ChatEventsEB7.registerGroupBusEvent(ModConfigEvent.Reloading.class, modBusGroup, this::onConfigChange);

            return true;
        } catch (ReflectiveOperationException e) {
            ChatEventsEB7.LOGGER.error("Failed to register events (EB7), chat parsing will not work!", e);
        } catch (Exception e) {
            ChatEventsEB7.LOGGER.error("Error while registering events (EB7), chat parsing will not work!", e);
        }
        return false;
    }

    private void onClientEventEB7(FMLClientSetupEvent event) {
        try {
            ChatEventsEB7.registerModBusEvent(ClientChatReceivedEvent.class, this::onChatReceived);
            ChatEventsEB7.registerModBusEvent(ClientChatReceivedEvent.Player.class, this::onPlayerChatReceived);
            ChatEventsEB7.registerModBusEvent(SystemMessageReceivedEvent.class, this::onSystemChatReceived);
        } catch (ReflectiveOperationException | SecurityException ex) {
            ChatEventsEB7.LOGGER.error("Unable to register chat events (EB7), chat parsing will not work!", ex);
        }
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
