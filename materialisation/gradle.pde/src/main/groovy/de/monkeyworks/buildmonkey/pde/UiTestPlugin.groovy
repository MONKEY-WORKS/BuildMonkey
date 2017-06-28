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

import de.monkeyworks.buildmonkey.pde.testing.UiTestExecuter
import de.monkeyworks.buildmonkey.pde.testing.UiTestExtension
import de.monkeyworks.buildmonkey.pde.tools.FileHelper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.testing.Test

import javax.inject.Inject

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
    static final TASK_NAME_UNZIP_TESTS = 'unzipUITest'
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
        addTaskCreateUnzipTests(project)
        addTaskCreateUiTest(project)
    }

    static void configureProject(Project project) {
        project.extensions.create(DSL_EXTENSION_NAME, UiTestExtension)
        project.getPlugins().apply(de.monkeyworks.buildmonkey.pde.BundlePlugin)
    }

    static void validateDslBeforeBuildStarts(Project project) {
        project.gradle.taskGraph.whenReady {
            // the application must be defined
            assert project.uiTest.applicationName != null
            assert project.uiTest.testClass != null
            def bundleName = project.uiTest.testBundle
            assert bundleName != null
        }
    }

    static void addTaskCreateUnzipTests(Project project) {
        Config config = Config.on(project)
        def unzipTests = project.task(TASK_NAME_UNZIP_TESTS )  {
            def testClassTarget = new File (project.getBuildDir(), "classes/test")
            def bundles

            doFirst {
                def bundleName = project.uiTest.testBundle
                def helper = new FileHelper()
                def pluginFolder = helper.findSubFolder(new File(project.rootProject.buildDir, 'application/application'), "plugins")
                assert pluginFolder != null
                bundles = new FileNameFinder().getFileNames(pluginFolder.absolutePath, "${bundleName}_*.jar")
                assert bundles != null
                assert bundles.size() > 0
                project.copy {
                    from project.zipTree(bundles[0])
                    into project.file(testClassTarget)
                }
            }
        }

        unzipTests.dependsOn(":${UITestDefinitionPlugin.TASK_NAME_INSTALL_TARGET_PLATFORM}")
    }

    static void addTaskCreateUiTest(Project project) {
        Config config = Config.on(project)
        def uiTest = project.task(TASK_NAME_UI_TEST, type: Test) {
            group = "PDE test plugin"
            description = 'Installs the SWTBot and the tests into a RCP application and runs them.'

            // configure the test runner to execute all classes from the project
            testExecuter = new UiTestExecuter(project)

            testClassesDir = new File("${project.buildDir}/")
            classpath = project.files("${project.buildDir}/classes/test")
            reports.html.destination = new File("${project.reporting.baseDir}/uiTest")

            // set some system properties for the test Eclipse
            systemProperty('osgi.requiredJavaVersion','1.8')
            systemProperty('eclipse.pde.launch','true')
            systemProperty('eclipse.p2.data.area','@config.dir/../p2')

            // set the task outputs
            def testDistributionDir = project.file("$project.buildDir/uiTest/application/")
            outputs.dir testDistributionDir

            doFirst {
                beforeUITest(project, config, testDistributionDir)
            }

            dependsOn  "${TASK_NAME_UNZIP_TESTS}"
        }
    }


    static void beforeUITest(Project project, Config config, File testDistributionDir) {
        // before testing, create a fresh eclipse IDE with all dependent plugins installed
        // first delete the test eclipse distribution and the original plugins.
        testDistributionDir.deleteDir()
        def workspace = new File (project.getBuildDir(), "/uiTest/workspace")
        if(workspace.exists()) {
            workspace.deleteDir()
        }

        // copy the target platform to the test distribution folder
        copyTargetPlatformToBuildFolder(project, config, testDistributionDir)

    }


    static void copyTargetPlatformToBuildFolder(Project project, Config config,  File distro) {
        project.copy {
            from project.file("$project.rootProject.buildDir/application/application")
            into distro
        }
    }

}

