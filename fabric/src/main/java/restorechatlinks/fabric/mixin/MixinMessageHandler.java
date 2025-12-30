package restorechatlinks.fabric.mixin;

import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import restorechatlinks.ChatHooks;

@Mixin(value = MessageHandler.class)
public class MixinMessageHandler {

    @ModifyVariable(
            method = "Lnet/minecraft/client/network/message/MessageHandler;onGameMessage(Lnet/minecraft/text/Text;Z)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Text rcl$change_onGameMessage(Text message) {
        return ChatHooks.onSystemMessage(message);
    }


    @ModifyVariable(
            method = "Lnet/minecraft/client/network/message/MessageHandler;onProfilelessMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageType$Parameters;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Text rcl$change_profilelessMessage(Text decorated) {
        return ChatHooks.onSystemMessage(decorated);
    }


    @ModifyVariable(
            method = "Lnet/minecraft/client/network/message/MessageHandler;processChatMessageInternal(Lnet/minecraft/network/message/MessageType$Parameters;Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/text/Text;Lcom/mojang/authlib/GameProfile;ZLjava/time/Instant;)Z",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/client/network/message/MessageHandler;getStatus(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/text/Text;Ljava/time/Instant;)Lnet/minecraft/client/network/message/MessageTrustStatus;"
            ),
            argsOnly = true
    )
    private Text rcl$processPlayerMessage(Text decorated) {
        // include both signed and unsigned messages
        return ChatHooks.processMessage(decorated);
    }
}
