package restorechatlinks.forge;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.SecureJar.Status;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import restorechatlinks.JarValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public final class IntegrityVerifier {

    private static final Logger LOGGER = LogManager.getLogger("RCL-Verifier");
    private static final boolean SKIP_SJH_VERIFY = Boolean.getBoolean("rcl.skipSjhVerify");

    static Status selfVerify(IModFile modFile, boolean isProduction) {
        if (SKIP_SJH_VERIFY) {
            LOGGER.debug("Skip SJH verify for {}", modFile.getFileName());
            return Status.NONE;
        }
        final SecureJar secureJar = modFile.getSecureJar();

        // reference from net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModProvider
        try (Stream<Path> pathStream = Files.find(secureJar.getRootPath(),
                Integer.MAX_VALUE,
                (path, attr) -> {
                    String filename = path.toString();
                    return path.getNameCount() > 0
                            && !attr.isDirectory()
                            && !(filename.endsWith(".MF") || filename.endsWith(".EC") || filename.endsWith(".SF")); // certs
                })) {

            List<Path> jarContents = pathStream.collect(Collectors.toList());

            boolean isTampered = false;
            boolean isAppended = false;

            List<Path> extraFiles = new ArrayList<>();
            List<Path> invalidFiles = new ArrayList<>();

            final Manifest rawJarManifest = getJarManifest(modFile.getFilePath());
            final Manifest actualManifestPaths = secureJar.moduleDataProvider().getManifest();

            boolean isSame = rawJarManifest.equals(secureJar.moduleDataProvider().getManifest());
            if (!isSame) {
                LOGGER.debug("SJH manifest ({}) is unexpectedly modified, repopulate entries", modFile.getFileName());
                for (Map.Entry<String, Attributes> entry : rawJarManifest.getEntries().entrySet()) {
                    String name = entry.getKey(); // per-entry "Name:"
                    Attributes attributes = entry.getValue();
                    actualManifestPaths.getEntries().put(name, attributes);
                }
            }

            final Set<String> manifestPaths = actualManifestPaths.getEntries().keySet();

            for (Path path : jarContents) {
                final String pathName = path.toString();

                final Attributes sjhTrustedManifestPath = secureJar.getTrustedManifestEntries(pathName);
                final Attributes fileManifestAttr = actualManifestPaths.getAttributes(pathName);

                // cpw.mods.jarhandling.impl.Jar::verifyAndGetSigners treat extra files as "VERIFIED"
                if (sjhTrustedManifestPath == null
                        && (fileManifestAttr == null || fileManifestAttr.getValue("SHA-256-Digest") == null)) {
                    isAppended = true; //contain extra files
                    extraFiles.add(path);
                    if (isProduction) {
                        LOGGER.warn("Jar contain extra files: {}", () -> path);
                    }
                    continue;
                }

                Status status = secureJar.verifyPath(path);
                if (status != Status.VERIFIED) {
                    invalidFiles.add(path);
                    isTampered = true;
                    if (isProduction) {
                        LOGGER.error("Jar contain invalid content: {}", () -> path);
                    }
                }
            }

            Set<String> allPaths = jarContents.stream().map(Path::toString).collect(Collectors.toUnmodifiableSet());
            manifestPaths.removeAll(allPaths);
            final boolean hasMissingFile = manifestPaths.size() > 0;
            if (hasMissingFile) {
                for (String path : manifestPaths) {
                    if (isProduction) {
                        LOGGER.warn("Jar contain missing file: {}", () -> path);
                    }
                }
            }

            Status jarStatus = (isAppended || hasMissingFile || isTampered) ? Status.INVALID : Status.VERIFIED;
            modFile.setSecurityStatus(isProduction ? jarStatus : Status.NONE);
            if (FMLLoader.isProduction() && jarStatus == Status.INVALID) {
                throw new SecurityException("Jar Has been tampered! " + isAppended + " " + hasMissingFile + " " + isTampered);
            }

            return isProduction ? jarStatus : Status.NONE;
        } catch (IOException e) {
            System.out.println("Error while trying to self verify");
            e.printStackTrace();
            return Status.NONE;
        }
    }

    static void handleStatus(Status status, IModFile modFile) {
        if (IntegrityVerifier.SKIP_SJH_VERIFY) {
            return;
        }
        switch (status) {
            case VERIFIED:
                if (FMLLoader.isProduction()) {
                    CodeSigner[] signers = modFile.getSecureJar().getManifestSigners();
                    boolean match = JarValidator.hasSignersMatch(RestoreChatLinksForge.MOD_SIGNATURE, signers);
                    if (!match) {
                        throw new SecurityException("JAR fingerprint not expected");
                    }
                }
                break;
            case NONE:
            case INVALID:
            case UNVERIFIED:
            default:
                if (FMLLoader.isProduction()) {
                    throw new SecurityException("JAR file is tampered! " + modFile.getFileName());
                } else {
                    LOGGER.debug("DEV mode, ignoring sign status");
                }
                break;
        }
    }

    private static Manifest getJarManifest(Path path) throws IOException {
        try (JarFile jar = new JarFile(path.toFile(), false, ZipFile.OPEN_READ)) {
            return jar.getManifest();
        }
    }
}
