package restorechatlinks.fabric.mixin;

import com.google.common.collect.ImmutableMap;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
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

    public static final Supplier<Boolean> HAS_CHAT_HEADS = () -> FabricLoader.getInstance().isModLoaded("chat_heads");

    @Nullable
    @SuppressWarnings("removal")
    public static final Boolean LOAD_LEGACY_IMPL = AccessController.doPrivileged(
            (PrivilegedAction<Boolean>) () -> {
                String rclProperty = System.getProperty("rcl.loadLegacyMixin");
                if (rclProperty == null) {
                    return null;
                }
                return Boolean.getBoolean("rcl.loadLegacyMixin");
            }
    );

    private static final Map<String, Supplier<Boolean>> CONDITIONS = ImmutableMap.of(
            "restorechatlinks.fabric.mixin.MixinMessageHandler", () -> {
                if (LOAD_LEGACY_IMPL == null) {
                    // Chat Heads will not work if canceled via ClientReceiveMessageEvents.ALLOW_CHAT,
                    // since it's mixin is inserted after fabric-api's.
                    // Enable if the system property is not set explicitly.
                    final boolean hasChatHead = HAS_CHAT_HEADS.get();
                    if (hasChatHead) {
                        LOGGER.debug("Chat Heads is present, enabled mixin version");
                    }
                    return hasChatHead;
                }
                if (LOAD_LEGACY_IMPL) {
                    LOGGER.debug("Requested mixin version from system property");
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
