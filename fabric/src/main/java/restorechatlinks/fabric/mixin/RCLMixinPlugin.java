package restorechatlinks.fabric.mixin;

import com.google.common.collect.ImmutableMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class RCLMixinPlugin implements IMixinConfigPlugin {
    // adapted from https://github.com/Juuxel/Adorn/blob/1.19/fabric/src/main/java/juuxel/adorn/AdornMixinPlugin.java

    private static final Logger LOGGER = LogManager.getLogger(RCLMixinPlugin.class);

    private static final Supplier<Boolean> TRUE = () -> true;

    @SuppressWarnings("removal")
    public static final boolean LOAD_LEGACY_IMPL = AccessController.doPrivileged(
            (PrivilegedAction<Boolean>) () -> Boolean.getBoolean("rcl.loadLegacyMixin")
    );

    private static final Map<String, Supplier<Boolean>> CONDITIONS = ImmutableMap.of(
            "restorechatlinks.fabric.mixin.MixinMessageHandler", () -> {
                if (LOAD_LEGACY_IMPL) {
                    LOGGER.debug("Legacy version loaded.");
                }
                return LOAD_LEGACY_IMPL;
            }
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return CONDITIONS.getOrDefault(mixinClassName, TRUE).get();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

}
