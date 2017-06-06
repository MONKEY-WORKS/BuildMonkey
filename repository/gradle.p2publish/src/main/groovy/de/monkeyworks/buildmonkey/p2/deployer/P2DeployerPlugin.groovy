/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.p2.deployer

import de.monkeyworks.buildmonkey.p2.deployer.util.FeatureHelper
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
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
        if (project.extensions.findByName(extensionName) == null) {
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
            if (!childProject.name)
                return

            List<Jar> jarTasks = childProject.tasks.withType(Jar).toList()
            if (jarTasks.size() > 0) {
                sourceTasks.addAll(jarTasks.collect { if (it && it.classifier == "sources") it })
                bundleTasks.addAll(jarTasks.collect { if (it && it.classifier != "sources") it })

                Collection<Task> t = createCopyArtefactTask(jarTasks)
                copyArtefactsTasks.addAll(t)
            }
        }

        sourceTasks.removeIf { it -> it == null }
        bundleTasks.removeIf { it -> it == null }

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
            if (sourceDir == null) {

                if (config.generateFeature) {
                    def version = config.version
                    println(version)
                    if (!version || version == "unspecified")
                        version = "0.0.0"

                    if (config.qualifier) {
                        version += config.qualifier
                    }

                    // create feature
                    def featureJar = config.targetRepository.toPath().resolve("features/${config.featureId}_${version}.jar")
                    FeatureHelper.createJar(config.featureId,
                            config.featureLabel,
                            version, "provider",
                            false,
                            bundleTasks,
                            featureJar.toFile())

                    if (sourceTasks.size() > 0) {
                        featureJar = config.targetRepository.toPath().resolve("features/${config.featureId}.source_${version}.jar")
                        FeatureHelper.createJar(config.featureId,
                                config.featureLabel + " - Sources",
                                version, "provider",
                                true,
                                sourceTasks,
                                featureJar.toFile())
                    }
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
            def isFeature = new File(task.project.getProjectDir(), "feature.xml").exists()

            Task newTask
            if (isFeature) {
                newTask = createFeatureTask(config, task)
            } else {
                newTask = createBundleTask(config, task)
            }

            createdTasks.add(newTask)

        }

        return createdTasks
    }

    Task createBundleTask(P2DeploymentExtension config, Jar task) {

        def classifier = task.classifier.contains("source") ? ".source" : ""

        Jar deployTask = task.project.tasks.create("p2" + task.name.capitalize(), Jar)
        deployTask.configure {
            with task.rootSpec
        }
        deployTask.dependsOn(task)

        deployTask.doFirst {

            def pluginsDir = new File(config.targetRepository, "plugins")
            if (!pluginsDir.exists())
                pluginsDir.mkdirs()

            String finalVersion = config.version
            finalVersion = finalVersion.replaceAll(".qualifier", "")

            if(!finalVersion || finalVersion == "unspecified") {
                finalVersion = task.manifest.effectiveManifest.attributes.get('Bundle-Version')
            }
            if (config.qualifier ) {
                finalVersion += config.qualifier
            }

            manifest {
                attributes(task.manifest.effectiveManifest.getAttributes())
                getAttributes().put('Bundle-Version', finalVersion)
            }
            destinationDir = pluginsDir
            archiveName = "${task.project.name}${classifier}_${finalVersion}.jar"
        }
        return deployTask
    }

    Task createFeatureTask(P2DeploymentExtension config, Jar task) {

        task.project.tasks.create("cleanCreateP2FeatureXML", Delete) {
            delete "${task.project.buildDir}/p2-tmp/feature.xml"
        }

        String finalVersion

        def parsedXML
        def featurexmlStream

        Task p2FeatureCreation = task.project.tasks.create("createP2FeatureXML") {
            doFirst {

                finalVersion = config.version

                if (!task.archivePath.exists()) {
                    featurexmlStream = new File(task.project.getProjectDir(), "feature.xml").newInputStream()
                } else {
                    JarFile jarFile = new JarFile(task.archivePath)
                    featurexmlStream = jarFile.getInputStream(jarFile.getEntry("feature.xml"))
                }

                parsedXML = new XmlSlurper().parse(featurexmlStream)
                featurexmlStream.close()

                if(!finalVersion || finalVersion == "unspecified") {
                    finalVersion = parsedXML['@version'].toString()
                }

                finalVersion = finalVersion.replace(".qualifier", "")
            }
            doLast {
                File tmpDir = new File(task.project.buildDir, "p2-tmp")
                if(!tmpDir.exists())
                    tmpDir.mkdirs()

                File modifiedFile = new File(tmpDir, "feature.xml")
                if (modifiedFile.exists())
                    modifiedFile.delete()

                if (config.qualifier ) {
                    finalVersion += config.qualifier
                }

                parsedXML['@version'] = finalVersion

                def writer = new FileWriter(modifiedFile)
                writer.withWriter { outWriter ->
                    XmlUtil.serialize(new StreamingMarkupBuilder().bind { mkp.yield parsedXML }, outWriter)
                }
                writer.close()

            }
        }

        Jar deployTask = task.project.tasks.create("p2" + task.name.capitalize(), Jar)
        deployTask.dependsOn(task)
        deployTask.dependsOn(p2FeatureCreation)

        deployTask.doFirst {
            from "${task.project.buildDir}/p2-tmp/feature.xml"

            def featureDir = new File(config.targetRepository, "features")
            if (!featureDir.exists())
                featureDir.mkdirs()

            destinationDir = featureDir
            archiveName = "${task.project.name}_${finalVersion}.jar"
        }
        return deployTask
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
