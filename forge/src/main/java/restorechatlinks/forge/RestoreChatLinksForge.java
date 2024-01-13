package restorechatlinks.forge;

import cpw.mods.jarhandling.SecureJar;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;
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
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.commons.codec.digest.DigestUtils;
import restorechatlinks.ChatHooks;
import restorechatlinks.ChatLink;
import restorechatlinks.RestoreChatLinks;
import restorechatlinks.forge.config.Config;

import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Locale;

@Mod(RestoreChatLinks.MOD_ID)
public class RestoreChatLinksForge {

    public static final String MOD_SIGNATURE = "@signature@";
    public static final boolean IS_SIGNED = !MOD_SIGNATURE.replace('@', '\0').contains("signature");

    public RestoreChatLinksForge() {
        boolean isValidJar = FMLLoader.isProduction() && RestoreChatLinks.validJarSignature(ModList.get()
                .getModFileById(RestoreChatLinks.MOD_ID)
                .getFile()
                .getFilePath()
                .toFile());
        if (!isValidJar && IS_SIGNED && FMLLoader.isProduction()) {
            throw new SecurityException("Jar file is modified");
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

    static {
        final IModFile modFile = ModList.get().getModFileById(RestoreChatLinks.MOD_ID).getFile();
        if (modFile.getModFileInfo() instanceof ModFileInfo modInfo) {
            String fingerprint = modInfo.getCodeSigningFingerprint().orElse(null);
            if (IS_SIGNED && FMLLoader.isProduction() && !MOD_SIGNATURE.toLowerCase(Locale.ROOT).equals(fingerprint)) {
                throw new SecurityException("Jar fingerprint does not match");
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
