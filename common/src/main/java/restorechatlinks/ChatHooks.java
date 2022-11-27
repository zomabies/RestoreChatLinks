package restorechatlinks;

import net.minecraft.text.LiteralTextContent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;

public class ChatHooks {

    public static void onSystemMessage(ClientGameChatEvent event) {

        final TextContent systemMessage = event.getMessage().getContent();

        if (systemMessage instanceof LiteralTextContent literalString) {
            Text withClickEvent = ChatLink.newChatWithLinks(literalString.string());
            event.setMessage(withClickEvent);
            return;
        }

        if (systemMessage instanceof TranslatableTextContent translatableText
                && translatableText.getKey().equals("chat.type.text")) {
            final MutableText modified = MutableText.of(translatableText);
            final Object[] args = translatableText.getArgs();
            Text txt = ((Text) args[1]);
            args[1] = ChatLink.newChatWithLinks(txt.getString());
            event.setMessage(modified);
        }

    }

}
