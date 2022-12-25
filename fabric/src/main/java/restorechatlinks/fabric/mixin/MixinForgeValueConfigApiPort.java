package restorechatlinks.fabric.mixin;

import net.minecraft.text.Text;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.configured.ForgeValue;
import net.minecraftforge.configured.ReflectionHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@SuppressWarnings("UnstableApiUsage")
@Pseudo
@Mixin(ForgeValue.class)
public class MixinForgeValueConfigApiPort {

    @Shadow(remap = false)
    @Final
    public ForgeConfigSpec.ValueSpec valueSpec;

    @Unique
    private Field rcl$rangeField;

    @Inject(
            method = "Lnet/minecraftforge/configured/ForgeValue;getValidationHint()Lnet/minecraft/text/Text;",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private void rcl$fixDefineInListAssumeAsRange(CallbackInfoReturnable<Text> cir) {
        // Fixes a crash when editing "defineInList" from the "Configured" integration gui
        try {
            // From net.minecraftforge.configured.ForgeValue.loadRange
            if (rcl$rangeField == null) {
                rcl$rangeField = ReflectionHelper.getDeclaredField(ForgeConfigSpec.ValueSpec.class, "range");
            } else {
                final Object o = rcl$rangeField.get(valueSpec);
                if (o == null) {
                    cir.setReturnValue(null);
                }
            }
        } catch (IllegalAccessException ignored) {
            cir.setReturnValue(null);
        }
    }

}
