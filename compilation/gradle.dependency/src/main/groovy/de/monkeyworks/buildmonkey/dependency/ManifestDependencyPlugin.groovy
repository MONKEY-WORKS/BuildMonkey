/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.dependency

import de.monkeyworks.buildmonkey.osgi.ManifestParser
import de.monkeyworks.buildmonkey.osgi.RequiredBundle
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.artifacts.Configuration
import org.gradle.internal.os.OperatingSystem

import java.util.jar.Manifest

class ManifestDependencyPluginExtension {
    Project project
    String mavenGroup
    String eclipseGroup
    Closure dependencyClosure
    def testBundleVersions

    ManifestDependencyPluginExtension(Project project) {
        this.project = project
        this.eclipseGroup = 'eclipse'
        this.testBundleVersions = [:]
    }

    void dependencyHandling(Closure closure) {
        dependencyClosure = closure
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
    private static final List pluginsDependingOnSWT = [
        "org.eclipse.ui",
        "org.eclipse.swt",
        "org.eclipse.xtext.xbase.ui"
    ]

    /**
     * List with dependencies to javax.inject for special handling.
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
            def manifestDependencies = project.manifestDependencies

            // Resolves the dependencies for the compile configuration
            project.configurations.getByName('compile') { Configuration config ->
                requireBundles().each { RequiredBundle dependency ->
                    setProjectDependencies(config, dependency)
                }
                fragmentHost().each { String dependency ->    
                    setProjectDependencies(config, dependency)
                }
                featureDependencies().each { String dependency ->
                    setProjectDependencies(config, dependency)
                }

            }
            def testBundleVersions = manifestDependencies.testBundleVersions
            // Resolves the dependencies for the test compile configuration.
            project.configurations.getByName('testCompile') { Configuration config ->
                testBundles().each { String dependency ->
                    if(dependency != null) {
                        if(testBundleVersions.containsKey(dependency)) {
                            setProjectDependencies(config, dependency, parseVersion(testBundleVersions[dependency])
                        } else {
                            setProjectDependencies(config, dependency)
                        }
                    }
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
        def evaluationStrategy = {
            def properties = new Properties()
            properties.load(it)
            def bundles = properties.get('additional.bundles')
            return bundles ? bundles.split(',') : []
        }
       return evaluateDependencyFile("build.properties", evaluationStrategy)
    }

    /**
     * Reads in the MANIFEST.MF file and returns a list with the entries of the Require-Bundle property.
     *
     * @return List with additional bundles.
     */
    private List requireBundles() {
        def evaluationStrategy = {
            def manifest = new Manifest(it)

            return new ManifestParser(manifest).parseRequireBundles().toArray()
        }
        return evaluateDependencyFile("META-INF/MANIFEST.MF", evaluationStrategy)
    }

    /**
     * Reads in the MANIFEST.MF file and returns a list with the entries of the Require-Bundle property.
     *
     * @return List with additional bundles.
     */
    private List fragmentHost() {
        def evaluationStrategy = {
            def bundles = new Manifest(it).getMainAttributes().getValue('Fragment-Host')
            return bundles ? bundles.split(',') : []
        }
        return evaluateDependencyFile("META-INF/MANIFEST.MF", evaluationStrategy)
    }

    /**
     * Reads in the feature.xml file and returns a list with the entries of several properties holding dependency information.
     *
     * @return List with additional bundles.
     */
    private List featureDependencies() {
        def evaluationStrategy = {
            def parsedXML = new XmlSlurper().parse(it)
            def extractFromXML = { String nodeName, String attribute ->
                return parsedXML.'**'.findAll { node -> node.name() == nodeName }.collect { it["@$attribute"] }
            }
            return extractFromXML("plugin", "id") + extractFromXML("import", "plugin") + extractFromXML("include", "id")
        }
        return evaluateDependencyFile("feature.xml", evaluationStrategy)
    }

    /**
     * Helper method that checks if the file to be read exists and if it does applies a evaluation strategy that should yield a list.
     *
     * @param config Configuration of the dependency. can be compile or testCompile.
     * @param dependency Dependency to check.
     */
    private List evaluateDependencyFile(String path, Closure evaluationStrategy){
        def file = project.file(path)
        return (file.exists()) ? file.withInputStream(evaluationStrategy) : []
    }

    /**
     * Checks given dependency for special handling and sets the resulting dependency as maven artefact.
     *
     * @param config Configuration of the dependency. can be compile or testCompile.
     * @param dependency Dependency to check.
     */
    void setProjectDependencies(Configuration config, String dependency) {
        String name = dependency
        def version = "+"

        if(dependency.contains(";")) {
            name = dependency.substring(0, dependency.indexOf(";"))
            version = parseVersion(dependency)
        }

        name = name.trim()

        setProjectDependency(config, name, version)
    }

    void setProjectDependencies(Configuration config, RequiredBundle bundle) {
        def version = parseVersion(bundle.attributes.get("bundle-version"))
        setProjectDependency(config, bundle.bundleName, version)
    }

    private void setProjectDependency(Configuration config, String name, version) {
        if(!version)
            version = '+'

        if(!name) {
            return
        }

        final Project rootProject = project.rootProject

        // check if the required project is a sibling of this subproject
        def subProject = rootProject.subprojects.find {
            it.name.equals(name)
        }
        // if so, delegate it as a project dependency and stop resolution
        if (subProject) {
            project.dependencies {
                delegate."${config.name}" subProject
            }
            return
        }

        // it is a dependency in a Maven repository, we must resolve group id, artefact name and version
        def manifestDependencies = project.manifestDependencies

        // The default values
        def groupID = ''
        def artifactId = name

        // at first, call the configured function, if group ID is set take over artifact id and version also from there if given
        def ids = manifestDependencies.dependencyClosure(name)

        boolean match = false

        if (ids != null && ids.size() > 0) {
            groupID = ids['groupId']
            match = (groupID != null && groupID != '')
            if (match) {
                if (ids.containsKey('artifactId')) {
                    artifactId = ids['artifactId']
                }
                if (ids.containsKey('version')) {
                    version = ids['version']
                }
            }
        }

        // if group ID still unknown, make a resolution with mavenGroup or eclipseGroup
        if (!match) {
            def mavenGroup = manifestDependencies.mavenGroup
            // second check for third-party dependencies.
            // TODO Add dependency and a list of additional dependencies
            if (name.startsWith(mavenGroup + ".")) {
                groupID = mavenGroup

                if (name.equals(mavenGroup + '.mockito-core')) {
                    addDependency(config, mavenGroup, mavenGroup + '.hamcrest-core')
                    addDependency(config, mavenGroup, mavenGroup + '.objenesis')
                }

                // gradle cannot handle the eclipse junit and vice-versa. so replace this here
                // TODO Replace dependency with another one
            } else if (name.equals('org.junit') || name.equals('junit')) {
                groupID = 'junit'
                artifactId = 'junit'
                version = '4.+'
                // finally these are the eclipse dependencies. just add them and handle some specific stuff
                // TODO Handle remaining, group is eclipse as default,
            } else {
                def eclipseGroup = manifestDependencies.eclipseGroup
                groupID = eclipseGroup

                // yep. SWT is platform dependent. handle the name and add it
                if (pluginsDependingOnSWT.contains(name)) {
                    addDependency(config, eclipseGroup, getSWTBundleName())
                }

                // injection. of. dependencies. needed here!
                if (pluginsDependingOnJavaxInject.contains(name)) {
                    addDependency(config, eclipseGroup, 'javax.inject')
                }
            }
        }
        addDependency(config, groupID, artifactId, version)
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
        final os = OperatingSystem.current()
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


    private String fillVersion(String version) {
        String[] versionParts = version.split("\\.")
        String[] tmp = ["0", "0", "0"]
        if(tmp.length < versionParts.length) {
            return version
        }

        for(int i=0;i<versionParts.length;i++) {
            tmp[i] = versionParts[i]
        }
        return tmp.join(".")
    }

    private String parseVersion(String depBundlesString) {
        if(!depBundlesString)
            return "+"

        String version = depBundlesString
        if(depBundlesString.contains(';')) {
            List elements = depBundlesString.split(';')

            for (String element in elements) {
                if (element.startsWith("bundle-version")) {
                    version = element.substring(element.indexOf('=') + 1)
                    if (version.startsWith('"'))
                        version = version.substring(1)
                    if (version.endsWith('"'))
                        version = version.substring(0, version.length() - 1)
                    break
                }
            }
        }

        // Possible version tuples
        //
        // 2.0 -> [2.0.0,)
        // 2.0.0 -> [2.0.0,)
        // [2.0.0,3.0.0)
        // [2.0,3.0) -> [2.0.0,3.0.0)
        // [2.0,) -> [2.0.0,)
        // [2.0.0,) -> [2.0.0,)
        // [2.1, 3.0) -> [2.1.0, 3.0.0)

        if(!version) {
            return version
        }

        if(!version.contains(",")) {
            version = fillVersion(version)
            return "[${version},)"
        }

        String minBoarder = version.substring(0, 1)
        String maxBoarder = version.substring(version.length() - 1)

        String[] versionParts = version.substring(1, version.length() - 1).split(",")

        String minVersion = fillVersion(versionParts[0])
        String maxVersion = versionParts[1]

        if(maxVersion.length()>0) {
            maxVersion = fillVersion(maxVersion)
        }

        return minBoarder + minVersion + "," + maxVersion + maxBoarder
    }
}
