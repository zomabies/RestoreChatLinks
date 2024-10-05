import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

class SignJarExt {

    static boolean hasSigning(Project project) {
        return project.hasProperty('keyStore')
                && project.hasProperty('keyStoreAlias')
                && project.findProperty('keyStorePass')
                && project.findProperty('keyStoreType')
    }

    static TaskProvider<SignJar> createSignJarTask(
            Project project,
            Provider<? extends Jar> jarTask,
            boolean hasSigning,
            String customName = null) {

        def tasks = project.tasks
        def projectName = project.name
        if (customName != null) {
            projectName = customName
        }

        // https://github.com/Darkhax-Minecraft/Minecraft-Modding-Template/blob/forge-1.16.5/gradle/forge.gradle
        return tasks.register("sign${projectName.capitalize()}Jar", SignJar) {

            onlyIf("No signing key") {
                hasSigning
            }
            it.description = "Signs ${projectName} Jar"

            if (hasSigning) {

                it.keyStore = project.findProperty('keyStore')
                it.alias = project.findProperty('keyStoreAlias')
                it.storePass = project.findProperty('keyStorePass')
                it.keyPass = project.findProperty('keyPass')
                it.storeType = project.findProperty('keyStoreType')
                it.sigFile = 'RCL'

                it.inputFile = jarTask.get().archiveFile
                it.outputFile = jarTask.get().archiveFile
                //it.refTimeStampFile = 'mods.toml'

                // hash algorithm
                it.digestAlg = 'SHA-256'
                it.sigAlg = 'EdDSA'

                // timestamping
                if (project.hasProperty('tsa') && !project.gradle.startParameter.offline) {
                    //noinspection HttpUrlsUsage
                    it.tsa = 'http://timestamp.sectigo.com?td=sha256'
                    it.tsaDigestAlg = 'SHA-256'
                }

                //it.verbose = 'all'
                //it.signedFileName = "${remapJar.archiveFileName.get()}-signed.jar"
                //it.exclude('META-INF/mods.toml')

                project.logger.debug("${projectName}: Configured Jar signing for this build")

                //dependsOn rmpJarTask
            } else {
                project.logger.info("${projectName}: Skipped Jar signing. No keyStore property could be found")
            }
        }
    }


}
