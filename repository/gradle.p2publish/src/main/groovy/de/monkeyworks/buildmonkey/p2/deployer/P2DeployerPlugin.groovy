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
import org.gradle.api.tasks.Delete
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

            project.p2Deployment.qualifier = ".v" + new Date().format('yyyyMMddHHmm')
        }

        // create bundle task
        def bundleTask = project.task(taskName)


        List<Task> copyArtefactsTasks = new ArrayList<Task>()

        Set<Jar> sourceTasks = new HashSet<>()
        Set<Jar> bundleTasks = new HashSet<>()

        def cleanTask = project.tasks.create("cleanP2", Delete)
        cleanTask.doFirst {
            delete project.p2Deployment.targetRepository
        }


        project.subprojects.forEach { childProject ->
            if(!childProject.name)
                return

            List<Jar> jarTasks = childProject.tasks.withType(Jar).toList()
            if (jarTasks.size() > 0) {
                sourceTasks.addAll(jarTasks.collect { if(it && it.classifier == "sources") it })
                bundleTasks.addAll(jarTasks.collect { if(it && it.classifier != "sources") it })

                Collection<Task> t = createCopyArtefactTask(jarTasks)
                copyArtefactsTasks.addAll(t)
            }
        }

        sourceTasks.removeIf { it -> it == null}
        bundleTasks.removeIf { it -> it == null}

        copyArtefactsTasks.forEach {
            it.dependsOn cleanTask
        }

        if (copyArtefactsTasks.size() > 0) {
            bundleTask.dependsOn(copyArtefactsTasks.toArray())
        }

        bundleTask.doLast {

            P2DeploymentExtension config = project.p2Deployment

            def eclipseHome = config.project.eclipseConfiguration
            def repoDirUri = config.targetRepository.toURI()
            def sourceDir = config.sourceRepository

            // deploy source repository
            if(sourceDir == null) {

                if (config.generateFeature) {
                    if(!config.version || config.version == "unspecified")
                        config.version = "0.0.0"
                    // create feature
                    def featureJar = config.targetRepository.toPath().resolve("features/${config.featureId}_${config.version}.jar")
                    FeatureHelper.createJar(config.featureId,
                            config.featureLabel,
                            config.version, "provider",
                            false,
                            bundleTasks,
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

    private Set<Task> createCopyArtefactTask(List<Jar> jarTasks) {

        P2DeploymentExtension config = project.p2Deployment


        Set<Task> createdTasks = new HashSet<>()

        jarTasks.forEach { task ->
            def sourceBundle = task.classifier == "sources"

            println(task.project.name)
            def isFeature = new File(task.project.getProjectDir(), "feature.xml").exists()

            def mversion
            if(isFeature) {
                def featurexmlStream

                if(!task.archivePath.exists()) {
                    featurexmlStream = new File(task.project.getProjectDir(), "feature.xml").newInputStream()
                } else {
                    JarFile jarFile = new JarFile(task.archivePath)
                    featurexmlStream = jarFile.getInputStream(jarFile.getEntry("feature.xml"))
                }

                def parsedXML = new XmlSlurper().parse(featurexmlStream)
                mversion = parsedXML['@version'].toString()
                featurexmlStream.close()
            } else {
                mversion = task.manifest.effectiveManifest.attributes.get('Bundle-Version')
            }

            def classifier = sourceBundle ? ".sources" : ""

            if(mversion.endsWith(".qualifier")) {
                mversion = mversion.replace(".qualifier", "")
            }

            Task deployTask = task.project.tasks.create("p2" + task.name.capitalize(), Jar)
            deployTask.dependsOn(task)

            deployTask.configure {
                with task.rootSpec
            }

            deployTask.doFirst {

                def pluginsDir = new File(config.targetRepository, "plugins")
                def featureDir = new File(config.targetRepository, "features")
                def destination = isFeature ? featureDir : pluginsDir

                if(!pluginsDir.exists())
                    pluginsDir.mkdirs()

                if(!featureDir.exists())
                    featureDir.mkdirs()



                if(config.generateQualifier) {
                    mversion += config.qualifier
                }

                manifest {
                    attributes(task.manifest.effectiveManifest.getAttributes())
                    getAttributes().put('Bundle-Version', mversion)
                }
                destinationDir = destination
                archiveName = "${task.project.name}${classifier}_${mversion}.jar"
            }

            createdTasks.add(deployTask)

        }

        return createdTasks
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
