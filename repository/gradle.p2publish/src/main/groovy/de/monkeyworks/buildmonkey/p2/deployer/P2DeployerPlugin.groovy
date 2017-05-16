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

import java.util.jar.JarFile

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

            def eclipseHome = config.project.eclipseConfiguration
            def repoDirUri = config.targetRepository.toURI()
            def sourceDir = config.sourceRepository

            // deploy source repository
            if(sourceDir == null) {
                def tasks = doCopyArtifacts(config)

                def jarTasks = tasks[0]
                def sourceTasks = tasks[1]

                if (config.generateFeature) {
                    // create feature
                    def featureJar = config.targetRepository.toPath().resolve("features/${config.featureId}_${config.version}.jar")
                    FeatureHelper.createJar(config.featureId,
                            config.featureLabel,
                            config.version, "provider",
                            false,
                            jarTasks,
                            featureJar.toFile())

                    featureJar = config.targetRepository.toPath().resolve("features/${config.featureId}.source_${config.version}.jar")
                    FeatureHelper.createJar(config.featureId,
                            config.featureLabel,
                            config.version, "provider",
                            true,
                            sourceTasks,
                            featureJar.toFile())
                }

                doBuildP2Repository(eclipseHome, config.targetRepository.getAbsolutePath(), repoDirUri, false)
            } else {
                doBuildP2Repository(eclipseHome, sourceDir, repoDirUri, true)
            }
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
        Set<Jar> sourceTasks = new HashSet<>()

        Set<Jar>[] tasks = new Set<Jar>[2]
        tasks[0] = jarTasks
        tasks[1] = sourceTasks

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
                            // collect source artifact for feature generation
                            sourceTasks.add(it)
                        }
                    } else {
                        // collect artifact for feature generation
                        jarTasks.add(it)
                    }

                    def mversion = "${it.version}"
                    // check if feature or plugin
                    def targetDir = pluginsDir
                    if(it.baseName.endsWith("feature")) {
                        targetDir = featureDir

                        def jarFile = new JarFile(it.archivePath)
                        def featurexmlStream = jarFile.getInputStream(jarFile.getJarEntry("feature.xml"))

                        def parsedXML = new XmlSlurper().parse(featurexmlStream)
                        mversion = parsedXML['@version'].toString()
                        featurexmlStream.close()

                    } else {
                        mversion = it.manifest.effectiveManifest.attributes.get('Bundle-Version')
                    }


                    // setup new file name
                    def targetFileName = "${it.baseName}${classifier}_${mversion}.${it.extension}"


                    // copy file
                    Files.copy(file.toPath(), targetDir.toPath().resolve(targetFileName), StandardCopyOption.REPLACE_EXISTING)

                }
            }
        }
        return tasks
    }

    private void doBuildP2Repository(EclipseConfiguration eclipseHome, String sourceDir, URI targetURI, boolean publishArtifacts) {
        project.exec {
            commandLine 'java', '-jar', eclipseHome.getLauncherPath().toFile(),
                    '-application', 'org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher',
                    '-metadataRepository', targetURI,
                    '-artifactRepository', targetURI,
                    '-source', sourceDir,
                    '-compress',
                    publishArtifacts ? '-publishArtifacts' : ''
        }
    }
}
