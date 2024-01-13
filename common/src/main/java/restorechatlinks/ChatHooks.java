package restorechatlinks;

import net.minecraft.text.LiteralTextContent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import restorechatlinks.config.RCLConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ChatHooks {

    private static final Logger LOGGER = LogManager.getLogger("RCL-DEBUG");

    private static final HashSet<String> CHAT_TRANSLATION_TYPE = new HashSet<>(
            List.of("chat.type.announcement",
                    "chat.type.text",
                    "commands.message.display.outgoing",
                    "commands.message.display.incoming")
    );

    public static void onSystemMessage(ClientGameChatEvent event) {
        Text text = processMessage(event.getMessage());
        event.setMessage(text);
    }

    public static Text processMessage(Text message) {

        final TextContent textContent = message.getContent();

        logMessage(() -> Pair.of("Before: {}", Text.Serializer.toJson(message)));

        if (textContent instanceof LiteralTextContent) {

            AtomicReference<MutableText> modifiedText = new AtomicReference<>();
            message.visit((style, asString) -> {
                if (modifiedText.get() == null) {
                    modifiedText.set(((MutableText) ChatLink.newChatWithLinks(asString)).setStyle(style));
                } else {
                    modifiedText.get().append(((MutableText) ChatLink.newChatWithLinks(asString)).setStyle(style));
                }
                return Optional.empty();
            }, Style.EMPTY);
            modifiedText.get().setStyle(message.getStyle());
            logMessage(() -> Pair.of("AFTER-(LITERAL): {}", Text.Serializer.toJson(modifiedText.get())));
            return modifiedText.get();
        }

        if (textContent instanceof TranslatableTextContent translatableText
                && CHAT_TRANSLATION_TYPE.contains(translatableText.getKey())) {

            final MutableText modified = MutableText.of(translatableText).setStyle(message.getStyle());
            modified.getSiblings().addAll(message.getSiblings());
            final Object[] args = translatableText.getArgs();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Text txt) {
                    args[i] = ((MutableText) ChatLink.newChatWithLinks(txt.getString())).setStyle(txt.getStyle());
                }
                if (args[i] instanceof String str) {
                    args[i] = ChatLink.newChatWithLinks(str);
                }
            }
            logMessage(() -> Pair.of("AFTER-(TRANSLATABLE): {}", Text.Serializer.toJson(modified)));
            return (modified);
        }

        return message;
    }

    /**
     * Creates a copy without updateTranslations called. Used for multiplayer
     **/
    public static MutableText copyTranslatableText(TranslatableTextContent translated){
        // chat HUD uses cached "translation", build by "TranslatableTextContent#updateTranslation".
        // MessageHandler#processChatMessageInternal => getStatus => MessageTrustStatus.getStatus (update in multiplayer)
        // manual editing using getArgs does not update the cache
        return Text.translatable(translated.getKey(), translated.getArgs());
    }

    private static void logMessage(Supplier<Pair<String, String>> message) {
        if (RCLConfig.debugMessage) {
            Pair<String, String> messagePair = message.get();
            LOGGER.info(messagePair.getLeft(), messagePair.getRight());
        }
    }
}
