package restorechatlinks.mixin;

import restorechatlinks.ChatHooks;
import restorechatlinks.ClientGameChatEvent;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MessageHandler.class)
public class MixinMessageHandler {

    @ModifyVariable(
            method = "Lnet/minecraft/client/network/message/MessageHandler;onGameMessage(Lnet/minecraft/text/Text;Z)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Text rcl$change_onGameMessage(Text message) {
        ClientGameChatEvent event = new ClientGameChatEvent(message);
        ChatHooks.onSystemMessage(event);
        return event.isCancelled() ? null : event.getMessage();
    }

    @Inject(
            method = "Lnet/minecraft/client/network/message/MessageHandler;onGameMessage(Lnet/minecraft/text/Text;Z)V",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private void rcl$cancel_onGameMessage(Text message, boolean overlay, CallbackInfo ci) {
        if (message == null) {
            ci.cancel();
        }
    }


    @ModifyVariable(
            method = "Lnet/minecraft/client/network/message/MessageHandler;onProfilelessMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageType$Parameters;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Text rcl$change_profilelessMessage(Text decorated) {
        ClientGameChatEvent event = new ClientGameChatEvent(decorated);
        ChatHooks.onSystemMessage(event);
        return event.isCancelled() ? null : event.getMessage();
    }

    @Inject(
            method = "Lnet/minecraft/client/network/message/MessageHandler;onProfilelessMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageType$Parameters;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void rcl$cancel_profilelessMessage(Text message, MessageType.Parameters params, CallbackInfo ci) {
        if (message == null) {
            ci.cancel();
        }
    }
}