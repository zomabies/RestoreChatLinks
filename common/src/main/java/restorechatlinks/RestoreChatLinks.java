package restorechatlinks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class RestoreChatLinks {
    public static final String MOD_ID = "restorechatlinks";

    private static final Logger LOGGER = LogManager.getLogger(RestoreChatLinks.class);

    public static void init() {

        // System.out.println(ExampleExpectPlatform.getConfigDirectory().toAbsolutePath().normalize().toString());
    }


    // https://github.com/Darkhax-Minecraft/Minecraft-Modding-Template/blob/forge-1.16.5/src/main/java/net/darkhax/examplemod/ExampleMod.java#L39-L86
    public static boolean validJarSignature(File file) {

        // Only JAR file signatures can be verified here.
        if (file.exists() && !file.isDirectory()) {

            try (JarFile jar = new JarFile(file)) {

                boolean hasFailed = false;
                final Enumeration<JarEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {

                    final JarEntry entry = entries.nextElement();
                    try (final InputStream is = jar.getInputStream(entry)) {

                        final byte[] buffer = new byte[8192];
                        while (is.read(buffer, 0, buffer.length) != -1) {
                            // In Java 8+ we need to read the data if we actually want the code
                            // signers to be verified. Invalid signatures will throw errors
                            // when read which are caught.
                        }
                    }
                    // This exception is raised when the contents of a file do not match the
                    // expected signature. We don't hard fail right away to allow all
                    // violations to be logged.
                    catch (SecurityException e) {
                        hasFailed = true;
                        LOGGER.catching(e);
                    }
                }
                return !hasFailed;
            } catch (final IOException e) {
                //e.printStackTrace();
                return false;
            }
        }
        return false;
    }
}
