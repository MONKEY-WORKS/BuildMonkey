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

import de.monkeyworks.buildmonkey.eclipsesdk.DownloadHelper
import de.monkeyworks.buildmonkey.eclipsesdk.EclipseConfiguration

/**
 * Gradle plugin to bundle all build artifacts into one p2 repository
 *
 * Created by Johannes Tandler on 27.01.17.
 */
public class P2DeployerPlugin implements Plugin<Project> {

    private final String extensionName = "p2Deployment"

    private final String taskName = "publishP2"

    private Project project

    @Override
    void apply(Project project) {
        this.project = project

        DownloadHelper.addEclipseConfigurationExtension(project)
        DownloadHelper.addTaskDownloadEclipseSdk(project)

        // create project extension if it doesn't exist yet
        if(project.extensions.findByName(extensionName) == null) {
            project.extensions.create(extensionName, P2DeploymentExtension, project)
        }

        // create bundle task
        project.task(taskName, dependsOn: project.subprojects.jar).doFirst {

            P2DeploymentExtension config = project.p2Deployment

            def jarTasks = doCopyArtifacts(config)

            // create feature
            def featureJar = config.targetRepository.toPath().resolve("features/${config.featureId}_${config.version}.jar")
            FeatureHelper.createJar(config.featureId, 
                config.featureLabel, 
                config.version, "provider", 
                jarTasks, 
                featureJar.toFile())

            
            doBuildP2Repository(config)
        }
    }

    private Set<Jar> doCopyArtifacts(P2DeploymentExtension config) {
        def pluginsDir = new File(config.targetRepository, "plugins")
        def featureDir = new File(config.targetRepository, "features")

        if(!pluginsDir.exists())
            pluginsDir.mkdirs()

        if(!featureDir.exists())
            featureDir.mkdirs()

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
                    
                    // check if feature or plugin
                    def targetDir = pluginsDir
                    println(it.baseName)
                    if(it.baseName.endsWith("feature")) {
                        targetDir = featureDir
                    }

                    // copy file
                    Files.copy(file.toPath(), targetDir.toPath().resolve(targetFileName), StandardCopyOption.REPLACE_EXISTING)

                    // collect artifact for feature generation
                    jarTasks.add(it)
                }
            }
        }
        return jarTasks
    }


    private void doBuildP2Repository(P2DeploymentExtension config) {
        def eclipseHome = config.project.eclipseConfiguration
        def repoDirUri = config.targetRepository.toURI()

        project.exec {
            commandLine 'java', '-jar', eclipseHome.getLauncherPath().toFile(),
                    '-application', 'org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher',
                    '-metadataRepository', repoDirUri,
                    '-artifactRepository', repoDirUri,
                    '-source', config.targetRepository,
                    '-compress'
        }
    }
}
