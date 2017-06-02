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

import de.monkeyworks.buildmonkey.pde.testing.UiTestExtension
import de.monkeyworks.buildmonkey.pde.tools.FileHelper
import groovy.xml.MarkupBuilder

import org.gradle.api.Plugin
import org.gradle.api.Project

import java.util.jar.JarFile
import java.util.jar.Manifest
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.testing.Test

import de.monkeyworks.buildmonkey.pde.testing.UiTestExecuter

import javax.inject.Inject
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


/**
 * Gradle plug-in to build Eclipse test bundles and launch tests.
 * <p/>
 * It contributes the following DSL to describe a testing project:
 * <pre>
 * pluginTest {
 *     fragmentHost 'host.plugin.id'
 *     applicationName 'org.eclipse.pde.junit.runtime.coretestapplication'
 *     optionsFile file('.options')
 * }
 * </pre>
 * If the test project is an Eclipse plug-in fragment, then the the {@code fragmentHost} specifies
 * the host plug-in's ID (not mandatory). The {@code applicationName} is the PDE test runner class.
 * The {@code optionsFile} specifies a file containing extra arguments for the testing (not
 * mandatory).
 * <p/>
 * The tests are launched with PDE. The process is: (1) Copy the target platform to the build
 * folder. (2) Install the test plug-in and it's dependencies into the copied target platform with
 * P2. (3) Launch Eclipse with the PDE testing application (4) Collect the test results.
 * <p/>
 * The way how the tests are collected from the testing project and how the results are collected is
 * defined in the testing package.
 * <p/>
 * More information on the PDE testing automation:
 * <a href="http://www.eclipse.org/articles/Article-PDEJUnitAntAutomation/">
 * http://www.eclipse.org/articles/Article-PDEJUnitAntAutomation</a>.
 */
class UiTestPlugin implements Plugin<Project> {

    // name of the root node in the DSL
    static String DSL_EXTENSION_NAME = "uiTest"

    // task names
    static final TASK_NAME_UI_TEST = 'uiTest'

    public final FileResolver fileResolver


    @Inject
    public UiTestPlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver
    }

    @Override
    public void apply(Project project) {
        configureProject(project)
        validateDslBeforeBuildStarts(project)
        addTaskCreateUiTest(project)
    }

    static void configureProject(Project project) {
        project.extensions.create(DSL_EXTENSION_NAME, UiTestExtension)
        project.getPlugins().apply(de.monkeyworks.buildmonkey.pde.BundlePlugin)

        // append the sources of each first-level dependency and its transitive dependencies of
        // the 'bundled' configuration to the 'bundledSource' configuration
        project.afterEvaluate {
            project.configurations.bundled.resolvedConfiguration.firstLevelModuleDependencies.each { dep ->
                addSourcesRecursively(project, dep)
            }
        }
    }

    private static def addSourcesRecursively(project, dep) {
        project.dependencies {
            bundledSource group: dep.moduleGroup, name: dep.moduleName, version: dep.moduleVersion, classifier: 'sources'
        }
        dep.children.each { childDep -> addSourcesRecursively(project, childDep) }
    }

    static void validateDslBeforeBuildStarts(Project project) {
        project.gradle.taskGraph.whenReady {
            // the eclipse application must be defined
            assert project.uiTest.applicationName != null
        }
    }

    static void addTaskCreateUiTest(Project project) {
        Config config = Config.on(project)
        def pluginTest = project.task(TASK_NAME_UI_TEST, type: Test) {
            group = "PDE test plugin"
            description = 'Installs the SWTBot and the tests into a RCP application and runs them.'

            // configure the test runner to execute all classes from the project
            testExecuter = new UiTestExecuter(project)
            testClassesDir = project.sourceSets['main'].output.classesDir
            classpath = project.sourceSets.main.output + project.sourceSets.test.output
            reports.html.destination = new File("${project.reporting.baseDir}/uiTest")

            // set some system properties for the test Eclipse
            systemProperty('osgi.requiredJavaVersion','1.8')
            systemProperty('eclipse.pde.launch','true')
            systemProperty('eclipse.p2.data.area','@config.dir/../p2')

            // set the task outputs
            def testDistributionDir = project.file("$project.buildDir/uiTest/application/")
            def additionalPluginsDir = project.file("$project.buildDir/uiTest/additions")
            outputs.dir testDistributionDir
            outputs.dir additionalPluginsDir

            // the input for the task 'pluginTest' is the output jars from the dependent projects
            // consequently we have to set it after the project is evaluated
            project.afterEvaluate {
                for (tc in project.configurations.compile.dependencies.withType(ProjectDependency)*.dependencyProject.tasks) {
                    def taskHandler = tc.findByPath("jar")
                    if(taskHandler != null) inputs.files taskHandler.outputs.files
                }
            }

            doFirst { beforePluginTest(project, config, testDistributionDir, additionalPluginsDir) }

            dependsOn  ":${UITestDefinitionPlugin.TASK_NAME_INSTALL_TARGET_PLATFORM}"

        }

        // Make sure that every time the testing is running the 'pluginTest' task is also gets executed.
        pluginTest.dependsOn 'test'
        pluginTest.dependsOn 'jar'
    }


    static void beforePluginTest(Project project, Config config, File testDistributionDir, File additionalPluginsDir) {
        // before testing, create a fresh eclipse IDE with all dependent plugins installed
        // first delete the test eclipse distribution and the original plugins.
        testDistributionDir.deleteDir()
        additionalPluginsDir.deleteDir()
        def workspace = new File (project.getBuildDir(), "/uiTest/workspace")
        if(workspace.exists()) {
            workspace.deleteDir()
        }

        // copy the target platform to the test distribution folder
        copyTargetPlatformToBuildFolder(project, config, testDistributionDir)

        // publish the dependencies' output jars into a P2 repository in the additions folder
        // install all elements from the P2 repository into the test Eclipse distribution
        installDependenciesIntoTargetPlatform(project, config, additionalPluginsDir, testDistributionDir)
    }


    static void copyTargetPlatformToBuildFolder(Project project, Config config,  File distro) {
        project.copy {
            from project.file("$project.rootProject.buildDir/application/application")
            into distro
        }
    }

    static void installDependenciesIntoTargetPlatform(Project project, Config config, File additionalPluginsDir, File testDistributionDir) {
        def bundles = prepareUpdateSite(project, additionalPluginsDir)
        def plugins = FileHelper.findSubFolder(project.rootProject.buildDir.toPath().resolve("eclipse").toAbsolutePath().toFile(), 'plugins')

        def equinoxLaunchers = new FileNameFinder().getFileNames(plugins.toString(), 'org.eclipse.equinox.launcher_*.jar')
        assert equinoxLaunchers.size() > 0

        def repo = project.rootProject.uiTestBuild.getSwtbotRepository()

        // convert (pdetest/additions subfolder) to a mini P2 update site
        project.logger.info("Create additional repository in ${additionalPluginsDir.path}")
        project.exec {
            commandLine("java",
                    '-cp', equinoxLaunchers.get(0),
                    'org.eclipse.core.launcher.Main',
                    "-application", "org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher",
                    "-metadataRepository", "file:${additionalPluginsDir.path}",
                    "-artifactRepository", "file:${additionalPluginsDir.path}",
                    "-source", "$additionalPluginsDir.path",
                    "-nosplash")
        }

        // take the mini P2 update sites from the build folder and install it into the test application distribution
        def appFolder  = FileHelper.findSubFolder(testDistributionDir, 'plugins').parentFile
        project.logger.info("Install additional repository ${additionalPluginsDir.path} with ${bundles} into ${appFolder}")
        println("Install additional repository ${additionalPluginsDir.path} with ${bundles} into ${appFolder}")
        project.exec {
            commandLine("java",
                    '-cp', equinoxLaunchers.get(0),
                    'org.eclipse.core.launcher.Main',
                    '-application', 'org.eclipse.equinox.p2.director',
                    '-artifactRepository', "file:${testDistributionDir.path},${repo}",
                    '-repository', "file:${additionalPluginsDir.path}",
                    '-installIU', bundles,
                    '-destination', appFolder,
                    '-consolelog')
        }
    }


    /**
     * Resolves the direct project dependencies of the given projects recursively.
     *
     * @param projects List with projects to resolve the project dependencies from
     * @return Accumulated list with input projects and all found dependencies in every tier
     */
    private static List<Project> resolveProjectDependencies (List<Project> projects) {
        List<Project> newProjects = []
        projects.each { project ->
            for (ProjectDependency dep : project.configurations.compile.dependencies.withType(ProjectDependency)) {
                Project p = dep.dependencyProject

                if(!projects.contains(p)) {
                    newProjects.add(p)
                }
            }
        }

        if(newProjects.size() > 0) {
            newProjects = resolveProjectDependencies(newProjects)
        }

        projects.addAll(newProjects)
        return projects
    }

    /**
     *  Prepares the update site repository by copying all jars to the plugin folder and
     *  create a comma separated list as String with the installable units.
     *
     * @param project Project to prepare for
     * @param additionalPluginsDir Folder to copy the artefacts into
     * @return String with a comma separated list of all installable units.
     */
    private static String prepareUpdateSite(Project project, File additionalPluginsDir) {
        List<Project> projects = [project]

        // Resolve all project dependencies recursively
        projects = resolveProjectDependencies(projects)

        // Take all build jar artefacts from the projects
        def bundles = []
        projects.each { p ->
            p.tasks.jar.outputs.files.each { singleFile ->
                bundles.add(singleFile)
            }
        }

        def projectPattern = project.uiTest.projectPattern
        project.configurations.runtime.each {
            String jar = it.getName()

            // In maven cache it is group qualifier are separated by slash or backslash, in gradle cache they are separated by an dot,
            // so we use regular expressions
            projectPattern.each { pattern ->
                if (jar.matches(pattern)) {
                    bundles.add(it)
                }
            }
        }

        // Create a plugin folder inside of updateSite folder
        File pluginFolder = new File ("$additionalPluginsDir.path" + "/plugins")

        if(!pluginFolder.exists()) {
            pluginFolder.mkdirs()
        }

        // Create a feature containing all the project dependencies
        def targetDir = additionalPluginsDir.toPath().resolve("features")
        def targetFile = targetDir.resolve("${project.name}.feature_1.0.0.jar").toFile()

        if(!targetFile.getParentFile().exists()) {
            targetDir.toFile().mkdirs()
        }

        // Inject a feature.xml with the dependencies
        targetFile.withOutputStream {
            ZipOutputStream zipStream = new ZipOutputStream(it)
            zipStream.putNextEntry(new ZipEntry('feature.xml'))
            Writer w = new OutputStreamWriter(zipStream, 'UTF-8')
            def xmlMarkup = new MarkupBuilder(w)

            xmlMarkup.'feature'('id': "${project.name}.feature", 'label':"TargetPlatform feature for ${project.name}", 'version':"1.0.0", 'provider-name':"Generated with monkeyworks p2 tooling") {
                bundles.each { unit ->
                    Manifest mani = new JarFile(unit).getManifest()
                    def name = mani.getMainAttributes().getValue('Bundle-SymbolicName')
                    if(name != null) {

                        if (name.contains(';')) {
                            name = name.substring(0, name.indexOf(';'))
                        }
                        def version = mani.getMainAttributes().getValue('Bundle-Version')
                        project.copy {
                            from unit
                            into pluginFolder
                            rename '.+', "${name}_${version}.jar"
                        }
                        xmlMarkup.'plugin'('id': name, 'version': version, unpack: false, 'download-size': "0", 'install-size': "0")
                    }
                }
            }
            zipStream.closeEntry()

            zipStream.putNextEntry(new ZipEntry('p2.inf'))
            w = new OutputStreamWriter(zipStream, 'UTF-8')
            def pw = new PrintWriter(w)

            pw.println "properties.1.name=org.eclipse.equinox.p2.type.category"
            pw.println "properties.1.value=true"

            pw.flush()
            w.flush()

            zipStream.closeEntry()
            zipStream.close()
        }

        return "${project.name}.feature.feature.group"
    }



    /**
     * Removes .jar extensions and transform name-version to name/version format for be accepted as installable unit.
     *
     * @param bundleName Name to transform, normally name-version.jar
     * @return Name as installable unit, can be name or name/version
     */
    private static String transformBundleName (String bundleName) {
        String transformedName = bundleName.replace(".jar", "")
        int index = transformedName.lastIndexOf('-')
        if(index > 0) {
            String version = transformedName.substring(index + 1)
            transformedName = transformedName.substring(0, index) + "/" + version
        }
        return transformedName
    }

}

