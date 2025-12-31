package restorechatlinks.forge;

import cpw.mods.jarhandling.SecureJar;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import restorechatlinks.ChatHooks;
import restorechatlinks.JarValidator;
import restorechatlinks.RestoreChatLinks;
import restorechatlinks.forge.config.Config;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.security.CodeSigner;
import java.util.function.Supplier;

@Mod(RestoreChatLinks.MOD_ID)
public class RestoreChatLinksForge {

    public static final String MOD_SIGNATURE = "@signature@";
    public static final boolean IS_SIGNED = !MOD_SIGNATURE.replace('@', '\0').contains("signature");

    private static final Logger LOGGER = LogManager.getLogger("RCL");
    private static final MethodHandle MH_SystemMessageReceivedEvent$getMessage;
    private static final MethodHandle MH_SystemMessageReceivedEvent$setMessage;
    private static final boolean HAS_MH_1_20_6;

    @SuppressWarnings("unused")
    public RestoreChatLinksForge() {
        this(null); // compatibility no-arg ctor
    }

    public RestoreChatLinksForge(FMLJavaModLoadingContext context) {
        if (IS_SIGNED && FMLLoader.isProduction()) {
            JarValidator.of(ModList.get().getModFileById(RestoreChatLinks.MOD_ID).getFile().getFilePath())
                    .validate()
                    .throwIfInvalid(MOD_SIGNATURE);
        }

        // Submit our event bus to let architectury register our content on the right time
        //EventBuses.registerModEventBus(RestoreChatLinks.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        RestoreChatLinks.init();

        if (context == null) {
            // forge version prior to ctor injection
            context = FMLJavaModLoadingContext.get();
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.clientSpec);
        } else {
            // Newer forge
            // FMLJavaModLoadingContext.registerConfig(ModConfig.Type type, IConfigSpec configSpec)
            try {
                final Method registerConfig = context
                        .getClass()
                        .getMethod("registerConfig", ModConfig.Type.class, IConfigSpec.class);
                registerConfig.setAccessible(true);
                registerConfig.invoke(context, ModConfig.Type.CLIENT, Config.clientSpec);
                LOGGER.info("Config registration complete (reflect)");
            } catch (Throwable e) {
                throw new RuntimeException("Unable to register config", e);
            }
        }

        context.getModEventBus().addListener(EventPriority.HIGH, this::onClientEvent);

        context.getModEventBus().addListener(this::onConfigLoad);
        context.getModEventBus().addListener(this::onConfigChange);

    }

    private void onClientEvent(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, this::onChatReceived);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, this::onPlayerChatReceived);
        if (HAS_MH_1_20_6) {
            // 1.20.6+
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                    getSystemChatEvent_1_20_6(), this::onSystemChatReceived_1_20_6);
        } else {
            // 1.20.3 - 1.20.4
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, this::onSystemChatReceived);
        }
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        Config.reloadConfig();
    }

    private void onConfigChange(ModConfigEvent.Reloading event) {
        Config.reloadConfig();
    }

    private void onChatReceived(ClientChatReceivedEvent chat) {
        Supplier<Boolean> isSystemChat = () -> (chat instanceof ClientChatReceivedEvent.System);
        boolean processMessage = HAS_MH_1_20_6 || !isSystemChat.get();
        // check manually, isSystem is deprecated
        if (chat.getSender().equals(Util.NIL_UUID) && processMessage) {
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

    private void onSystemChatReceived_1_20_6(Event chat) {
        try {
            Text message = (Text) MH_SystemMessageReceivedEvent$getMessage.invokeExact(chat);
            MH_SystemMessageReceivedEvent$setMessage.invokeExact(chat, ChatHooks.processMessage(message));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static ImmutablePair<MethodHandle, MethodHandle> checkAndInitMH_1_20_6() {
        MethodHandle getMessageMH = null;
        MethodHandle setMessageMH = null;
        try {
            Class<? extends Event> eventClass = getSystemChatEvent_1_20_6();
            if (eventClass != null) {
                MethodHandles.Lookup lookup = MethodHandles.lookup();

                getMessageMH = lookup.findVirtual(eventClass, "getMessage", MethodType.methodType(Text.class));
                setMessageMH = lookup.findVirtual(eventClass, "setMessage", MethodType.methodType(void.class, Text.class));

                MethodHandle MH_systemChatReceived1_20_6 = lookup.findVirtual(
                        RestoreChatLinksForge.class,
                        "onSystemChatReceived_1_20_6",
                        MethodType.methodType(void.class, Event.class)
                );

                Class<?> typeToCast = MH_systemChatReceived1_20_6.type().parameterType(1); // first method parameter
                // ((SystemMessageReceivedEvent) event).getMessage()
                // ((SystemMessageReceivedEvent) event).setMessage(param)
                getMessageMH = getMessageMH.asType(getMessageMH.type().changeParameterType(0, typeToCast));
                setMessageMH = setMessageMH.asType(setMessageMH.type().changeParameterType(0, typeToCast));
            }
        } catch (NoSuchMethodException | IllegalAccessException | SecurityException ex) {
            LOGGER.error("System chat parsing will not work!");
            LOGGER.error("Unable to get method handle for SystemMessageReceivedEvent", ex);
        }
        return ImmutablePair.of(getMessageMH, setMessageMH);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> getSystemChatEvent_1_20_6() {
        try {
            return (Class<? extends Event>) Class.forName("net.minecraftforge.client.event.SystemMessageReceivedEvent");
        } catch (ClassNotFoundException | SecurityException | LinkageError e) {
            return null;
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

        ImmutablePair<MethodHandle, MethodHandle> result = checkAndInitMH_1_20_6();
        MH_SystemMessageReceivedEvent$getMessage = result.getLeft();
        MH_SystemMessageReceivedEvent$setMessage = result.getRight();
        HAS_MH_1_20_6 = MH_SystemMessageReceivedEvent$getMessage != null
                && MH_SystemMessageReceivedEvent$setMessage != null;
    }

}
