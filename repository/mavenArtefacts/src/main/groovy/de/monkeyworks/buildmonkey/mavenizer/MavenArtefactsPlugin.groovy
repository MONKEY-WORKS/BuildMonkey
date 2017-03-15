/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.mavenizer

import org.gradle.api.GradleScriptException
import org.gradle.api.Plugin
import org.gradle.api.Project

import de.monkeyworks.buildmonkey.eclipsesdk.DownloadHelper
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.plugins.PluginInstantiationException


class MavenArtefactsPlugin implements Plugin<Project> {

	static final String TASK_NAME_CONVERT_MAVEN_OSGI = "convertMavenArtefacts"
	// Must be a common constant
	//static final String TASK_NAME_MIRROR_P2 = "mirrorP2Repository"

	@Override
    void apply(Project project) {

        DownloadHelper.addEclipseConfigurationExtension(project)
        DownloadHelper.addTaskDownloadEclipseSdk(project)

        project.extensions.create("convertMaven", MavenArtefactsExtension)

        def rootTask = project.tasks.create(TASK_NAME_CONVERT_MAVEN_OSGI)
        rootTask.dependsOn(DownloadHelper.TASK_NAME_DOWNLOAD_ECLIPSE_SDK)

        checkPlugins(project)

        project.pluginManager.apply('org.standardout.bnd-platform')
        project.pluginManager.apply('de.monkeyworks.buildmonkey.ArtifactoryUploadPlugin')
        project.pluginManager.apply('de.monkeyworks.buildmonkey.mavenizer.MavenizerPlugin')

        // Create task mavenizing the p2 repository
        createMavenTask(project)
        // Create the two uploads configurations
        createUploads(project)

        project.afterEvaluate {
            MavenArtefactsExtension config = project.convertMaven
            // create clan task if necessary
            if (project.tasks.findByPath('clean') == null) {project.tasks.create('clean')}

            LinkedHashMap<String, ResolvedDependency> flattenedDeps = prepareDependencies(project, config)

            configureBndTools(flattenedDeps, project, config)
            configureMavenize(project, config)
            configureUpload(project, config)

            project.updateSite.dependsOn('downloadEclipseSdk')
            project.artifactoryUpload.dependsOn('updateSite')
            project.mavenizeP2Repository.dependsOn('updateSite')
            project.mavenizeP2Repository_1.dependsOn('updateSite')
            project.artifactoryUpload.dependsOn('mavenizeP2Repository')
            project.artifactoryUpload_0.dependsOn('updateSite')
            project.artifactoryUpload_1.dependsOn('mavenizeP2Repository')

            rootTask.dependsOn('downloadEclipseSdk')
            rootTask.dependsOn('updateSite')
            rootTask.dependsOn('mavenizeP2Repository')
            rootTask.dependsOn('artifactoryUpload')
            rootTask.dependsOn('artifactoryUpload_0')
            rootTask.dependsOn('artifactoryUpload_1')

        }
    }

    private void checkPlugins(Project project) {
        int canProceed = 0

        project.buildscript.configurations.classpath.each {
            if (it.getName().matches(/bnd-platform-.+jar/)) {
                canProceed++
            } else if (it.getName().matches(/gradle\.mavenize-.+jar/)) {
                canProceed++
            } else if (it.getName().matches(/artifactoryupload-.+jar/)) {
                canProceed++
            }
        }

        if (canProceed < 3) {
            throw new PluginInstantiationException('''\
Missing depending plugins! Include following line into the build.gradle file:
buildscript {
  dependencies {
    classpath 'org.standardout:bnd-platform:+'
    classpath 'de.monkeyworks.buildmonkey:gradle.mavenize:+'
    classpath 'de.monkeyworks.buildmonkey:artifactoryupload:+'
  }
} ''')
        }
    }

    private LinkedHashMap<String, ResolvedDependency> prepareDependencies(project, config) {
        project.configurations.each {
            it.exclude(['group': 'junit'])
        }

        def jars = config.artefacts
        jars.each { e ->
            def dep = project.dependencies.create(e.value as String)
            project.configurations.platform.dependencies.add(dep)
        }

        Map<String, ResolvedDependency> flattenedDeps = [:]

        project.configurations.platform.getResolvedConfiguration().getFirstLevelModuleDependencies().each { dep ->
            def addChildDeps
            addChildDeps = { d ->
                if (!flattenedDeps[d.getName()]) {
                    flattenedDeps[d.getName()] = d
                }

                d.getChildren().each { c ->
                    addChildDeps(c)
                }
            }

            addChildDeps(dep)
        }
        flattenedDeps
    }

    private void configureBndTools(LinkedHashMap<String, ResolvedDependency> flattenedDeps, project,  config) {
        def extension = project.getExtensions().getByName("platform")

        flattenedDeps.each { name, dep ->
            extension.bnd(name) {
                setSymbolicName("${config.prefix}." + (dep.getModuleName().toString() as String))
            }
        }
        // Manipulate mockito-core
        extension.bnd('org.mockito:mockito-core:1.10.19') {
            // hamcrest.core must be required ...
            instruction 'Require-Bundle', 'org.hamcrest.core'
            // ... and removed from Import-Package instruction
            def newValue = ''
            properties.each { key, value ->
                if (key.equals('Import-Package')) {
                    // Look for org.hamcrest
                    int index = value.indexOf('org.hamcrest')
                    if (index > 0) {
                        // Store all content before package
                        newValue = value.substring(0, index)
                        // Retrieve all content behind
                        def remainder = value.substring(index + 13)
                        // if org.hamcrest has a version constraint
                        if (remainder.startsWith('version')) {
                            // Trim content behind package to everthing behind closing double quote
                            index = remainder.indexOf('"', 9)
                            remainder = remainder.substring(index + 2)
                        }
                        newValue += remainder
                    }
                }
            }
            // If a new Import declaration was created, replace the old one
            if (newValue.length() > 0) {
                properties.put('Import-Package', newValue)
            }
        }

        extension.useFeatureHashQualifiers = false
        extension.addBndPlatformManifestHeaders = true
        extension.useBndHashQualifiers = false
        extension.defaultQualifier = ""
        extension.featureId = "${config.featureId}"
        extension.featureName = "${config.featureName}"
        extension.featureVersion = "${config.featureVersion}"
        extension.updateSiteDir = new File(config.updateSiteDir)

        def eclipseConf = project.getExtensions().getByName("eclipseConfiguration")
        extension.eclipseHome = new File(eclipseConf.localEclipseDir)
    }

    private void createMavenTask(project) {
        def extension = project.getExtensions().getByName("mavenize")

        extension.mavenizeTask{
            setSourceP2Repository('')
            setTargetDir('')
            setGroupId('')
        }
    }

    private void configureMavenize(project, config) {
        def extension = project.getExtensions().getByName("mavenize")

        extension.mavenizeTasks[0].setSourceP2Repository config.updateSiteDir
        extension.mavenizeTasks[0].setTargetDir config.targetDir
        extension.mavenizeTasks[0].setGroupId config.prefix
    }

    private void createUploads(project) {
        def extension = project.getExtensions().getByName("artifactoryUploads")
        extension.artifactoryUpload {
            sourceDirectory = ""
            artifactoryURL = ""
            repositoryPath = ""
            credentials {
                username = ""
                password = ""
            }
        }

        extension.artifactoryUpload {
            sourceDirectory = ""
            artifactoryURL = ""
            repositoryPath = ""
            credentials {
                username = ""
                password = ""
            }
        }
    }

    private void configureUpload(project, config) {
        def extension = project.getExtensions().getByName("artifactoryUploads")

        def sourceFolder = config.updateSiteDir
        def url = config.repositoryServerURL == null ? config.p2RepositoryServerURL : config.repositoryServerURL
        def name = config.p2RepositoryName
        def user = config.repositoryUser == null ? config.p2RepositoryUser : config.repositoryUser
        def pwd = config.repositoryPassword == null ? config.p2RepositoryPassword : config.repositoryPassword

        def upload = extension.uploads[0]
        upload.setSourceDirectory(sourceFolder)
        upload.setArtifactoryURL(url)
        upload.setRepositoryPath(name)
        upload.credentials['username'] = user
        upload.credentials['password'] = pwd
        upload.threads = config.uploadThreads

        sourceFolder = config.targetDir
        url = config.repositoryServerURL == null ? config.mavenRepositoryServerURL : config.repositoryServerURL
        name = config.mavenRepositoryName
        user = config.repositoryUser == null ? config.mavenRepositoryUser : config.repositoryUser
        pwd = config.repositoryPassword == null ? config.mavenRepositoryPassword : config.repositoryPassword

        upload = extension.uploads[1]
        upload.setSourceDirectory(sourceFolder)
        upload.setArtifactoryURL(url)
        upload.setRepositoryPath(name)
        upload.credentials['username'] = user
        upload.credentials['password'] = pwd
        upload.threads = config.uploadThreads
    }
}