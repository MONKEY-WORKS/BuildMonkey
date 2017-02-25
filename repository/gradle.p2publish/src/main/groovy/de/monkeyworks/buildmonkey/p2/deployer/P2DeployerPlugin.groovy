/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.p2.deployer

import de.monkeyworks.buildmonkey.p2.deployer.util.FeatureHelper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Gradle plugin to bundle all build artifacts into one p2 repository
 *
 * Created by Johannes Tandler on 27.01.17.
 */
public class P2DeployerPlugin implements Plugin<Project> {

    private final String extensionName = "p2Deployment"

    private final String taskName = "publishP2"

    @Override
    void apply(Project project) {

        // create project extension if it doesn't exist yet
        if(project.extensions.findByName(extensionName) == null) {
            project.extensions.create(extensionName, P2DeploymentExtension, project)
        }

        // create bundle task
        project.task(taskName, dependsOn: project.subprojects.jar).doFirst {

            P2DeploymentExtension config = project.p2Deployment

            def jarTasks = doCopyArtifacts(config)

            // create feature
            def featureJar = config.targetRepository.toPath().resolve("features/${config.featureId}-${config.version}.jar")
            FeatureHelper.createJar(config.featureId, 
                config.featureLabel, 
                config.version, "provider", 
                jarTasks, 
                featureJar.toFile())

            
            doBuildP2Repository()
        }
    }

    private Set<Jar> doCopyArtifacts(P2DeploymentExtension config) {
        def pluginsDir = new File(config.targetRepository, "plugins")

        if(!pluginsDir.exists())
            pluginsDir.mkdirs()

        // collect all deployed artifacts
        Set<Jar> jarTasks = new HashSet<>()

        project.subprojects.each { subProject ->
            subProject.tasks.each {
                // copy jar only
                if(it instanceof Jar) {

                    def file = new File(it.destinationDir, it.archiveName)

                    def classifier = ""
                    // fix source classifier
                    if(it.classifier.length()>0) {
                        if(it.classifier == "sources") {
                            classifier = ".source"
                        }
                    }

                    // setup new file name
                    def targetFileName = "${it.baseName}${classifier}_${it.version}.${it.extension}"

                    // copy file
                    Files.copy(file.toPath(), pluginsDir.toPath().resolve(targetFileName), StandardCopyOption.REPLACE_EXISTING)

                    // collect artifact for feature generation
                    jarTasks.add(it)
                }
            }
        }
        return jarTasks
    }


    private void doBuildP2Repository(P2DeploymentExtension config) {
        def eclipseHome = config.eclipseHome
        def launcherJar = eclipseHome.getAbsolutePath() + "/plugins/org.eclipse.equinox.launcher_1.3.201.v20161025-1711.jar"
        def repoDirUri = config.targetRepository.toURI()

        project.exec {
            commandLine 'java', '-jar', launcherJar,
                    '-application', 'org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher',
                    '-metadataRepository', repoDirUri,
                    '-artifactRepository', repoDirUri,
                    '-source', config.targetRepository,
                    '-compress'
        }
    }
}
