/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.eclipsesdk.tools

import de.monkeyworks.buildmonkey.eclipsesdk.EclipseConfiguration
import de.monkeyworks.buildmonkey.eclipsesdk.utils.FileSemaphore
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class DownloadEclipseSdkTask extends DefaultTask {

    String downloadUrl

    File targetDir

    @TaskAction
    void downloadSdk() {
        // if multiple builds start on the same machine (which is the case with a CI server)
        // we want to prevent them downloading the same file to the same destination
        def directoryLock = new FileSemaphore(targetDir)
        directoryLock.lock()
        try {
            downloadEclipseSdkUnprotected(getProject())
        } finally {
            directoryLock.unlock()
        }
    }

    private void downloadEclipseSdkUnprotected(Project project) {
        // download the archive
        File sdkArchive = eclipseSdkArchive()

        EclipseConfiguration config = project.eclipseConfiguration

        // delete folder for extracted zip
        Path folder = sdkArchive.parentFile.toPath()
        def os = OperatingSystem.current()
        if(os.isMacOsX()) {
            folder = folder.resolve("Eclipse.app")
        } else {
            folder = folder.resolve("eclipse")
        }
        if(Files.exists(folder)) {
            folder.deleteDir()
        }

        project.logger.info("Download Eclipse SDK from '${downloadUrl}' to '${sdkArchive.absolutePath}'")
        project.ant.get(src: new URL(downloadUrl), dest: sdkArchive)

        // extract it to the same location where it was extracted
        project.logger.info("Extract '$sdkArchive' to '$sdkArchive.parentFile.absolutePath'")
        if (os.isWindows()) {
            project.ant.unzip(src: sdkArchive, dest: sdkArchive.parentFile, overwrite: true)
        } else {
            project.ant.untar(src: sdkArchive, dest: sdkArchive.parentFile, compression: "gzip", overwrite: true)
        }
        
        def targetEclipseDir = Paths.get(config.localEclipseDir)
        if(Files.exists(targetEclipseDir)) {
            targetEclipseDir.deleteDir()
        }

        Files.move(folder, Paths.get(config.localEclipseDir))
    }

    private File eclipseSdkArchive() {
        new File(targetDir, OperatingSystem.current().isWindows() ? 'eclipse-sdk.zip' : 'eclipse-sdk.tar.gz')
    }
}