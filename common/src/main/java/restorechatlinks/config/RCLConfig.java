package restorechatlinks.config;

import net.minecraft.ChatFormatting;
import restorechatlinks.RestoreChatLinks;

public final class RCLConfig {

    public static boolean underlineLink = false;
    public static boolean colorLink = false;
    public static String colorName = RestoreChatLinks.serializeLegacyFormat(ChatFormatting.BLUE);
    public static boolean convertFormattingCodes = false;
    public static boolean debugMessage = false;
}
