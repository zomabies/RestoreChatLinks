package restorechatlinks;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.StringDecomposer;
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

    public static Component onSystemMessage(Component message) {
        Component result = processMessage(message);
        return result;
    }

    public static Component processMessage(final Component message) {

        final ComponentContents textContent = message.getContents();

        logMessage(() -> Pair.of("Before: {}", message.toString()));

        if (textContent instanceof PlainTextContents) {
            Component literalText = message;
            AtomicReference<MutableComponent> modifiedText = new AtomicReference<>();

            if (RCLConfig.convertFormattingCodes) {
                // some chat modification returns formatting code, which introduces issues.
                Component styled = convertToStyled(message);
                literalText = styled;
                logMessage(() -> Pair.of("Styled: {}", styled.toString()));
            }

            // Prevent text siblings shifted to front when TextContent is "EMPTY"
            // It skips itself when using visitor methods.
            if (textContent == PlainTextContents.EMPTY) {
                modifiedText.set(Component.empty());
            }

            literalText.visit((style, asString) -> {
                if (modifiedText.get() == null) {
                    modifiedText.set(((MutableComponent) ChatLink.newChatWithLinks(asString)).setStyle(style));
                } else {
                    modifiedText.get().append(((MutableComponent) ChatLink.newChatWithLinks(asString)).setStyle(style));
                }
                return Optional.empty();
            }, Style.EMPTY);
            modifiedText.get().setStyle(message.getStyle());
            logMessage(() -> Pair.of("AFTER-(LITERAL): {}", modifiedText.get().toString()));
            return modifiedText.get();
        }

        if (textContent instanceof TranslatableContents translatableText
                && CHAT_TRANSLATION_TYPE.contains(translatableText.getKey())) {

            final MutableComponent modified = copyTranslatableText(translatableText).setStyle(message.getStyle());
            modified.getSiblings().addAll(message.getSiblings());
            final Object[] args = translatableText.getArgs();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component txt) {
                    args[i] = ((MutableComponent) ChatLink.newChatWithLinks(txt.getString())).setStyle(txt.getStyle());
                }
                if (args[i] instanceof String str) {
                    args[i] = ChatLink.newChatWithLinks(str);
                }
            }
            logMessage(() -> Pair.of("AFTER-(TRANSLATABLE): {}", modified.toString()));
            return (modified);
        }

        return message;
    }

    /**
     * Creates a copy without updateTranslations called. Used for multiplayer
     **/
    public static MutableComponent copyTranslatableText(TranslatableContents translated) {
        // chat HUD uses cached "translation", build by "TranslatableTextContent#updateTranslation".
        // MessageHandler#processChatMessageInternal => getStatus => MessageTrustStatus.getStatus (update in multiplayer)
        // manual editing using getArgs does not update the cache
        return Component.translatable(translated.getKey(), translated.getArgs());
    }

    /**
     * Converts '§' formatting codes to styled
     * Example: §6ABC -> {"text":"ABC", "color":"gold"}
     *
     * @return Styled string without '§' literal
     */
    public static Component convertToStyled(FormattedText inlineFormatText) {
        StringBuilder stringBuilder = new StringBuilder();
        MutableObject<MutableComponent> mutableTextWrapper = new MutableObject<>(Component.literal(""));
        MutableObject<Style> prevStyle = new MutableObject<>();

        StringDecomposer.iterateFormatted(inlineFormatText, Style.EMPTY, (int index, Style currentStyle, int codePoint) -> {

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

    private static void updateTextAndStyle(StringBuilder stringBuilder, MutableObject<MutableComponent> mutableTextWrapper, MutableObject<Style> prevStyle, Style currentStyle) {
        if (mutableTextWrapper.getValue() == null) {
            mutableTextWrapper.setValue(Component.literal(stringBuilder.toString()).setStyle(prevStyle.getValue()));
        } else {
            mutableTextWrapper.getValue().append(Component.literal(stringBuilder.toString()).setStyle(prevStyle.getValue()));
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
