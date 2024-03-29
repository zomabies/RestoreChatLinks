package restorechatlinks.forge.config;

import net.minecraft.util.Formatting;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;
import restorechatlinks.config.RCLConfig;

import java.util.Collection;

public class Config {

    public static final ForgeConfigSpec clientSpec;
    public static final Client CLIENT;

    static {
        final Pair<Client, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Client::new);
        clientSpec = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    public static class Client {

        public static ForgeConfigSpec.BooleanValue underlineLink;
        public static ForgeConfigSpec.BooleanValue colorLink;
        public static ForgeConfigSpec.ConfigValue<String> colorName;
        public static ForgeConfigSpec.BooleanValue debugMessage;
        public static ForgeConfigSpec.BooleanValue convertFormattingCodes;

        Client(ForgeConfigSpec.Builder builder) {
            builder.comment("General configuration settings").push("client");

            underlineLink = builder
                    .comment("Show underline on link")
                    .define("underlineLink", false);

            colorLink = builder
                    .comment("Enable color on link")
                    .define("colorLink", false);

            final Collection<String> colorNames = Formatting.getNames(true, false);
            colorNames.remove(Formatting.RESET.getName());

            colorName = builder
                    .comment("Color of the link", "Valid colors: ", String.join(", ", colorNames))
                    .defineInList("colorName", Formatting.BLUE.getName(), colorNames);

            convertFormattingCodes = builder
                    .comment("Prevent link detection issues with message containing formatting codes")
                    .comment("Required for received messages that contains it")
                    .define("convertFormattingCode", false);

            debugMessage = builder
                    .comment("Show raw message for debugging")
                    .define("debugMessage", false);

            builder.pop();
        }
    }

    public static void reloadConfig() {
        RCLConfig.underlineLink = Client.underlineLink.get();
        RCLConfig.colorLink = Client.colorLink.get();
        RCLConfig.colorName = Client.colorName.get();
        RCLConfig.convertFormattingCodes = Client.convertFormattingCodes.get();
        RCLConfig.debugMessage = Client.debugMessage.get();
    }

}
