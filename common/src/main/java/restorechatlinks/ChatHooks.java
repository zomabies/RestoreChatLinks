package restorechatlinks;

import net.minecraft.text.LiteralTextContent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;

public class ChatHooks {

    public static void onSystemMessage(ClientGameChatEvent event) {

        final Text message = event.getMessage();
        final TextContent textContent = message.getContent();

        if (textContent instanceof LiteralTextContent literalString) {
            MutableText withClickEvent = ((MutableText) ChatLink.newChatWithLinks(literalString.string()));
            withClickEvent.setStyle(message.getStyle());
            event.setMessage(withClickEvent);
            return;
        }

        if (textContent instanceof TranslatableTextContent translatableText
                && translatableText.getKey().equals("chat.type.text")) {

            final MutableText modified = MutableText.of(translatableText).setStyle(message.getStyle());
            final Object[] args = translatableText.getArgs();
            Text txt = ((Text) args[1]);
            args[1] = ChatLink.newChatWithLinks(txt.getString());
            event.setMessage(modified);
        }

    }

}
