/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.pde

import de.monkeyworks.buildmonkey.equinox.api.MetadataRepositoryLoaderService
import de.monkeyworks.buildmonkey.equinox.api.P2DirectorService
import de.monkeyworks.buildmonkey.equinox.api.parameter.InstallFeatureParameter
import de.monkeyworks.buildmonkey.equinox.api.parameter.PlatformArchitecture
import de.monkeyworks.buildmonkey.equinox.embedding.EquinoxEmbedder
import de.monkeyworks.buildmonkey.pde.extension.ProductExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.nio.file.Paths

class ProductMaterialisationPlugin implements Plugin<Project> {
    // name of the root node in the DSL
    static String DSL_EXTENSION_NAME = "product"

    // task names
    static final String TASK_NAME_MATERIALISE_PRODUCT = "materialiseProduct"

    @Override
    void apply(Project project) {
        configureProject(project)

        //validateDslBeforeBuildStarts(project)
        addTaskMaterialiseProduct(project)
    }

    static void configureProject(Project project) {
        // add extension
        project.extensions.create(DSL_EXTENSION_NAME, ProductExtension, project)

        // make the withEclipseBundle(String) method available in the build script
        project.ext.withEclipseBundle = { String pluginName -> DependencyUtils.calculatePluginDependency(project, pluginName) }
    }

/*    static void validateDslBeforeBuildStarts(Project project) {
        // check if the build definition is valid just before the build starts
        project.gradle.taskGraph.whenReady {
            // check if the selected target platform exists for the given Eclipse version
            def targetPlatform = project.targetPlatform
            if (targetPlatform == null) {
                throw new RuntimeException("No target platform is defined.")
            }

            // check if a target platform file is referenced
            def targetDefinition = targetPlatform.targetDefinition
            if (targetDefinition == null || !targetDefinition.exists()) {
                throw new RuntimeException("No target definition file found for '${targetDefinition}'.")
            }

            // check if target definition file is a valid XML
            try {
                new XmlSlurper().parseText(targetDefinition.text)
            } catch(Exception e) {
                throw new RuntimeException("Target definition file '$targetDefinition' must be a valid XML document.", e)
            }
        }
    }
*/
    static void addTaskMaterialiseProduct(Project project) {
        project.task(TASK_NAME_MATERIALISE_PRODUCT, dependsOn: [TargetPlatformPlugin.TASK_NAME_ASSEMBLE_TARGET_PLATFORM,]) {
            EquinoxEmbedder embedder = EquinoxEmbedder.INSTANCE()

            def outputDir = new File("${project.buildDir}/products/${PlatformArchitecture.LINUX_GTK_64.operatingSystem}")

            group = 'Build Monkey PDE plugins'
            description = "Produces a P2 repository containing only the features from the product file and their dependencies."
            project.afterEvaluate { inputs.file project.targetPlatform.targetDefinition }
            project.afterEvaluate { outputs.dir  outputDir}
            doLast {

                // collect  update sites and feature names
                def updateSites = []
                def rootNode = new XmlSlurper().parseText(project.targetPlatform.targetDefinition.text)
                rootNode.locations.location.each { location ->
                    updateSites.add(location.repository.@location.text().replace('\${project_loc}', 'file://' +  project.buildDir.absolutePath))
                }
                updateSites.add(project.product.eclipseUpdateSite)

                def productFileService = embedder.getService(MetadataRepositoryLoaderService.class)

                def features = productFileService.getFeatures("${project.product.productFile}")

                def installIUs = []

                features.each {
                    installIUs.add("${it.id}.feature.group")
                }
                installIUs.add("org.eclipse.equinox.executable")

                def parameter = new InstallFeatureParameter()

                parameter.setArchitecture(PlatformArchitecture.LINUX_GTK_64)
                parameter.setProfile("SDKProfile")
                parameter.setDestination(outputDir.absolutePath)
                parameter.setBundlepool(outputDir.absolutePath)
                parameter.setInstallIU(installIUs.join(","))
                parameter.setRepository(updateSites.join(","))

                def service = embedder.getService(P2DirectorService.class)
                service.installFeaturesToNew(parameter)

            }
        }
    }
}
