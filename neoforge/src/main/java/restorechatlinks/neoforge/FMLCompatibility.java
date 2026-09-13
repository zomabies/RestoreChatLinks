package restorechatlinks.neoforge;

import net.neoforged.fml.jarcontents.JarContents;
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

    private final static LoadingModList loadingModList = FMLLoader.getCurrent().getLoadingModList();

    public static JarFile getUnderlyingJarFileFromFML(String modId) {
        try {
            IModFileInfo modFileInfo = loadingModList.getModFileById(modId);
            JarContents jarContents = modFileInfo.getFile().getContents();

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
        } catch (ClassNotFoundException e) {
            LOGGER.error("JarFileContent not found?", e);
        } catch (IllegalAccessException | SecurityException e) {
            LOGGER.error("Error accessing JarFileContents", e);
        }
        return null;
    }
}
