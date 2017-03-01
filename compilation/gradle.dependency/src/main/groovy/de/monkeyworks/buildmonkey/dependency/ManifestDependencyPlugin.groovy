/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.dependency

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.artifacts.Configuration
import org.gradle.internal.os.OperatingSystem

import java.util.jar.Manifest
import java.util.Properties
import java.lang.IllegalArgumentException

class ManifestDependencyPattern {
    String projectPattern
    String projectGroup
}

class ManifestDependencyPluginExtension {
    Project project
    def projectPatterns = [:]
    String mavenGroup

    ManifestDependencyPluginExtension(Project project) {
        this.project = project
    }

    void addProjectPattern(Closure closure) {
        def pattern = new ManifestDependencyPattern()
        project.configure(pattern, closure)
        projectPatterns[pattern.projectPattern] = pattern.projectGroup
    }
}

/**
 * Resolves versions of artefacts from the eclipse maintained MANIFEST.MF and build.properties files.
 *
 * @author Robert Rau
 */
class ManifestDependencyPlugin implements Plugin<Project> {
    static final String DSL_EXTENSION_NAME = "manifestDependencies"
    static final String TASK_NAME_RESOLVE_MANIFEST_DEPENDENCIES = "resolveManifestDependencies"

    /**
     * List with bundles requiring swt dependencies including the platform specific bundles.
     */
    private static final List pluginsDependeningOnSWT = [
        "org.eclipse.ui",
        "org.eclipse.swt",
        "org.eclipse.xtext.xbase.ui"
    ]

    /**
     * List with dependecies to javax.inject for special handling.
     */
    private static final List pluginsDependingOnJavaxInject = [
        "org.eclipse.ui.workbench",
        "org.eclipse.e4.core.services"
    ]

    /**
     * Project resolving the dependcies for.
     */
    private Project project

    @Override
    void apply(Project project) {
        this.project = project
        project.extensions.create(DSL_EXTENSION_NAME, ManifestDependencyPluginExtension, project)

        configureProject(project)

        addTasksForResolveDependencies(project)
    }


    static void configureProject(Project project) {
        // apply the java plugin
        project.plugins.apply(JavaPlugin)
    }

    void addTasksForResolveDependencies(Project project) {
        def name = TASK_NAME_RESOLVE_MANIFEST_DEPENDENCIES

        project.tasks.create("$name")
        //if (project.tasks.findByPath('assemble') == null) {project.tasks.create('assemble')}
        //project.tasks.assemble.dependsOn "${name}"

        project.tasks."${name}".dependsOn project.configurations.compile, project.configurations.testCompile

        project.afterEvaluate {
            // Resolves the dependencies become a compile dependency.
            project.configurations.getByName('compile') { Configuration config -> 
                requireBundles().each { String dependency ->    
                    setProjectDependencies(config, dependency)
                }
                fragmentHost().each { String dependency ->    
                    setProjectDependencies(config, dependency)
                }
            }

            // Resolves the dependencies become a test compile dependency.
            project.configurations.getByName('testCompile') { Configuration config ->
                testBundles().each { String dependency ->    
                    setProjectDependencies(config, dependency)
                }
            }
        }
    }

    /**
     * Reads in the build.properties file and returns a list with the entries of the additional.bundles property.
     *
     * @return List with additional bundles.
     */
    private List testBundles() {
        String bundles  = project.file("build.properties").withInputStream {
            def properties = new Properties() 
            properties.load(it)
            properties.get('additional.bundles')
        }
        return bundles ? bundles.split(',') : []   
    }

    /**
     * Reads in the MANIFEST.MF file and returns a list with the entries of the Require-Bundle property.
     *
     * @return List with additional bundles.
     */
    private List requireBundles() {
        String bundles = project.file("META-INF/MANIFEST.MF").withInputStream {
            def mani = new Manifest(it)
            mani.getMainAttributes().getValue('Require-Bundle')
        }
        return bundles ? bundles.split(',') : []
    }

    /**
     * Reads in the MANIFEST.MF file and returns a list with the entries of the Require-Bundle property.
     *
     * @return List with additional bundles.
     */
    private List fragmentHost() {
        String bundles = project.file("META-INF/MANIFEST.MF").withInputStream {
            def mani = new Manifest(it)
            mani.getMainAttributes().getValue('Fragment-Host')
        }
        return bundles ? bundles.split(',') : []
    }

        /**
     * Checks given dependecy for special handling and sets the resulting dependency as maven artefact.
     *
     * @param config Configuration of the dependency. can be compile or testCompile.
     * @param dependency Dependency to check.
     */
    void setProjectDependencies(Configuration config, String dependency) {
        String name = dependency.contains(';') ? dependency.split(';')[0] : dependency
        name = name.trim()

        def manifestDependencies = project.manifestDependencies
        def handledByPattern = false

        // TODO Create group from artifact name
        manifestDependencies.projectPatterns.each { pattern, group -> 
            // first check for our own projects and delegate it to the handling method
            if (name.matches(pattern)) {
                handleCustomDependency(config, name, group)    
                handledByPattern = true
            }
        }

        if(handledByPattern) {
            return
        }

        def mavenGroup = manifestDependencies.mavenGroup
        // second check for third-party dependencies.
        // TODO Add dependency and a list of additional dependencies
        if (name.startsWith(mavenGroup + ".")) {
            addDependency(config, mavenGroup, name)
            if(name.equals(mavenGroup + '.mockito-core')) {
                addDependency(config, mavenGroup, mavenGroup + '.hamcrest-core')
                addDependency(config, mavenGroup, mavenGroup + '.objenesis')
            }

        // gradle cannot handle the eclipse junit and vice-versa. so replace this here
        // TODO Replace dependency with another one
        } else if (name.equals('org.junit') || name.equals('junit')) {
            addDependency(config, 'junit', 'junit', '4.+')

        // finally these are the eclipse dependencies. just add them and handle some specific stuff
        // TODO Handle remaining, group is eclipse as default, 
        } else {
            addDependency(config, 'eclipse', name)

            // yep. SWT is platform dependent. handle the name and add it
            if (pluginsDependeningOnSWT.contains(name)) {
                addDependency(config, 'eclipse', getSWTBundleName())
            }
            
            // injection. of. dependencies. needed here!
            if (pluginsDependingOnJavaxInject.contains(name)) {
                addDependency(config, 'eclipse', 'javax.inject')  
            }
        }    
    }

    /**
     * Composes the maven artefact properties by checking branch definitions and versions.
     *
     * @param config Configuration of the dependency. can be compile or testCompile.
     * @param name Name of the bundle.
     */
    private void handleCustomDependency(Configuration config, String name, String group) {
        final Project rootProject = project.rootProject

        // check if the required project is a sibbling of this subproject
        def subProject = rootProject.subprojects.find {
            it.name.equals(name)
        }
        // if so, delegate it as a project dependency
        if (subProject) {
            project.dependencies {
                delegate."${config.name}" subProject
            }

        // if not, construct the required dependecy from the project configuration and add it this way
        } else {
            def groupId = name.split('\\.')[1]
            def projectGroup = group + '.' + groupId
            def branchName = rootProject.property("${groupId}_branch")
            addDependency(config, "${projectGroup}-${branchName}", name, "${rootProject.version}")
        }
    }

    /**
     * Adds the dependency defined by groupId, artifact and version to the given configuration.
     *
     * @param config Configuration of the dependency. can be compile or testCompile.
     * @param group Group id of the maven artefact.
     * @param artifact Artefact id of the maven artefact.
     * @param version Version of the artefact. Default is '+', will be resolved at publishing step.
     */
    private void addDependency(Configuration config, String group, String artifact, String version='+') {
        config.dependencies.add project.dependencies.create("${group}:${artifact}:${version}")
    }

    /**
     * Composes the platform dependend bundle name of the swt bundle.
     *
     * @return Platform dependend swt bundle name.
     */
    private static String getSWTBundleName() {
        final def os = OperatingSystem.current()
        String osString
        if (os.isWindows()) {
            osString = 'win32.win32'

        } else if (os.isLinux()) {
            osString = 'gtk.linux'

        } else if (os.isMacOsX()) {
            osString = 'cocoa.macosx'
            
        } else {
            throw new IllegalArgumentException("Detected not supported OperatingSystem ${os.getDisplayName()}")
        }
        return "org.eclipse.swt.${osString}.x86_64" 
    }
}