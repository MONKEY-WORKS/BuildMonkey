/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.pde.tools

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import de.monkeyworks.buildmonkey.pde.utils.FileSemaphore
//DUPLICATED, must be part of a common bundle
class DownloadApplicationTask extends DefaultTask {

    String downloadUrl
    File targetFile
    File targetDir

    @TaskAction
    public void downloadApplication() {
        def directoryLock = new FileSemaphore(targetDir)
        directoryLock.lock()
        try {
            downloadApplicationUnprotected(getProject())
        } finally {
            directoryLock.unlock()
        }
    }

    private void downloadApplicationUnprotected(Project project) {
        // download the archive
        project.logger.info("Download Eclipse SDK from '${downloadUrl}' to '${targetFile.absolutePath}'")
        project.ant.get(src: new URL(downloadUrl), dest: targetFile)

        // extract it to the same location where it was extracted
        project.logger.info("Extract '$targetFile' to '$targetFile.parentFile.absolutePath'")
        if (OperatingSystem.current().isWindows()) {
            project.ant.unzip(src: targetFile, dest: targetFile.parentFile, overwrite: true)
        } else {
            project.ant.untar(src: targetFile, dest: targetFile.parentFile, compression: "gzip", overwrite: true)
        }
    }

}