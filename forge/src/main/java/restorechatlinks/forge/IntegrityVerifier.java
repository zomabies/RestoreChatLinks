package restorechatlinks.forge;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.SecureJar.Status;
import cpw.mods.niofs.union.UnionPath;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class IntegrityVerifier {

    private static final Logger LOGGER = LogManager.getLogger("RCL-Verifier");

    static Status selfVerify(IModFile modFile, boolean isProduction) {

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

            // Forge 1.20.2+ SecureModules compact
            boolean isSecureModule = !jarContents.isEmpty() && !(jarContents.get(0) instanceof UnionPath);
            if (isSecureModule) {
                LOGGER.debug("Verifier run under Forge SecureModule");
            }
            Function<Path, String> pathToString = path -> {
                String p = path.toString();
                if (isSecureModule) {
                    return p.startsWith("/") ? p.substring(1) : p;
                }
                return p;
            };

            boolean isTampered = false;
            boolean isAppended = false;

            List<Path> extraFiles = new ArrayList<>();
            List<Path> invalidFiles = new ArrayList<>();

            final Manifest actualManifestPaths = secureJar.moduleDataProvider().getManifest();
            final Set<String> manifestPaths = actualManifestPaths.getEntries().keySet();

            for (Path path : jarContents) {
                final String stripPath = pathToString.apply(path); // Forge 1.20.2+ SecureModules compact

                final Attributes sjhTrustedManifestPath = secureJar.getTrustedManifestEntries(stripPath);
                final Attributes fileManifestAttr = actualManifestPaths.getAttributes(stripPath);

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

                Status status;
                if (isSecureModule) {
                    try {
                        // path in manifest is not same with ZipPath ("/" prefix)
                        // manually provide fixed path for verify
                        final CodeSigner[] signers = secureJar
                                .moduleDataProvider()
                                .verifyAndGetSigners(stripPath, Files.readAllBytes(path));
                        status = signers != null ? secureJar.getFileStatus(stripPath) : Status.INVALID;
                    } catch (IOException e) {
                        LOGGER.error("Failed to verify file (assume valid), via SecureModule", e);
                        status = Status.VERIFIED;
                    }
                } else {
                    status = secureJar.verifyPath(path);
                }

                if (status != Status.VERIFIED) {
                    invalidFiles.add(path);
                    isTampered = true;
                    if (isProduction) {
                        LOGGER.error("Jar contain invalid content: {}", () -> path);
                    }
                }
            }

            Set<String> allPaths = jarContents.stream().map(pathToString).collect(Collectors.toUnmodifiableSet());
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
}
