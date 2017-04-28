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

import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem


/**
 * Holds configuration-dependent settings for the plug-ins.
 */
class Config {

    private final Project project

    static Config on(Project project) {
        return new Config(project)
    }

    private Config(Project project) {
        this.project = project
    }


    File getTargetPlatformsDir() {
        // to avoid configuration timing issues we don't cache the values in fields
        project.hasProperty('targetPlatformsDir') ?
                new File(project.property('targetPlatformsDir') as String) :
                new File(project.buildDir, 'targetPlatforms')
    }

    File getTargetPlatformDir() {
        new File(targetPlatformsDir, "pluginTests")
    }

    File getNonMavenizedTargetPlatformDir() {
        new File(targetPlatformDir, 'target-platform')
    }

    File getRootNonMavenizedTargetPlatformDir() {
        new File(project.rootProject.buildDir, 'targetPlatforms/pluginTests/target-platform')
    }

    File getP2TargetPlatformDir() {
        new File(targetPlatformDir, 'updatesite')
    }

    File getMavenizedTargetPlatformDir() {
        new File(targetPlatformDir, 'mavenized-target-platform')
    }

    File getWorkspace() {
        new File ("${project.buildDir}/pluginTest/workspace")
    }

    /**
     * Returns the Eclipse runtime abbreviation of the operating system.
     *
     * http://help.eclipse.org/indigo/topic/org.eclipse.platform.doc.isv/reference/misc/runtime-options.html
     *
     * @return the operating system: 'linux', 'win32', 'macosx', or null
     */
    static String getOs() {
        OperatingSystem os = OperatingSystem.current()
        os.isLinux() ? 'linux' : os.isWindows() ? 'win32' : os.isMacOsX() ? 'macosx': null
    }

    /**
     * Return the Eclipse runtime abbreviation of the windowing system.
     *
     * http://help.eclipse.org/indigo/topic/org.eclipse.platform.doc.isv/reference/misc/runtime-options.html
     *
     * @return the windowing system: 'gtk', 'win32', 'cocoa', or null
     */
    static String getWs() {
        OperatingSystem os = OperatingSystem.current()
        os.isLinux() ? 'gtk' : os.isWindows() ? 'win32' : os.isMacOsX() ? 'cocoa' : null
    }

    /**
     * Returns the Eclipse runtime abbreviation of the architecture.
     *
     * http://help.eclipse.org/indigo/topic/org.eclipse.platform.doc.isv/reference/misc/runtime-options.html
     *
     * @return the architecture: x86_64 or x86
     */
    static String getArch() {
        System.getProperty("os.arch").contains("64") ? "x86_64" : "x86"
    }

    /**
     * Sets some constants in the target project's build script.
     */
    static exposePublicConstantsFor(Project project) {
        project.ext.ECLIPSE_OS = os
        project.ext.ECLIPSE_WS = ws
        project.ext.ECLIPSE_ARCH = arch
    }

    /**
     * Returns the group name of all tasks that contribute to the Eclipse Plugin build.
     *
     * @return the name of the group where all tasks defined in this project should show upon the execution of <code>gradle tasks</code>
     */
    static String getGradleTaskGroupName() {
        return "PDE Test Plugin Build"
    }

}
