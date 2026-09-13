package restorechatlinks;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RestoreChatLinks {
    public static final String MOD_ID = "restorechatlinks";

    private static final Logger LOGGER = LogManager.getLogger(RestoreChatLinks.class);

    public static void init() {

        // System.out.println(ExampleExpectPlatform.getConfigDirectory().toAbsolutePath().normalize().toString());
    }

    public static String serializeLegacyFormat(ChatFormatting format) {
        return Objects.requireNonNull(TextColor.fromLegacyFormat(format)).serialize();
    }

    /* read only */
    public static List<String> getBuiltInColors() {
        class Holder {
            private final static List<String> COLORS;

            static {
                List<String> colors = new ArrayList<>();
                for (ChatFormatting chatFormatting : ChatFormatting.values()) {
                    TextColor textColor = TextColor.fromLegacyFormat(chatFormatting);
                    if (textColor != null) {
                        colors.add(textColor.serialize());
                    }
                }
                COLORS = List.copyOf(colors);
            }
        }
        return Holder.COLORS;
    }

}
