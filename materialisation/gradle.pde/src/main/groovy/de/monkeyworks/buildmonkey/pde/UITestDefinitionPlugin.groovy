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

import de.monkeyworks.buildmonkey.pde.tools.DownloadApplicationTask
import de.monkeyworks.buildmonkey.pde.tools.DownloadEclipseSdkTask
import de.monkeyworks.buildmonkey.pde.tools.FileHelper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem


/**
 *  Extension class providing top-level content of the DSL definition for the plug-in.
 */
class UITestBuild {

    def eclipseSdkUrl
    def eclipseVersion

    def applicationUrl
    def applicationName
    def swtbotRepository

    def project
    def targetDefinition

    UITestBuild(Project project) {
        this.project = project

        eclipseSdkUrl = 'http://ftp-stud.hs-esslingen.de/Mirrors/eclipse/eclipse/downloads/drops4/R-4.6.1-201609071200'
        eclipseVersion = '4.6.1'


    }

}


class UITestDefinitionPlugin implements Plugin<Project> {

    // name of the root node in the DSL
    static String DSL_EXTENSION_NAME = "uiTestBuild"

    // task names
    static final String TASK_NAME_DOWNLOAD_ECLIPSE = "downloadEclipseForUI"
    static final String TASK_NAME_DOWNLOAD_APPLICATION = "downloadApplication"
    static final String TASK_NAME_INSTALL_TARGET_PLATFORM = "installUITargetPlatform"
    static final String TASK_NAME_UNINSTALL_APPLICATION = "uninstallApplication"

    @Override
    public void apply(Project project) {
        configureProject(project)

        Config config = Config.on(project)
        validateDslBeforeBuildStarts(project, config)
        addTaskDownloadEclipse(project, config)
        addTaskDownloadApplication(project, config)
        addTaskInstallSWTBot(project, config)
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
        }
    }

    static void addTaskDownloadEclipse(Project project, Config config) {
        project.task(TASK_NAME_DOWNLOAD_ECLIPSE, type: DownloadEclipseSdkTask) {
            group = Config.gradleTaskGroupName
            description = "Downloads an Eclipse SDK to perform P2 operations with."
            downloadUrl = project.uiTestBuild.getEclipseSdkUrl() + "/" + getEclipseSdkFileName(project, config)
            targetDir = project.buildDir.toPath().resolve("eclipse").toFile()
            onlyIf {
                def targetFile = eclipseSdkArchive(targetDir)
                return !targetFile.exists()
            }
        }
    }

    static String getEclipseSdkFileName(Project project, Config config)  {
        def os = org.gradle.internal.os.OperatingSystem.current()
        def arch = System.getProperty("os.arch").contains("64") ? "x86_64" : ""
        def version = project.uiTestBuild.getEclipseVersion()
        if (os.windows) {
            return "eclipse-SDK-${version}-${config.getWs()}-${arch}.zip"
        } else if (os.macOsX) {
            return "eclipse-SDK-${version}-macosx-${config.getWs()}-${arch}.tar.gz"
        } else if (os.linux) {
            return "eclipse-SDK-${version}-linux-${config.getWs()}-${arch}.tar.gz"
        }
        return ''
    }

    static File eclipseSdkArchive(def targetDir) {
        new File(targetDir, OperatingSystem.current().isWindows() ? 'eclipse-sdk.zip' : 'eclipse-sdk.tar.gz')
    }


    static void addTaskDownloadApplication(Project project, Config config) {
        project.task(TASK_NAME_DOWNLOAD_APPLICATION, type: DownloadApplicationTask, dependsOn: TASK_NAME_DOWNLOAD_ECLIPSE) {
            targetDir = project.buildDir.toPath().resolve("application").toFile()
            project.gradle.taskGraph.whenReady {
                targetFile = new File(targetDir, project.uiTestBuild.getApplicationName())
                downloadUrl = getApplicationDownloadUrl(project)
            }
            group = Config.gradleTaskGroupName
            description = "Downloads the application to perform P2 operations with."


            onlyIf {
                return !targetFile.exists()
            }
        }
    }

    static void addTaskInstallSWTBot(Project project, Config config) {
        project.task(TASK_NAME_INSTALL_TARGET_PLATFORM, dependsOn: TASK_NAME_DOWNLOAD_APPLICATION) {
            group = Config.gradleTaskGroupName
            description = "Installs eclipse swtbot junit feature."
            doLast {installTargetPlatform(project, config)}
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
        def url = mirror.getApplicationUrl()
        def applicationName = mirror.getApplicationName()

        return "${url}/${applicationName}"
    }

    static void installTargetPlatform(Project project, Config config) {

        def repo = project.uiTestBuild.getSwtbotRepository()
        def plugins = FileHelper.findSubFolder(project.rootProject.buildDir.toPath().resolve("eclipse").toAbsolutePath().toFile(), 'plugins')

        def equinoxLaunchers = new FileNameFinder().getFileNames(plugins.toString(), 'org.eclipse.equinox.launcher_*.jar')
        assert equinoxLaunchers.size() > 0

        def updateSites = []
        def features = []
        def rootNode = new XmlSlurper().parseText(project.uiTestBuild.targetDefinition.text)
        rootNode.locations.location.each { location ->
            updateSites.add(location.repository.@location.text().replace('\${project_loc}', 'file://' +  project.projectDir.absolutePath))
            location.unit.each {unit ->
                features.add("${unit.@id}/${unit.@version}")
            }
        }

        def appFolder = FileHelper.findSubFolder(project.rootProject.buildDir.toPath().resolve("application").toAbsolutePath().toFile(), 'plugins').parentFile

        // convert (pdetest/additions subfolder) to a mini P2 update site
        project.logger.info("Install swtbot in ${appFolder}")
        project.exec {
            commandLine("java",
                    '-cp', equinoxLaunchers.get(0),
                    'org.eclipse.core.launcher.Main',
                    "-application", "org.eclipse.equinox.p2.director",
                    '-repository', updateSites.join(','),
                    '-installIU', features.join(','),
                    "-destination", appFolder,
                    "-consolelog")
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


}
