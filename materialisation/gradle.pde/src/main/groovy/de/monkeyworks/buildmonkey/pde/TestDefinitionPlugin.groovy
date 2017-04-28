/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package de.monkeyworks.buildmonkey.pde

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem

import de.monkeyworks.buildmonkey.pde.mavenize.BundleMavenDeployer
import de.monkeyworks.buildmonkey.pde.tools.DownloadEclipseSdkTask
import de.monkeyworks.buildmonkey.pde.utils.FileSemaphore


class BundlePattern {
    String projectPattern
    String projectGroup
}

/**
 *  Extension class providing top-level content of the DSL definition for the plug-in.
 */
class PluginTestBuild {

    def eclipseSdkUrl
    def eclipseVersion
    def launcherVersion

    def targetDefinition

    def mavenGroup
    def p2Group

    Closure dependencyClosure

    def project

    PluginTestBuild(Project project) {
        this.project = project
        eclipseSdkUrl = 'http://ftp-stud.hs-esslingen.de/Mirrors/eclipse/eclipse/downloads/drops4/R-4.6.1-201609071200'
        eclipseVersion = '4.6.1'
        launcherVersion = '1.3.200.v20160318-1642'
        this.mavenGroup = 'buildMonkey'
        this.p2Group = 'eclipse'
    }

    void dependencyHandling(Closure closure) {
        dependencyClosure = closure
    }

}



class TestDefinitionPlugin implements Plugin<Project> {

    // name of the root node in the DSL
    static String DSL_EXTENSION_NAME = "pluginTestBuild"

    // task names
    static final String TASK_NAME_DOWNLOAD_ECLIPSE_SDK = "downloadEclipse"
    static final String TASK_NAME_PREPARE_TARGET_PLATFORM = "prepareTargetPlatform"
    static final String TASK_NAME_INSTALL_TARGET_PLATFORM = "installTargetPlatform"
    static final String TASK_NAME_UNINSTALL_TARGET_PLATFORM = "uninstallTargetPlatform"

    @Override
    public void apply(Project project) {
        configureProject(project)

        Config config = Config.on(project)
        validateDslBeforeBuildStarts(project, config)
        addTaskDownloadEclipseSdk(project, config)
        addTaskAssembleTargetPlatform(project, config)
        addTaskInstallTargetPlatform(project, config)
        addTaskUninstallTargetPlatform(project, config)
    }

    static void configureProject(Project project) {
        // add extension
        project.extensions.create(DSL_EXTENSION_NAME, PluginTestBuild, project)

        // expose some constants to the build files, e.g. for platform-dependent dependencies
        Config.exposePublicConstantsFor(project)

        // make the withEclipseBundle(String) method available in the build script
        project.ext.withEclipseBundle = { String pluginName -> DependencyUtils.calculatePluginDependency(project, pluginName) }
    }

    static void validateDslBeforeBuildStarts(Project project, Config config) {
        // check if the build definition is valid just before the build starts
        project.gradle.taskGraph.whenReady {
            // check if the selected target platform exists for the given Eclipse version
            def targetPlatform = project.pluginTestBuild
            if (targetPlatform == null) {
                throw new RuntimeException("No target platform is defined for selected Eclipse version '${config.eclipseVersion}'.")
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

    static void addTaskDownloadEclipseSdk(Project project, Config config) {
        project.task(TASK_NAME_DOWNLOAD_ECLIPSE_SDK, type: DownloadEclipseSdkTask) {
            group = Config.gradleTaskGroupName
            description = "Downloads an Eclipse SDK to perform P2 operations with."
            downloadUrl = project.pluginTestBuild.getEclipseSdkUrl() + "/" + getEclipseSdkFileName(project, config)
            targetDir = project.buildDir.toPath().resolve("eclipse").toFile()
            def launcherVersion = project.pluginTestBuild.launcherVersion

            onlyIf { 
                return !project.buildDir
                    .toPath()
                    .resolve("eclipse/eclipse/plugins/org.eclipse.equinox.launcher_${launcherVersion}.jar")
                    .toFile().exists()
            }
        }
    }

    static void addTaskAssembleTargetPlatform(Project project, Config config) {
        project.task(TASK_NAME_PREPARE_TARGET_PLATFORM, dependsOn: [
            TASK_NAME_DOWNLOAD_ECLIPSE_SDK,
        ]) {
            group = Config.gradleTaskGroupName
            description = "Assembles an Eclipse distribution based on the target platform definition."
            project.afterEvaluate { inputs.file project.pluginTestBuild.targetDefinition }
            project.afterEvaluate { outputs.dir config.nonMavenizedTargetPlatformDir }
            doLast { prepareTargetPlatform(project, config) }
        }
    }

    static void addTaskInstallTargetPlatform(Project project, Config config) {
        project.task(TASK_NAME_INSTALL_TARGET_PLATFORM, dependsOn: TASK_NAME_PREPARE_TARGET_PLATFORM) {
            group = Config.gradleTaskGroupName
            description = "Converts the assembled Eclipse distribution to a Maven repoository."
            project.afterEvaluate { inputs.dir config.nonMavenizedTargetPlatformDir }
            project.afterEvaluate { outputs.dir config.mavenizedTargetPlatformDir }
            doLast { installTargetPlatform(project, config) }
        }
    }

    static void addTaskUninstallTargetPlatform(Project project, Config config) {
        project.task(TASK_NAME_UNINSTALL_TARGET_PLATFORM) {
            group = Config.gradleTaskGroupName
            description = "Deletes the target platform."
            doLast { deleteFolder(project, project.pluginTestBuildDir) }
        }
    }

    static String getEclipseSdkDownloadUrl(Project project)  {
        def mirror = project.pluginTestBuild
        def os = org.gradle.internal.os.OperatingSystem.current()
        def arch = System.getProperty("os.arch").contains("64") ? "-x86_64" : ""
        def eclipseUrl = mirror.eclipseSdkURL
        def eclipseVersion = mirror.eclipseVersion
        if (os.windows) {
            return "${eclipseUrl}/eclipse-SDK-${eclipseVersion}-win32${arch}.zip"
        } else if (os.macOsX) {
            return "${eclipseUrl}/eclipse-SDK-${eclipseVersion}-macosx-cocoa${arch}.tar.gz"
        } else if (os.linux) {
            return "${eclipseUrl}/eclipse-SDK-${eclipseVersion}-linux-gtk${arch}.tar.gz"
        }
        return ''
    }

    static String getEclipseSdkFileName(Project project, Config config)  {
        def os = org.gradle.internal.os.OperatingSystem.current()
        def arch = System.getProperty("os.arch").contains("64") ? "x86_64" : ""
        def version = project.pluginTestBuild.getEclipseVersion()
        if (os.windows) {
            return "eclipse-SDK-${version}-windows-${config.getWs()}-${arch}.zip"
        } else if (os.macOsX) {
            return "eclipse-SDK-${version}-macosx-${config.getWs()}-${arch}.tar.gz"
        } else if (os.linux) {
            return "eclipse-SDK-${version}-linux-${config.getWs()}-${arch}.tar.gz"
        }
        return ''
    }

    static void prepareTargetPlatform(Project project, Config config) {
        // if multiple builds start on the same machine (which is the case with a CI server)
        // we want to prevent them assembling the same target platform at the same time
        def lock = new FileSemaphore(config.nonMavenizedTargetPlatformDir)
        try {
            lock.lock()
            prepareTargetPlatformUnprotected(project, config)
        } finally  {
            lock.unlock()
        }
    }

    static void prepareTargetPlatformUnprotected(Project project, Config config) {
        File p2Folder = new File("${config.nonMavenizedTargetPlatformDir.path}/p2")
        if(p2Folder.exists()) {
            return
        }

        // collect  update sites and feature names
        def updateSites = []
        def features = []
        def rootNode = new XmlSlurper().parseText(project.pluginTestBuild.targetDefinition.text)
        rootNode.locations.location.each { location ->
            updateSites.add(location.repository.@location.text().replace('\${project_loc}', 'file://' +  project.projectDir.absolutePath))
            location.unit.each {unit ->
                features.add("${unit.@id}/${unit.@version}")
            }
        }

        // invoke the P2 director application to assemble install all features from the target
        // definition file to the target platform: http://help.eclipse.org/luna/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Fguide%2Fp2_director.html
        def launcherVersion = project.pluginTestBuild.launcherVersion
        project.logger.info("Assemble target platfrom in '${config.nonMavenizedTargetPlatformDir.absolutePath}'.\n    Update sites: '${updateSites.join(' ')}'\n    Features: '${features.join(' ')}'")
        println("Assemble target platfrom in '${config.nonMavenizedTargetPlatformDir.absolutePath}'.\n    Update sites: '${updateSites.join(' ')}'\n    Features: '${features.join(' ')}'")
        project.exec {

            commandLine("java",
                    '-cp', project.buildDir.toPath().resolve("eclipse/eclipse/plugins/org.eclipse.equinox.launcher_${launcherVersion}.jar").toFile(),
                    'org.eclipse.core.launcher.Main',
                    '-application', 'org.eclipse.equinox.p2.director',
                    '-repository', updateSites.join(','),
                    '-installIU', features.join(','),
                    '-tag', 'target-platform',
                    '-destination', config.nonMavenizedTargetPlatformDir.path,
                    '-profile', 'PluginProfile',
                    '-bundlepool', config.nonMavenizedTargetPlatformDir.path,
                    '-p2.os', Config.os,
                    '-p2.ws', Config.ws,
                    '-p2.arch', Config.arch,
                    '-roaming',
                    '-nosplash')
        }
    }

    static void installTargetPlatform(Project project, Config config) {
        // delete the mavenized target platform directory to ensure that the deployment doesn't
        // have outdated artifacts
        if (config.mavenizedTargetPlatformDir.exists()) {
            project.logger.info("Delete mavenized platform directory '${config.mavenizedTargetPlatformDir}'")
            config.mavenizedTargetPlatformDir.deleteDir()
        }

        // install bundles
        project.logger.info("Convert Eclipse target platform '${config.nonMavenizedTargetPlatformDir}' to Maven repository '${config.mavenizedTargetPlatformDir}'")
        def deployer = new BundleMavenDeployer(project.ant, Config.mavenizedEclipsePluginGroupName, project.logger)
        deployer.deploy(config.nonMavenizedTargetPlatformDir, config.mavenizedTargetPlatformDir)
    }

    static void deleteFolder(Project project, File folder) {
        if (!folder.exists()) {
            project.logger.info("'$folder' doesn't exist")
        }
        else {
            project.logger.info("Delete '$folder'")
            def success = folder.deleteDir()
            if (!success) {
                throw new RuntimeException("Failed to delete '$folder'")
            }
        }
    }
}
