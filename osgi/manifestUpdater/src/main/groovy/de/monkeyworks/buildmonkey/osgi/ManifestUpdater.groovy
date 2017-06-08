package de.monkeyworks.buildmonkey.osgi

import org.gradle.api.Plugin
import org.gradle.api.Project

import java.util.jar.Manifest

/**
 *
 * Update manifest files
 *
 * Created by Johannes Tandler on 02.06.17.
 */
class ManifestUpdater implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.extensions.create("manifestUpdate", ManifestUpdaterExtension)

        addBundleVersionUpdate(project)
        addDependencyVersionUpdate(project)
    }

    /**
     * task to update osgi versions
     * @param project
     */
    void addDependencyVersionUpdate(Project project) {
        project.tasks.create("updateDependencyVersions") { task ->
            task.description = "Updates the dependency version inside the manifest files"

            task.doLast {
                ManifestUpdaterExtension config = project.extensions.manifestUpdate
                if(!config.dependencyUpdates)
                    return


                File file = new File("${project.projectDir}/META-INF/MANIFEST.MF")
                FileInputStream fis = new FileInputStream(file)
                Manifest manifest = new Manifest(fis)
                fis.close()

                ManifestParser parser = new ManifestParser(manifest)
                List<RequiredBundle> bundles = parser.parseRequireBundles()

                String newRequireBundleString = null

                for(RequiredBundle bundle : bundles) {
                    String newVersion = config.dependencyUpdates(bundle.bundleName)
                    if(newVersion != null) {
                        bundle.attributes.put("bundle-version", newVersion)
                    }

                    if(newRequireBundleString)
                        newRequireBundleString += ","
                    else
                        newRequireBundleString = ""

                    newRequireBundleString += bundle.toString()
                }

                if(newRequireBundleString)
                    manifest.getMainAttributes().putValue("Require-Bundle", newRequireBundleString)

                FileOutputStream fos = new FileOutputStream(file)
                manifest.write(fos)
                fos.close()
            }
        }
    }

    /**
     * task to update bundle version itself
     * @param project
     */
    void addBundleVersionUpdate(Project project) {
        project.tasks.create("setManifestVersion") { task ->
            task.description = "Set's the Bundle-Version attribute of the given MANIFEST.MF"

            task.doLast {
                ManifestUpdaterExtension config = project.extensions.manifestUpdate
                if(!config.version)
                    return

                File file = new File("${project.projectDir}/META-INF/MANIFEST.MF")
                FileInputStream fis = new FileInputStream(file)
                Manifest manifest = new Manifest(fis)
                fis.close()

                String newVersion = config.version
                if(config.addQualifier && !newVersion.endsWith(".qualifier")) {
                    newVersion += ".qualifier"
                }

                manifest.getMainAttributes().putValue("Bundle-Version", newVersion)

                FileOutputStream fos = new FileOutputStream(file)
                manifest.write(fos)
                fos.close()
            }
        }
    }
}
