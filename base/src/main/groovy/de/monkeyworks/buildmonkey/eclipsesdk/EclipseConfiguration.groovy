/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.eclipsesdk

import org.gradle.api.Project

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Created by Johannes Tandler on 24/02/2017.
 */
class EclipseConfiguration {

    /**
     * download url of eclipse
     */
    def eclipseSdkURL

    /**
     * version of eclipse
     */
    def eclipseVersion

    /**
     * local eclipse directory
     */
    String localEclipseDir

    /**
     * Version number of org.eclipse.equinox.launcher
     */
    String launcherVersion

    EclipseConfiguration(Project project) {
        eclipseSdkURL = 'http://ftp-stud.hs-esslingen.de/Mirrors/eclipse/eclipse/downloads/drops4/R-4.6.1-201609071200'
        eclipseVersion = '4.6.1'
        localEclipseDir = project.buildDir.toPath().resolve("eclipsesdk")

        project.project.gradle.taskGraph.whenReady {
            if(launcherVersion != null) {
                return
            }

            findLauncherVersion()
        }
    }

    Path getPluginPath() {
        def os = org.gradle.internal.os.OperatingSystem.current()
        def eclipsePath = Paths.get(localEclipseDir)
        def basePath = eclipsePath.resolve("plugins/")

        if(os.isMacOsX()) {
            basePath = eclipsePath.resolve("Contents/Eclipse/plugins/")
        }
        return basePath
    }

    void findLauncherVersion() {
        if(!Files.exists(getPluginPath())) {
            return
        }

        def equinoxLaunchers = new FileNameFinder().getFileNames(getPluginPath().toAbsolutePath().toFile().toString(), 'org.eclipse.equinox.launcher_*.jar')
        assert equinoxLaunchers.size() > 0

        String tmp = equinoxLaunchers.get(0)
        tmp = tmp.substring(tmp.indexOf("org.eclipse.equinox.launcher_") + ("org.eclipse.equinox.launcher_").length())
        tmp = tmp.substring(0, tmp.indexOf(".jar"))

        launcherVersion = tmp
    }

    Path getLauncherPath() {
        if(launcherVersion == null) {
            findLauncherVersion()
        }
        return getPluginPath().resolve("org.eclipse.equinox.launcher_" + launcherVersion + ".jar")
    }

    boolean isDownloded() {
        return Files.exists(getLauncherPath())
    }
}
