package restorechatlinks;

import net.minecraft.text.LiteralTextContent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class ChatHooks {

    private static final HashSet<String> CHAT_TRANSLATION_TYPE = new HashSet<>(
            List.of("chat.type.announcement", "chat.type.text")
    );

    public static void onSystemMessage(ClientGameChatEvent event) {

        final Text message = event.getMessage();
        final TextContent textContent = message.getContent();

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
            event.setMessage(modifiedText.get());
            return;
        }

        if (textContent instanceof TranslatableTextContent translatableText
                && CHAT_TRANSLATION_TYPE.contains(translatableText.getKey())) {

            final MutableText modified = MutableText.of(translatableText).setStyle(message.getStyle());
            final Object[] args = translatableText.getArgs();
            Text txt = ((Text) args[1]);
            args[1] = ((MutableText) ChatLink.newChatWithLinks(txt.getString())).setStyle(txt.getStyle());
            event.setMessage(modified);
        }

    }

}
