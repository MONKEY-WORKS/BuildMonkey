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

import de.monkeyworks.buildmonkey.pde.mavenize.BundleMavenDeployer
import de.monkeyworks.buildmonkey.pde.tools.DownloadApplicationTask
import org.gradle.api.Plugin
import org.gradle.api.Project


/**
 *  Extension class providing top-level content of the DSL definition for the plug-in.
 */
class UITestBuild {

    def applicationUrl
    Closure applicationClosure

    def project

    UITestBuild(Project project) {
        this.project = project
    }

    void getApplicationName(Closure closure) {
        applicationClosure = closure
        println applicationClosure('linux')
    }
}


class UITestDefinitionPlugin implements Plugin<Project> {

    // name of the root node in the DSL
    static String DSL_EXTENSION_NAME = "uiTestBuild"

    // task names
    static final String TASK_NAME_DOWNLOAD_APPLICATION = "downloadApplication"
    static final String TASK_NAME_PREPARE_APPLICATION = "prepareApplication"
    static final String TASK_NAME_INSTALL_APPLICATION = "installApplication"
    static final String TASK_NAME_UNINSTALL_APPLICATION = "uninstallApplication"

    @Override
    public void apply(Project project) {
        configureProject(project)

        Config config = Config.on(project)
        validateDslBeforeBuildStarts(project, config)
        addTaskDownloadApplication(project, config)
        addTaskAssembleApplication(project, config)
        addTaskInstallApplication(project, config)
        addTaskUninstallApplication(project, config)
    }

    static void configureProject(Project project) {
        // add extension
        project.extensions.create(DSL_EXTENSION_NAME, UITestBuild, project)

        // expose some constants to the build files, e.g. for platform-dependent dependencies
        Config.exposePublicConstantsFor(project)

    }

    static void validateDslBeforeBuildStarts(Project project, Config config) {
        // check if the build definition is valid just before the build starts
        project.gradle.taskGraph.whenReady {
            // check if the selected target platform exists for the given Eclipse version
            def targetPlatform = project.uiTestBuild
            if (targetPlatform == null) {
                throw new RuntimeException("No test configuration is defined for selected bundle.")
            }

            // check if a target platform file is referenced
/*            def targetDefinition = targetPlatform.targetDefinition
            if (targetDefinition == null || !targetDefinition.exists()) {
                throw new RuntimeException("No target definition file found for '${targetDefinition}'.")
            }

            // check if target definition file is a valid XML
            try {
                new XmlSlurper().parseText(targetDefinition.text)
            } catch(Exception e) {
                throw new RuntimeException("Target definition file '$targetDefinition' must be a valid XML document.", e)
            }*/
        }
    }

    static void addTaskDownloadApplication(Project project, Config config) {
        project.task(TASK_NAME_DOWNLOAD_APPLICATION, type: DownloadApplicationTask) {
            doLast {
                group = Config.gradleTaskGroupName
                description = "Downloads the application to perform P2 operations with."
                downloadUrl = getApplicationDownloadUrl(project)
                targetDir = project.buildDir.toPath().resolve("application").toFile()
                def launcherVersion = findLauncherVersion(targetDir.toPath().resolve('plugins'))

                onlyIf {
                    return !project.buildDir
                            .toPath()
                            .resolve("application/plugins/org.eclipse.equinox.launcher_${launcherVersion}.jar")
                            .toFile().exists()
                }
            }
        }
    }

    static void addTaskAssembleApplication(Project project, Config config) {
        project.task(TASK_NAME_PREPARE_APPLICATION, dependsOn: [
            TASK_NAME_DOWNLOAD_APPLICATION,
        ]) {
            group = Config.gradleTaskGroupName
            description = "Assembles the application."
            //project.afterEvaluate { inputs.file project.pluginTestBuild.targetDefinition }
            project.afterEvaluate { outputs.dir config.nonMavenizedTargetPlatformDir }
            doLast { /*prepareApplication(project, config)*/ }
        }
    }

    static void addTaskInstallApplication(Project project, Config config) {
        project.task(TASK_NAME_INSTALL_APPLICATION, dependsOn: TASK_NAME_PREPARE_APPLICATION) {
            group = Config.gradleTaskGroupName
            description = "Converts the application to a Maven repoository."
            project.afterEvaluate { inputs.dir config.nonMavenizedTargetPlatformDir }
            project.afterEvaluate { outputs.dir config.mavenizedTargetPlatformDir }
        }
    }

    static void addTaskUninstallApplication(Project project, Config config) {
        project.task(TASK_NAME_UNINSTALL_APPLICATION) {
            group = Config.gradleTaskGroupName
            description = "Deletes the application."
            doLast { deleteFolder(project, project.buildDir.toPath().resolve("application").toFile()) }
        }
    }

    static String getApplicationDownloadUrl(Project project)  {
        def mirror = project.uiTestBuild
        String os = Config.getOs()
        String arch = System.getProperty("os.arch").contains("64") ? "x86_64" : ""
        def url = mirror.getApplicationUrl()
        println url
        def applicationName = project.uiTestBuild.applicationClosure(os)

        if (os == 'win32') {
            return "${url}/${applicationName}.zip"
        } else {
            return "${url}/${applicationName}.tar.gz"
        }
        return ''
    }

    static void installApplication(Project project, Config config) {
        // delete the mavenized target platform directory to ensure that the deployment doesn't
        // have outdated artifacts
        if (config.mavenizedTargetPlatformDir.exists()) {
            project.logger.info("Delete mavenized platform directory '${config.mavenizedTargetPlatformDir}'")
            config.mavenizedTargetPlatformDir.deleteDir()
        }

        // install bundles
        project.logger.info("Convert Eclipse target platform '${config.nonMavenizedTargetPlatformDir}' to Maven repository '${config.mavenizedTargetPlatformDir}'")
        def deployer = new BundleMavenDeployer(project.ant, project.pluginTestBuild.p2Group, project.logger)
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

    void findLauncherVersion(def targetDir) {

        def equinoxLaunchers = new FileNameFinder().getFileNames(targetDir.toAbsolutePath().toFile().toString(), 'org.eclipse.equinox.launcher_*.jar')
        assert equinoxLaunchers.size() > 0

        String tmp = equinoxLaunchers.get(0)
        tmp = tmp.substring(tmp.indexOf("org.eclipse.equinox.launcher_") + ("org.eclipse.equinox.launcher_").length())
        tmp = tmp.substring(0, tmp.indexOf(".jar"))

        launcherVersion = tmp
    }
}
