package restorechatlinks;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarValidator {

    private static final Logger LOGGER = LogManager.getLogger("RCL-JarValidator");

    private final JarFile jarFile;
    private final boolean shouldCloseJar;

    private boolean processed;
    private CodeSigner[] manifestSigners;
    private final ArrayList<String> invalidFiles = new ArrayList<>();

    public JarValidator() {
        this(null, false);
    }

    public JarValidator(JarFile jarFile) {
        this(jarFile, false);
    }

    public JarValidator(JarFile jarFile, boolean shouldCloseJar) {
        this.jarFile = jarFile;
        this.shouldCloseJar = shouldCloseJar;
    }

    public JarValidator validate() {
        if (isEmpty()) {
            return this;
        }
        if (processed) {
            return this;
        }

        processManifest();
        verifyAllFiles();

        if (shouldCloseJar) {
            IOUtils.closeQuietly(jarFile);
        }

        processed = true;
        return this;
    }

    private void processManifest() {
        try {
            JarEntry manifestEntry = jarFile.getJarEntry(JarFile.MANIFEST_NAME);
            try (InputStream is = jarFile.getInputStream(manifestEntry)) {
                is.readAllBytes();
            }
            this.manifestSigners = manifestEntry.getCodeSigners();
        } catch (IOException e) {
            LOGGER.warn("Error processing manifest, treating as unsigned");
        }
    }

    public boolean isEmpty() {
        return jarFile == null;
    }

    public boolean isSigned() {
        return manifestSigners != null && manifestSigners.length > 0;
    }

    public void throwIfInvalid() {
        if (isEmpty()) {
            return;
        }

        if (!isSigned()) {
            throw new SecurityException("JAR is not signed: " + jarFile.getName());
        }

        if (invalidFiles.size() > 0) {
            StringBuilder formatted = new StringBuilder();
            formatted.append("Files: ").append('\n');
            for (String fileName : invalidFiles) {
                formatted.append(fileName).append('\n');
            }
            throw new SecurityException("JAR is modified: " + jarFile.getName() + "\n" + formatted);
        }
    }

    public void throwIfInvalid(String jarFingerprint) {
        throwIfInvalid();

        if (jarFingerprint != null) {
            if (!hasSignersMatch(jarFingerprint, manifestSigners)) {
                String message = "None of the JAR singers matched the fingerprint, [" + jarFingerprint + "]" +
                        " File: " + jarFile.getName();
                throw new SecurityException(message);
            }
        }
    }

    // https://github.com/Darkhax-Minecraft/Minecraft-Modding-Template/blob/forge-1.16.5/src/main/java/net/darkhax/examplemod/ExampleMod.java#L39-L86
    private void verifyAllFiles() {
        final Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {

            final JarEntry entry = entries.nextElement();
            try (final InputStream is = jarFile.getInputStream(entry)) {

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
                LOGGER.error(e);
                invalidFiles.add(entry.getName());
            } catch (IOException e) {
                LOGGER.warn("IO Error when opening {}", entry.getName(), e);
            }
        }
    }

    public static boolean hasSignersMatch(String jarFingerprint, CodeSigner[] signers) {
        boolean match = false;
        String replacedFP = jarFingerprint.replaceAll(":", "");

        for (CodeSigner codeSigner : signers) {
            if (codeSigner == null) {
                continue;
            }
            for (Certificate cert : codeSigner.getSignerCertPath().getCertificates()) {
                try {
                    String currentFP = DigestUtils.sha256Hex(cert.getEncoded());
                    match = currentFP.equalsIgnoreCase(replacedFP);
                    if (match) {
                        break;
                    }
                } catch (CertificateEncodingException ignored) {
                }
            }
        }
        return match;
    }


    public static JarValidator of(Path jarPath) {
        File file = jarPath.toFile();
        if (file.exists() && !file.isDirectory()) {
            try {
                return new JarValidator(new JarFile(file), true);
            } catch (IOException e) {
                LOGGER.warn("Error opening jar", e);
            }
        }
        return new JarValidator();
    }

    public static JarValidator ofExisting(JarFile jarFile) {
        return new JarValidator(jarFile);
    }
}
