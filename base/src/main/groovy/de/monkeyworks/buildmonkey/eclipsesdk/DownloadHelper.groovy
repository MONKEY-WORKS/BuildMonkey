/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.eclipsesdk

import de.monkeyworks.buildmonkey.eclipsesdk.tools.DownloadEclipseSdkTask
import org.gradle.api.Project

/**
 * Created by Johannes Tandler on 24/02/2017.
 */
class DownloadHelper {

    static final String TASK_NAME_DOWNLOAD_ECLIPSE_SDK = "downloadEclipseSdk"
    static final String ECLIPSE_CONFIGURATION_EXTENSION_NAME = "eclipseConfiguration"

    static void addEclipseConfigurationExtension(Project project) {
        if(project.extensions.findByName(ECLIPSE_CONFIGURATION_EXTENSION_NAME) == null) {
            project.extensions.create(ECLIPSE_CONFIGURATION_EXTENSION_NAME, EclipseConfiguration, project)
        }
    }

    static void addTaskDownloadEclipseSdk(Project project) {
        if (project.tasks.findByPath(TASK_NAME_DOWNLOAD_ECLIPSE_SDK) != null)
            return

        project.task(TASK_NAME_DOWNLOAD_ECLIPSE_SDK, type: DownloadEclipseSdkTask) {
            project.afterEvaluate {
                description = "Downloads an Eclipse SDK to perform P2 operations with."

                EclipseConfiguration config = project.eclipseConfiguration

                onlyIf {
                    return !config.isDownloded()
                }

                def os = org.gradle.internal.os.OperatingSystem.current()
                def arch = System.getProperty("os.arch").contains("64") ? "-x86_64" : ""
                def eclipseUrl = config.eclipseSdkURL
                println eclipseUrl
                def eclipseVersion = config.eclipseVersion

                if (os.windows) {
                    downloadUrl = "${eclipseUrl}/eclipse-SDK-${eclipseVersion}-win32${arch}.zip"
                } else if (os.macOsX) {
                    downloadUrl = "${eclipseUrl}/eclipse-SDK-${eclipseVersion}-macosx-cocoa${arch}.tar.gz"
                } else if (os.linux) {
                    downloadUrl = "${eclipseUrl}/eclipse-SDK-${eclipseVersion}-linux-gtk${arch}.tar.gz"
                }

                targetDir = project.buildDir.toPath().resolve("downloads").toFile()
            }
        }
    }
}
