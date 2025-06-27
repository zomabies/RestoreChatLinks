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
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforgespi.locating.IModFile;
import org.apache.commons.codec.digest.DigestUtils;
import restorechatlinks.ChatHooks;
import restorechatlinks.RestoreChatLinks;
import restorechatlinks.neoforge.config.Config;

import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Locale;

@Mod(RestoreChatLinks.MOD_ID)
public class RestoreChatLinksNeoForge {

    public static final String MOD_SIGNATURE = "@signature@";
    public static final boolean IS_SIGNED = !MOD_SIGNATURE.replace('@', '\0').contains("signature");

    public RestoreChatLinksNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        boolean isValidJar = FMLLoader.isProduction() && RestoreChatLinks.validJarSignature(ModList.get()
                .getModFileById(RestoreChatLinks.MOD_ID)
                .getFile()
                .getFilePath()
                .toFile());
        if (!isValidJar && IS_SIGNED && FMLLoader.isProduction()) {
            throw new SecurityException("Jar file is modified");
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
        if (modFile.getModFileInfo() instanceof ModFileInfo modInfo) {
            String fingerprint = modInfo.getCodeSigningFingerprint().orElse("").toLowerCase(Locale.ROOT);
            if (IS_SIGNED && FMLLoader.isProduction() && !MOD_SIGNATURE.toLowerCase(Locale.ROOT).equals(fingerprint)) {
                throw new SecurityException("Jar fingerprint does not match, fp: " + fingerprint);
            }
        }

        SecureJar.Status status = IS_SIGNED
                ? IntegrityVerifier.selfVerify(modFile, FMLLoader.isProduction())
                : SecureJar.Status.NONE;
        switch (status) {

            case VERIFIED: {
                if (FMLLoader.isProduction()) {
                    boolean match = false;
                    String literalFP = MOD_SIGNATURE.replaceAll(":", "");
                    for (CodeSigner codeSigner : modFile.getSecureJar().getManifestSigners()) {
                        for (Certificate cert : codeSigner.getSignerCertPath().getCertificates()) {
                            try {
                                String a = DigestUtils.sha256Hex(cert.getEncoded());
                                match = a.equalsIgnoreCase(literalFP);
                            } catch (CertificateEncodingException ignored) {
                            }
                        }
                    }
                    if (match) {
                        //System.out.println("Success verify in static constructor!");
                    } else {
                        throw new SecurityException("Jar fingerprint not expected");
                    }
                }
                break;
            }
            case NONE:
            case INVALID:
            case UNVERIFIED:
            default: {
                if (IS_SIGNED && FMLLoader.isProduction()) {
                    throw new SecurityException("Jar file is tampered! " + modFile.getFileName());
                } else {
                    System.out.println("DEV mode, ignoring jar sign status");
                }
            }
        }
    }

}
