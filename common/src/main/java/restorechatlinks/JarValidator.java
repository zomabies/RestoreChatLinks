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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class JarValidator {
    private static final Logger LOGGER = LogManager.getLogger("RCL-JarValidator");
    private static final boolean SKIP_JAR_VALIDATE = Boolean.getBoolean("rcl.skipJarValidate");
    private static final boolean WARN_JAR_VALIDATE = Boolean.getBoolean("rcl.warnJarValidate");
    private static final Result EMPTY = new Result.Empty();

    private final JarFile jarFile;
    private final boolean shouldCloseJar;

    private Manifest manifest;
    private CodeSigner[] manifestSigners;

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

    public Result validate() {
        if (isEmpty()) {
            return EMPTY;
        }

        Result result = EMPTY;
        if (SKIP_JAR_VALIDATE) {
            LOGGER.warn("JAR validation skipped for {}", jarFile.getName());
        } else {
            processManifest();
            if (isSigned()) {
                result = verifyAllFiles();
            } else {
                result = Result.ofUnsigned(jarFile.getName());
            }
        }

        if (shouldCloseJar) {
            IOUtils.closeQuietly(jarFile);
        }

        return result;
    }

    private void processManifest() {
        try {
            JarEntry manifestEntry = jarFile.getJarEntry(JarFile.MANIFEST_NAME);
            try (InputStream is = jarFile.getInputStream(manifestEntry)) {
                is.readAllBytes();
            }
            this.manifest = jarFile.getManifest();
            this.manifestSigners = manifestEntry.getCodeSigners();
        } catch (IOException | SecurityException e) {
            LOGGER.warn("Error processing manifest, treating as unsigned", e);
        }
    }

    public boolean isEmpty() {
        return jarFile == null;
    }

    public boolean isSigned() {
        return manifestSigners != null && manifestSigners.length > 0;
    }

    // https://github.com/Darkhax-Minecraft/Minecraft-Modding-Template/blob/forge-1.16.5/src/main/java/net/darkhax/examplemod/ExampleMod.java#L39-L86
    private Result verifyAllFiles() {
        ArrayList<String> invalidFiles = new ArrayList<>();
        ArrayList<String> extraFiles = new ArrayList<>();
        ArrayList<String> missingFiles = new ArrayList<>();

        Enumeration<JarEntry> entries = jarFile.entries();
        ArrayList<String> allFiles = new ArrayList<>();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();

            String filePath = entry.getName();
            if (entry.isDirectory() || isSigningRelated(filePath)) {
                continue;
            }
            allFiles.add(filePath);

            try (final InputStream is = jarFile.getInputStream(entry)) {
                final byte[] buffer = new byte[8192];
                while (is.read(buffer, 0, buffer.length) != -1) {
                    // In Java 8+ we need to read the data if we actually want the code
                    // signers to be verified. Invalid signatures will throw errors
                    // when read which are caught.
                }
                CodeSigner[] entryCS = entry.getCodeSigners();
                if (entryCS == null) {
                    // unsigned file, assume appended
                    LOGGER.warn("JAR contain extra files: {}", filePath);
                    extraFiles.add(filePath);
                }
            }
            // This exception is raised when the contents of a file do not match the
            // expected signature. We don't hard fail right away to allow all
            // violations to be logged.
            catch (SecurityException e) {
                e.setStackTrace(new StackTraceElement[0]);
                LOGGER.error("JAR contain invalid file: {}", filePath, e);
                invalidFiles.add(filePath);
            } catch (IOException e) {
                LOGGER.warn("IO Error when opening {}", filePath, e);
            }
        }

        ArrayList<String> declaredFiles = getDeclaredManifestFiles();
        if (declaredFiles.removeAll(allFiles)) {
            for (String path : declaredFiles) {
                LOGGER.error("JAR contain missing file: {}", path);
                missingFiles.add(path);
            }
        }

        return new Result(jarFile.getName(), manifestSigners, invalidFiles, missingFiles, extraFiles);
    }

    public static boolean hasSignersMatch(String jarFingerprint, CodeSigner[] signers) {
        String replacedFP = jarFingerprint.replaceAll(":", "");

        for (CodeSigner codeSigner : signers) {
            if (codeSigner == null) {
                continue;
            }
            for (Certificate cert : codeSigner.getSignerCertPath().getCertificates()) {
                try {
                    String currentFP = DigestUtils.sha256Hex(cert.getEncoded());
                    boolean match = currentFP.equalsIgnoreCase(replacedFP);
                    if (match) {
                        return true;
                    }
                } catch (CertificateEncodingException ignored) {
                }
            }
        }
        return false;
    }


    public static JarValidator of(Path jarPath) {
        File file = jarPath.toFile();
        if (file.exists() && !file.isDirectory()) {
            try {
                return new JarValidator(new JarFile(file), true);
            } catch (IOException | SecurityException e) {
                LOGGER.warn("Error opening JAR", e);
            }
        }
        return new JarValidator();
    }

    public static JarValidator ofExisting(JarFile jarFile) {
        return new JarValidator(jarFile);
    }


    public static class Result {
        private final String jarFile;
        private final CodeSigner[] manifestSigners;
        private final List<String> invalidFiles;
        private final List<String> missingFiles;
        private final List<String> extraFiles;

        Result(String jarFile,
               CodeSigner[] cs,
               List<String> invalidFiles,
               List<String> missingFiles,
               List<String> extraFiles) {
            this.jarFile = jarFile;
            this.manifestSigners = cs;
            this.invalidFiles = invalidFiles;
            this.missingFiles = missingFiles;
            this.extraFiles = extraFiles;
        }

        static Result ofUnsigned(String jarFile) {
            return new Result(jarFile, null, List.of(), List.of(), List.of());
        }

        public boolean isValid(String jarFingerprint) {
            boolean result = isSigned()
                    && invalidFiles.size() < 1
                    && missingFiles.size() < 1
                    && extraFiles.size() < 1;

            if (jarFingerprint != null && result) {
                result = hasSignersMatch(jarFingerprint, manifestSigners);
            }
            return result;
        }

        public void throwIfInvalid() {
            if (SKIP_JAR_VALIDATE) {
                return;
            }

            if (!isSigned()) {
                throwOrLog(new SecurityException("JAR is not signed: " + jarFile));
                return;
            }

            StringBuilder formatted = new StringBuilder();
            addFilesExceptionFormatting("Invalid files", invalidFiles, formatted);
            addFilesExceptionFormatting("Missing files", missingFiles, formatted);
            addFilesExceptionFormatting("Extra files", extraFiles, formatted);

            if (invalidFiles.size() > 0 || missingFiles.size() > 0 || extraFiles.size() > 0) {
                throwOrLog(new SecurityException("JAR is modified: " + jarFile + "\n" + formatted));
            }
        }

        public void throwIfInvalid(String jarFingerprint) {
            if (SKIP_JAR_VALIDATE) {
                return;
            }
            throwIfInvalid();

            if (jarFingerprint != null && isSigned()) {
                if (!hasSignersMatch(jarFingerprint, manifestSigners)) {
                    String message = "None of the JAR singers matched the fingerprint, [" + jarFingerprint + "]" +
                            " File: " + jarFile;
                    throwOrLog(new SecurityException(message));
                }
            }
        }

        protected boolean isSigned() {
            return manifestSigners != null && manifestSigners.length > 0;
        }

        protected void throwOrLog(RuntimeException exception) {
            if (WARN_JAR_VALIDATE) {
                LOGGER.error(exception);
            } else {
                throw exception;
            }
        }

        private static void addFilesExceptionFormatting(String title, List<String> fileNames, StringBuilder sb) {
            if (fileNames.size() > 0) {
                sb.append(title).append(":").append('\n');
                for (String fileName : fileNames) {
                    sb.append("    ").append(fileName).append('\n');
                }
            }
        }

        private static final class Empty extends Result {
            private Empty() {
                super(null, null, List.of(), List.of(), List.of());
            }

            @Override
            public boolean isValid(String jarFingerprint) {
                return true;
            }

            @Override
            public void throwIfInvalid() {
            }

            @Override
            public void throwIfInvalid(String jarFingerprint) {
            }
        }
    }


    private ArrayList<String> getDeclaredManifestFiles() {
        ArrayList<String> manifestPaths = new ArrayList<>();
        for (Map.Entry<String, Attributes> section : manifest.getEntries().entrySet()) {
            // Attributes.Name, string
            for (Map.Entry<Object, Object> attrEntry : section.getValue().entrySet()) {
                String attrName = attrEntry.getKey().toString();
                if (attrName.toUpperCase(Locale.ROOT).endsWith("-DIGEST")) {
                    String sectionName = section.getKey();
                    manifestPaths.add(sectionName);
                }
            }
        }
        return manifestPaths;
    }

    private static boolean isSigningRelated(String filePath) {
        filePath = filePath.toUpperCase(Locale.ROOT);
        return filePath.endsWith(".SF")
                || filePath.endsWith(".DSA")
                || filePath.endsWith(".RSA")
                || filePath.endsWith(".EC");
    }
}
