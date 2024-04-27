package restorechatlinks;

import net.minecraft.text.LiteralTextContent;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TextVisitFactory;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.mutable.MutableObject;
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

    public static Text processMessage(final Text message) {

        final TextContent textContent = message.getContent();

        logMessage(() -> Pair.of("Before: {}", Text.Serializer.toJson(message)));

        if (textContent instanceof LiteralTextContent || textContent == TextContent.EMPTY) {
            Text literalText = message;
            AtomicReference<MutableText> modifiedText = new AtomicReference<>();

            if (RCLConfig.convertFormattingCodes) {
                // some chat modification returns formatting code, which introduces issues.
                Text styled = convertToStyled(message);
                literalText = styled;
                logMessage(() -> Pair.of("Styled: {}", Text.Serializer.toJson(styled)));
            }

            // Prevent text siblings shifted to front when TextContent is "EMPTY"
            // It skips itself when using visitor methods.
            if (textContent == TextContent.EMPTY) {
                modifiedText.set(Text.empty());
            }

            literalText.visit((style, asString) -> {
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

            final MutableText modified = copyTranslatableText(translatableText).setStyle(message.getStyle());
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
    public static MutableText copyTranslatableText(TranslatableTextContent translated) {
        // chat HUD uses cached "translation", build by "TranslatableTextContent#updateTranslation".
        // MessageHandler#processChatMessageInternal => getStatus => MessageTrustStatus.getStatus (update in multiplayer)
        // manual editing using getArgs does not update the cache
        return Text.translatable(translated.getKey(), translated.getArgs());
    }

    /**
     * Converts '§' formatting codes to styled
     * Example: §6ABC -> {"text":"ABC", "color":"gold"}
     *
     * @return Styled string without '§' literal
     */
    public static Text convertToStyled(StringVisitable inlineFormatText) {
        StringBuilder stringBuilder = new StringBuilder();
        MutableObject<MutableText> mutableTextWrapper = new MutableObject<>(Text.literal(""));
        MutableObject<Style> prevStyle = new MutableObject<>();

        TextVisitFactory.visitFormatted(inlineFormatText, Style.EMPTY, (int index, Style currentStyle, int codePoint) -> {

            if (prevStyle.getValue() == null) {
                prevStyle.setValue(currentStyle);
            } else if (!prevStyle.getValue().equals(currentStyle)) {
                updateTextAndStyle(stringBuilder, mutableTextWrapper, prevStyle, currentStyle);
            }

            //System.out.println(Character.toString(codePoint) + "  -> " + currentStyle);
            stringBuilder.appendCodePoint(codePoint);
            return true;
        });

        updateTextAndStyle(stringBuilder, mutableTextWrapper, prevStyle, null);
        return mutableTextWrapper.getValue();
    }

    private static void updateTextAndStyle(StringBuilder stringBuilder, MutableObject<MutableText> mutableTextWrapper, MutableObject<Style> prevStyle, Style currentStyle) {
        if (mutableTextWrapper.getValue() == null) {
            mutableTextWrapper.setValue(Text.literal(stringBuilder.toString()).setStyle(prevStyle.getValue()));
        } else {
            mutableTextWrapper.getValue().append(Text.literal(stringBuilder.toString()).setStyle(prevStyle.getValue()));
        }
        stringBuilder.delete(0, stringBuilder.length());
        prevStyle.setValue(currentStyle);
    }

    private static void logMessage(Supplier<Pair<String, String>> message) {
        if (RCLConfig.debugMessage) {
            Pair<String, String> messagePair = message.get();
            LOGGER.info(messagePair.getLeft(), messagePair.getRight());
        }
    }
}
