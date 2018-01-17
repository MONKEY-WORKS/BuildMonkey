/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.pde

import de.monkeyworks.buildmonkey.equinox.api.parameter.PlatformArchitecture
import de.monkeyworks.buildmonkey.equinox.embedding.EquinoxEmbedder
import de.monkeyworks.buildmonkey.pde.common.TargetPlatformPreparer
import de.monkeyworks.buildmonkey.pde.extension.TargetPlatformExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.nio.file.Files
import java.nio.file.Path

class TargetPlatformPlugin implements Plugin<Project> {
    // name of the root node in the DSL
    static String DSL_EXTENSION_NAME = "targetPlatform"

    // task names
    static final String TASK_NAME_ASSEMBLE_TARGET_PLATFORM = "assembleTargetPlatform"

    @Override
    void apply(Project project) {
        configureProject(project)

        validateDslBeforeBuildStarts(project)
        addTaskAssembleTargetPlatform(project)
    }

    static void configureProject(Project project) {
        // add extension
        project.extensions.create(DSL_EXTENSION_NAME, TargetPlatformExtension, project)

        // make the withEclipseBundle(String) method available in the build script
        project.ext.withEclipseBundle = { String pluginName -> DependencyUtils.calculatePluginDependency(project, pluginName) }
    }

    static void validateDslBeforeBuildStarts(Project project) {
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

    static void addTaskAssembleTargetPlatform(Project project) {
        project.task(TASK_NAME_ASSEMBLE_TARGET_PLATFORM) {
            EquinoxEmbedder embedder = EquinoxEmbedder.INSTANCE()
            embedder.configure(project)

            def outputDir = new File("${project.buildDir}/products/linux")

            group = 'Build Monkey PDE plugins'
            description = "Assembles an P2 repository distribution based on the target platform definition."
            project.afterEvaluate { inputs.file project.targetPlatform.targetDefinition }
            project.afterEvaluate { outputs.dir  outputDir}
            doLast {

                Path targetDir = project.buildDir.toPath().resolve("equinox")
                Files.createDirectories(targetDir)

                if(Files.exists(targetDir.resolve("configuration")))
                    targetDir.resolve("configuration").deleteDir()

                embedder.bootstrap(targetDir.toAbsolutePath().toString())

                def preparer = new TargetPlatformPreparer(embedder, new File("${project.buildDir}"))
                preparer.prepareTargetPlatform(project.targetPlatform.targetDefinition, outputDir, PlatformArchitecture.LINUX_GTK_64)
            }
        }
    }
}
