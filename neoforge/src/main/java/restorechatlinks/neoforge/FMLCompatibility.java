package restorechatlinks.neoforge;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.jar.JarFile;

class FMLCompatibility {

    private static final Logger LOGGER = LogManager.getLogger(FMLCompatibility.class);

    private static boolean isProduction;
    private static final boolean hasSuccessInitialized;
    private static LoadingModList loadingModList;

    static {
        hasSuccessInitialized = hasNewFMLRewrite() && tryInitialize();
    }

    private static boolean tryInitialize() {
        try {
            Class<FMLLoader> fmlLoaderCls = FMLLoader.class;
            Method getCurrentMethod = fmlLoaderCls.getMethod("getCurrent");
            Method getLoadingModListMethod = fmlLoaderCls.getMethod("getLoadingModList");
            Method isProductionMethod = fmlLoaderCls.getMethod("isProduction");

            FMLLoader currentLoader = (FMLLoader) getCurrentMethod.invoke(null);
            loadingModList = (LoadingModList) getLoadingModListMethod.invoke(currentLoader);
            isProduction = ((boolean) isProductionMethod.invoke(currentLoader));
            return true;
        } catch (NoSuchMethodException e) {
            LOGGER.error("FML method not found", e);
            return false;
        } catch (InvocationTargetException | IllegalAccessException | SecurityException e) {
            LOGGER.error("Unable to get FML related instances", e);
            return false;
        }
    }

    public static boolean isProduction() {
        if (hasSuccessInitialized) {
            return isProduction;
        } else {
            return FMLLoader.isProduction();
        }
    }

    public static boolean hasNewFMLRewrite() {
        try {
            Class.forName("net.neoforged.fml.jarcontents.JarContents");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static JarFile getUnderlyingJarFileFromFML(String modId) {
        if (!hasSuccessInitialized) {
            return null;
        }
        try {
            Class<IModFile> iModFileCls = IModFile.class;
            Method getContents = iModFileCls.getMethod("getContents"); // JarContents

            IModFileInfo modFileInfo = loadingModList.getModFileById(modId);
            Object jarContents = getContents.invoke(modFileInfo.getFile());

            Class<?> jarFileContentCls = Class.forName("net.neoforged.fml.jarcontents.JarFileContents");

            if (jarFileContentCls.isInstance(jarContents)) {
                try {
                    // JarFileContents.jarFile
                    Field jarFileField = jarFileContentCls.getDeclaredField("jarFile");
                    jarFileField.setAccessible(true);
                    return ((JarFile) jarFileField.get(jarContents));
                } catch (NoSuchFieldException e) {
                    LOGGER.error("Error obtaining JarFileContents.jarFile", e);
                    return null;
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            LOGGER.error("JarFileContent not found?", e);
        } catch (InvocationTargetException | IllegalAccessException | SecurityException e) {
            LOGGER.error("Error accessing JarFileContents", e);
        }
        return null;
    }
}
