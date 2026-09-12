package restorechatlinks.fabric.mixin;

import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import restorechatlinks.ChatHooks;

@Mixin(value = ChatListener.class)
public class MixinMessageHandler {

    @ModifyVariable(
            method = "Lnet/minecraft/client/multiplayer/chat/ChatListener;handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Component rcl$change_onGameMessage(Component message) {
        return ChatHooks.onSystemMessage(message);
    }


    @ModifyVariable(
            method = "Lnet/minecraft/client/multiplayer/chat/ChatListener;handleDisguisedChatMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Component rcl$change_profilelessMessage(Component decorated) {
        return ChatHooks.onSystemMessage(decorated);
    }


    @ModifyVariable(
            method = "Lnet/minecraft/client/multiplayer/chat/ChatListener;showMessageToPlayer(Lnet/minecraft/network/chat/ChatType$Bound;Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/network/chat/Component;Lcom/mojang/authlib/GameProfile;ZLjava/time/Instant;)Z",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;evaluateTrustLevel(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/network/chat/Component;Ljava/time/Instant;)Lnet/minecraft/client/multiplayer/chat/ChatTrustLevel;"
            ),
            argsOnly = true
    )
    private Component rcl$processPlayerMessage(Component decorated) {
        // include both signed and unsigned messages
        return ChatHooks.processMessage(decorated);
    }
}
