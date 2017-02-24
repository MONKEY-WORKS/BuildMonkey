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

/**
 * Gradle plugin for the root project of the Eclipse plugin build.
 * <p/>
 * Applying this plugin offers a DSL to specify Eclipse target platforms which will be the base
 * of the compilation of the sub-projects applying applying the following plug-ins:
 * {@link BundlePlugin}, {@link TestBundlePlugin}, {@link FeaturePlugin}, {@link UpdateSitePlugin}.
 * <p/>
 * A target platform references a standard target definition file to define the composing features.
 * When building, the platform is assembled by using the P2 director application.
 * <p/>
 * A valid target platform definition DSL looks like this:
 * <pre>
 * PluginTestBuild {
 *     defaultEclipseVersion = '44'
 *
 *     targetPlatform {
 *         eclipseVersion = '44'
 *         targetDefinition = file('tooling-e44.target')
 *         versionMapping = [
 *             'org.eclipse.core.runtime' : '3.10.0.v20140318-2214'
 *         ]
 *     }
 *
 *     targetPlatform {
 *        eclipseVersion = '43'
 *        ...
 *     }
 * }
 * </pre>
 * If no target platform version is defined for the build then the one matches to the value of the
 * {@link defaultEclipseVersion} attribute will be selected. This can be changed by appending the
 * the {@code -Peclipse.version=[version-number]} argument to he build. In the context of the
 * example above it would be:
 * <pre>
 * gradle clean build -Peclipse.version=43
 * </pre>
 * The directory layout where the target platform and it's mavenized counterpart stored is defined
 * in the {@link Config} class. The directory containing the target platforms can be redefined with
 * the {@code -PtargetPlatformsDir=<path>} argument.
 * <p/>
 * The {@code versionMapping} can be used to define exact plugin dependency versions per target platform.
 * A bundle can define a dependency through the {@code withEclipseBundle()} method like
 * <pre>
 * compile withEclipseBundle('org.eclipse.core.runtime')
 * </pre>
 * If the active target platform has a version mapped for the dependency then that version is used,
 * otherwise an unbound version range (+) is applied.
 */
class TestDefinitionPlugin implements Plugin<Project> {

    /**
     *  Extension class providing top-level content of the DSL definition for the plug-in.
     */
    static class PluginTestBuild {

        def defaultEclipseVersion
        final def targetPlatforms
        def eclipseSdkURL
        def eclipseVersion
        def launcherVersion

        PluginTestBuild() {
            targetPlatforms = [:]
            eclipseSdkURL = 'http://ftp-stud.hs-esslingen.de/Mirrors/eclipse/eclipse/downloads/drops4/R-4.6.1-201609071200'
            eclipseVersion = '4.6.1'
            launcherVersion = '1.3.200.v20160318-1642'
        }

        def targetPlatform(Closure closure) {
            def tp = new TargetPlatform()
            tp.apply(closure)
            targetPlatforms[tp.eclipseVersion] = tp
        }
    }

    /**
     * POJO class describing one target platform. Instances are stored in the {@link PluginTestBuild#targetPlatforms} map.
     */
    static class TargetPlatform {

        def eclipseVersion
        def targetDefinition
        def versionMapping

        TargetPlatform() {
            this.versionMapping = [:]
        }

        def apply (Closure closure) {
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            closure.delegate = this
            closure.call()
            // convert GStrings to Strings in the versionMapping key to avoid lookup misses
            versionMapping = versionMapping.collectEntries { k, v -> [k.toString(), v]}
        }
    }

    // name of the root node in the DSL
    static String DSL_EXTENSION_NAME = "pluginTestBuild"

    // task names
    static final String TASK_NAME_DOWNLOAD_ECLIPSE_SDK = "downloadEclipseSdk"
    static final String TASK_NAME_PREPARE_TARGET_PLATFORM = "prepareTargetPlatform"
    static final String TASK_NAME_INSTALL_TARGET_PLATFORM = "installTargetPlatform"
    static final String TASK_NAME_UNINSTALL_TARGET_PLATFORM = "uninstallTargetPlatform"
    static final String TASK_NAME_UNINSTALL_ALL_TARGET_PLATFORMS = "uninstallAllTargetPlatforms"

    @Override
    public void apply(Project project) {
        configureProject(project)

        Config config = Config.on(project)
        validateDslBeforeBuildStarts(project, config)
        addTaskDownloadEclipseSdk(project, config)
        addTaskAssembleTargetPlatform(project, config)
        addTaskInstallTargetPlatform(project, config)
        addTaskUninstallTargetPlatform(project, config)
        addTaskUninstallAllTargetPlatforms(project, config)
    }

    static void configureProject(Project project) {
        // add extension
        project.extensions.create(DSL_EXTENSION_NAME, PluginTestBuild)

        // expose some constants to the build files, e.g. for platform-dependent dependencies
        Config.exposePublicConstantsFor(project)

        // make the withEclipseBundle(String) method available in the build script
        project.ext.withEclipseBundle = { String pluginName -> DependencyUtils.calculatePluginDependency(project, pluginName) }
    }

    static void validateDslBeforeBuildStarts(Project project, Config config) {
        // check if the build definition is valid just before the build starts
        project.gradle.taskGraph.whenReady {
            if (project.pluginTestBuild.defaultEclipseVersion == null) {
                throw new RuntimeException("$DSL_EXTENSION_NAME must specify 'defaultEclipseVersion'.")
            }

            // check if the selected target platform exists for the given Eclipse version
            def targetPlatform = config.targetPlatform
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
            downloadUrl = Config.getEclipseSdkDownloadUrl(project)
            targetDir = config.eclipseSdkDir
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
            project.afterEvaluate { inputs.file config.targetPlatform.targetDefinition }
            project.afterEvaluate { outputs.dir config.nonMavenizedTargetPlatformDir }
            doLast { prepareTargetPlatform(project, config) }
        }
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
        def rootNode = new XmlSlurper().parseText(config.targetPlatform.targetDefinition.text)
        rootNode.locations.location.each { location ->
            updateSites.add(location.repository.@location.text().replace('\${project_loc}', 'file://' +  project.projectDir.absolutePath))
            location.unit.each {unit -> features.add("${unit.@id}/${unit.@version}") }
        }

        // invoke the P2 director application to assemble install all features from the target
        // definition file to the target platform: http://help.eclipse.org/luna/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Fguide%2Fp2_director.html
        def launcherVersion = project.pluginTestBuild.launcherVersion
        project.logger.info("Assemble target platfrom in '${config.nonMavenizedTargetPlatformDir.absolutePath}'.\n    Update sites: '${updateSites.join(' ')}'\n    Features: '${features.join(' ')}'")
        project.exec {

            commandLine("java", 
                    '-cp', project.buildDir.toPath().resolve("eclipse/eclipse/plugins/org.eclipse.equinox.launcher_${launcherVersion}.jar").toFile(),
                    'org.eclipse.core.launcher.Main',
                    '-application', 'org.eclipse.equinox.p2.director',
                    '-repository', updateSites.join(','),
                    '-installIU', features.join(','),
                    '-tag', 'target-platform',
                    '-destination', config.nonMavenizedTargetPlatformDir.path,
                    '-profile', 'SDKProfile',
                    '-bundlepool', config.nonMavenizedTargetPlatformDir.path,
                    '-p2.os', Config.os,
                    '-p2.ws', Config.ws,
                    '-p2.arch', Config.arch,
                    '-roaming',
                    '-nosplash')
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

    static void addTaskUninstallTargetPlatform(Project project, Config config) {
        project.task(TASK_NAME_UNINSTALL_TARGET_PLATFORM) {
            group = Config.gradleTaskGroupName
            description = "Deletes the target platform."
            doLast { deleteFolder(project, config.targetPlatformDir) }
        }
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

    static void addTaskUninstallAllTargetPlatforms(Project project, Config config) {
        project.task(TASK_NAME_UNINSTALL_ALL_TARGET_PLATFORMS) {
            group = Config.gradleTaskGroupName
            description = "Deletes all target platforms from the current machine."
            doLast { deleteFolder(project, config.targetPlatformsDir) }
        }
    }

    // Duplicate from P2MirrorPlugin
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
}
