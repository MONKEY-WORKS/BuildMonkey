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

import groovy.xml.MarkupBuilder

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTree

import java.util.jar.JarFile
import java.util.jar.Manifest
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.testing.Test

import de.monkeyworks.buildmonkey.pde.testing.PluginTestExecuter
import de.monkeyworks.buildmonkey.pde.testing.PluginTestExtension

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
class PluginTestPlugin implements Plugin<Project> {

    // name of the root node in the DSL
    static String DSL_EXTENSION_NAME = "pluginTest"

    // task names
    static final TASK_NAME_PLUGIN_TEST = 'pluginTest'

    public final FileResolver fileResolver


    @Inject
    public PluginTestPlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver
    }

    @Override
    public void apply(Project project) {
        configureProject(project)
        validateDslBeforeBuildStarts(project)
        addTaskCreatePluginTest(project)
    }

    static void configureProject(Project project) {
        project.extensions.create(DSL_EXTENSION_NAME, PluginTestExtension)
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
            assert project.pluginTest.applicationName != null
        }
    }

    static void addTaskCreatePluginTest(Project project) {
        Config config = Config.on(project)
        def pluginTest = project.task(TASK_NAME_PLUGIN_TEST, type: Test) {
            group = "PDE test plugin"
            description = 'Installs all dependencies into a fresh Eclipse, runs the IDE and executes the test classes with the PDE Test Runner'

            // configure the test runner to execute all classes from the project
            testExecuter = new PluginTestExecuter(project)
            testClassesDir = project.sourceSets['main'].output.classesDir
            classpath = project.sourceSets.main.output + project.sourceSets.test.output
            reports.html.destination = new File("${project.reporting.baseDir}/pluginTest")

            // set some system properties for the test Eclipse
            systemProperty('osgi.requiredJavaVersion','1.8')
            systemProperty('eclipse.pde.launch','true')
            systemProperty('eclipse.p2.data.area','@config.dir/../p2')

            // set the task outputs
            def testDistributionDir = project.file("$project.buildDir/pluginTest/eclipse")
            def additionalPluginsDir = project.file("$project.buildDir/pluginTest/additions")
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
            
            dependsOn  ":${TestDefinitionPlugin.TASK_NAME_PREPARE_TARGET_PLATFORM}"

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

        // copy the target platform to the test distribution folder
        copyTargetPlatformToBuildFolder(project, config, testDistributionDir)

        // publish the dependencies' output jars into a P2 repository in the additions folder
        // install all elements from the P2 repository into the test Eclipse distribution
        installDependenciesIntoTargetPlatform(project, config, additionalPluginsDir, testDistributionDir)
    }


    static void copyTargetPlatformToBuildFolder(Project project, Config config,  File distro) {
        project.copy {
            from config.rootNonMavenizedTargetPlatformDir
            into distro
        }
    }

    static void installDependenciesIntoTargetPlatform(Project project, Config config, File additionalPluginsDir, File testDistributionDir) {
        def bundles = prepareUpdateSite(project, additionalPluginsDir)
        def launcherVersion = project.pluginTest.launcherVersion

        // convert (pdetest/additions subfolder) to a mini P2 update site
        project.logger.info("Create additional repository in ${additionalPluginsDir.path}")
        println("Create additional repository in ${additionalPluginsDir.path}")
        project.exec {
            commandLine("java", 
                    '-cp', project.rootProject.buildDir.toPath().resolve("eclipse/eclipse/plugins/org.eclipse.equinox.launcher_${launcherVersion}.jar").toFile(),
                    'org.eclipse.core.launcher.Main',
                    "-application", "org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher",
                    "-metadataRepository", "file:${additionalPluginsDir.path}",
                    "-artifactRepository", "file:${additionalPluginsDir.path}",
                    "-source", "$additionalPluginsDir.path",
                    "-nosplash")
        }

        // take the mini P2 update sites from the build folder and install it into the test Eclipse distribution
        project.logger.info("Install additional repository ${additionalPluginsDir.path} with ${bundles} into ${testDistributionDir}")
        println("Install additional repository ${additionalPluginsDir.path} with ${bundles} into ${testDistributionDir}")
        project.exec {
            commandLine("java",
                    '-cp', project.rootProject.buildDir.toPath().resolve("eclipse/eclipse/plugins/org.eclipse.equinox.launcher_${launcherVersion}.jar").toFile(),
                    'org.eclipse.core.launcher.Main',
                    '-application', 'org.eclipse.equinox.p2.director',
                    '-metadataRepository', "file:${project.rootProject.buildDir}/eclipse/eclipse/p2/org.eclipse.equinox.p2.engine/profileRegistry/SDKProfile.profile",
                    '-artifactRepository', "file:${project.rootProject.buildDir}/eclipse/eclipse,file:${testDistributionDir.path}",
                    '-repository', "file:${additionalPluginsDir.path}",
                    '-installIU', bundles,
                    '-destination', testDistributionDir,
                    '-profile', 'PluginProfile',
                    '-p2.os', Config.os,
                    '-p2.ws', Config.ws,
                    '-p2.arch', Config.arch,
                    '-roaming',
                    '-nosplash')
        }

        createConfig(project, config, testDistributionDir)
    }

    /**
     * Creates a config.ini with the bundles to load and start.
     *
     * @param project Project requesting the plugin tests.
     * @param config Static plugin configuration
     * @param testDistributionDir Folder where the eclipse rcp distribution persists for the test runtime.
     */
    private static void createConfig(Project project, Config config, File testDistributionDir) {

        File configuratorFolder = new File ("${testDistributionDir}/configuration/org.eclipse.equinox.simpleconfigurator")

        if(!configuratorFolder.exists()) {
            configuratorFolder.mkdirs()
        }

        Properties bundles = writeBundlesInfo(project, testDistributionDir)

        File workspace = config.getWorkspace()
        if(!workspace.exists()) {
            workspace.mkdirs()
        }

        File configIni = new File("${testDistributionDir}/configuration/config.ini")

        configIni.withWriter { out ->
            out.println "#Configuration File"
            out.println "#Some timestamp"

            out.println 'org.eclipse.update.reconcile=false'
            out.println 'eclipse.p2.profile=SDKProfile'
            out.println "osgi.instance.area=${testDistributionDir}/plugins"
            out.println "osgi.instance.area.default=${workspace}"
            def key  = 'org.eclipse.osgi'
            if(bundles.containsKey(key)) {
                def version = bundles.get(key)
                out.println "osgi.framework=file\\:plugins/${key}_${version}.jar"
            }
            else {
                println "Missing required version for bundle $key"
            }

            key = 'org.eclipse.equinox.simpleconfigurator'
            if(bundles.containsKey(key)) {
                def version = bundles.get(key)
                out.println "osgi.bundles=reference\\:file\\:${key}_${version}.jar@1\\:start"
            }
            else {
                println "Missing required version for bundle $key"
            }

            out.println 'org.eclipse.equinox.simpleconfigurator.configUrl=file\\:org.eclipse.equinox.simpleconfigurator/bundles.info'
            out.println 'osgi.configuration.cascaded=false'
            
            key = 'org.eclipse.sdk'
            def instanceID = '4.5.0.I20150603-2000'
            if(bundles.containsKey(key)) {
                def version = bundles.get(key)
                instanceID = version.replace('v', 'I')
            }
            else {
                println "Missing required version for bundle $key"
            }

            out.println 'osgi.splashPath=platform\\:/base/plugins/org.eclipse.platform'
            out.println 'eclipse.p2.data.area=@config.dir/../p2/'
            out.println 'equinox.use.ds=true'
            out.println "eclipse.buildId=${instanceID}"
            out.println 'eclipse.product=org.eclipse.platform.ide'
            key = 'org.eclipse.osgi.compatibility.state'
            if(bundles.containsKey(key)) {
                def version = bundles.get(key)
                out.println "osgi.framework.extensions=reference\\:file\\:${key}_${version}.jar"
            }
            else {
                println "Missing required version for bundle $key"
            }
            
            out.println 'osgi.bundles.defaultStartLevel=4'
            out.println 'eclipse.application=org.eclipse.ui.ide.workbench'
        }

    }

    /**
     * Creates a bundle.info with the bundles to load and start.
     *
     * @param project Project requesting the plugin tests.
     * @param testDistributionDir Folder where the eclipse rcp distribution persists for the test runtime.
     */
    private static Properties writeBundlesInfo(Project project, File testDistributionDir) {
        def autoStarts = [  'org.eclipse.equinox.common',
                            'org.eclipse.core.runtime',
                            'org.eclipse.equinox.ds',
                            'org.eclipse.equinox.event',
                            'org.eclipse.equinox.simpleconfigurator',
                            'org.eclipse.osgi',
                            'org.eclipse.equinox.p2.reconciler.dropins']

        def startLevels = [ 'org.eclipse.equinox.common' : '2' ,
                            'org.eclipse.core.runtime': '4',
                            'org.eclipse.equinox.ds' : '2',
                            'org.eclipse.equinox.event' :'4',
                            'org.eclipse.equinox.simpleconfigurator' : '1',
                            'org.eclipse.osgi' : '-1']

        def blackList = ['org.eclipse.core.runtime.compatibility.registry']

        File bundleInfo = new File ("${testDistributionDir}/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info")

        def dependencies = project.configurations.testRuntime
        def needed = []
        dependencies.each {
            String jar = it.getName()
            def parts = jar.split("-[\\.\\d+]+")
            needed.add(parts[0])
        }


        Properties bundles = new Properties()

        bundleInfo.withWriter { out ->
            def pluginFolderName = "${testDistributionDir}/plugins"
            File pluginFolder = new File(pluginFolderName)
            String[] plugins = pluginFolder.list()

            plugins.sort(java.text.Collator.instance)

            plugins.each { pluginPath ->
                def startLevel = '4'
                def autoStart = 'false'
                def artefact = ''
                def version = ''

                File plugin = new File("${pluginFolderName}/${pluginPath}")

                if(!plugin.getName().contains(".source")) {

                    FileTree tree

                    if (plugin.isFile()) {
                        tree = project.zipTree(plugin)
                    }
                    else {
                        tree = project.fileTree(dir: plugin)
                    }

                    tree.each { file ->
                        if(file.getName().equals("MANIFEST.MF")) {
                            file.withInputStream {
                                def mani = new Manifest(it)
                                version = mani.getMainAttributes().getValue('Bundle-Version')
                                artefact = mani.getMainAttributes().getValue('Bundle-SymbolicName')
                                if(artefact == null) {
                                    artefact = mani.getMainAttributes().getValue('Bundle-Name')
                                }
                            }

                            if(artefact != null && ! artefact.contains("_ID")) {
                                int index = artefact.indexOf(";")
                                if(index > 0) {
                                    artefact = artefact.substring(0,index)
                                }

                                if(artefact in autoStarts) {
                                    autoStart = 'true'
                                }

                                if(startLevels.containsKey(artefact)) {
                                    startLevel = startLevels.get(artefact)
                                }

                                bundles.setProperty(artefact, version)
//                                  if(artefact in needed || artefact in whitelist || !artefact.startsWith('org.eclipse')) {
                                if(!(artefact in blackList)) {
                                    out.println "${artefact},${version},plugins/${plugin.getName()},${startLevel},${autoStart}"
                                }
                            }
                        }
                    }
                }
            }
        }

        return bundles
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

        def projectPattern = project.pluginTest.projectPattern
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
