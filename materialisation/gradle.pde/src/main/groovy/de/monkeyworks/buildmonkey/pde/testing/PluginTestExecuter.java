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

package de.monkeyworks.buildmonkey.pde.testing;

import de.monkeyworks.buildmonkey.pde.Config;
import de.monkeyworks.buildmonkey.pde.PluginTestPlugin;

import org.eclipse.jdt.internal.junit.model.ITestRunListener2;
import org.eclipse.jdt.internal.junit.model.RemoteTestRunnerClient;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.testing.*;
import org.gradle.api.internal.tasks.testing.detection.TestExecuter;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.processors.TestMainAction;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.time.TrueTimeProvider;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.DefaultJavaExecAction;
import org.gradle.process.internal.JavaExecAction;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public final class PluginTestExecuter implements TestExecuter {

    private static final Logger LOGGER = Logging.getLogger(PluginTestExecuter.class);

    private final Project project;

    private final Config config;

    public PluginTestExecuter(Project project) {
        this.project = project;
        config = Config.on(project);
    }

    @Override
    public void execute(Test test, TestResultProcessor testResultProcessor) {
        LOGGER.info("Executing tests in Eclipse");

        int pluginTestPort = locatePluginTestPortNumber();
        if (pluginTestPort == -1) {
            throw new GradleException("Cannot allocate port for PDE test run");
        }
        LOGGER.info("Will use port {0} to communicate with Eclipse.", pluginTestPort);

        runPluginTestsInEclipse(test, testResultProcessor, pluginTestPort);
    }

    private PluginTestExtension getExtension(Test testTask) {
        return (PluginTestExtension) testTask.getProject().getExtensions().findByName("pluginTest");
    }

    private void runPluginTestsInEclipse(final Test testTask, final TestResultProcessor testResultProcessor,
            final int pluginTestPort) {
        ExecutorService threadPool = Executors.newFixedThreadPool(2);
        File runDir = new File(testTask.getProject().getBuildDir(), testTask.getName());

        File testEclipseDir = new File(this.project.property("buildDir") + "/pluginTest/eclipse");

        // File configIniFile = getInputs().getFiles().getSingleFile();
        File configIniFile = new File(testEclipseDir, "configuration/config.ini");
        assert configIniFile.exists();

        File runPluginsDir = new File(testEclipseDir, "plugins");
        LOGGER.info("Eclipse test directory is {}", runPluginsDir.getPath());
        File equinoxLauncherFile = getEquinoxLauncherFile(testEclipseDir);
        LOGGER.info("equinox launcher file {}", equinoxLauncherFile);

        PluginTestExtension extension = getExtension(testTask);
        final JavaExecAction javaExecHandleBuilder;

        if(extension.getApplicationName().endsWith("coretestapplication")) {
            javaExecHandleBuilder = getPluginTestJavaExecAction(testTask, pluginTestPort, configIniFile, equinoxLauncherFile);
        }
        else if(extension.getApplicationName().endsWith("swtbottestapplication")) {
            javaExecHandleBuilder = getSWTBOTTestJavaExecAction(testTask, pluginTestPort, configIniFile, equinoxLauncherFile);
        }
        else {
            throw new GradleException("Unknown application type " + extension.getApplicationName());
        }

        final CountDownLatch latch = new CountDownLatch(1);
        Future<?> eclipseJob = threadPool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    ExecResult execResult = javaExecHandleBuilder.execute();
                    execResult.assertNormalExitValue();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                finally {
                    latch.countDown();
                }
            }
        });
        // TODO
        final String suiteName = this.project.getName();
        Future<?> testCollectorJob = threadPool.submit(new Runnable() {
            @Override
            public void run() {
                PluginTestListener pluginTestListener = new PluginTestListener(testResultProcessor, suiteName, this);
                new PluginTestRunnerClient().startListening(new ITestRunListener2[] { pluginTestListener }, pluginTestPort);
                LOGGER.info("Listening on port " + pluginTestPort + " for test suite " + suiteName + " results ...");
                synchronized (this) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    } finally {
                        latch.countDown();
                    }
                }
            }
        });
        try {
            latch.await(getExtension(testTask).getTestTimeoutSeconds(), TimeUnit.SECONDS);
            // short chance to do cleanup
            eclipseJob.get(15, TimeUnit.SECONDS);
            testCollectorJob.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new GradleException("Test execution failed", e);
        }
    }

    private JavaExecAction getPluginTestJavaExecAction(Test testTask, int pluginTestPort, File configIniFile, File equinoxLauncherFile) {
        final JavaExecAction javaExecHandleBuilder = new DefaultJavaExecAction(getFileResolver(testTask));
        javaExecHandleBuilder.setClasspath(this.project.files(equinoxLauncherFile));
        javaExecHandleBuilder.setMain("org.eclipse.equinox.launcher.Main");
        List<String> programArgs = new ArrayList<String>();

        programArgs.add("-os");
        programArgs.add(Config.getOs());
        programArgs.add("-ws");
        programArgs.add(Config.getWs());
        programArgs.add("-arch");
        programArgs.add(Config.getArch());

        if (getExtension(testTask).isConsoleLog()) {
            programArgs.add("-consoleLog");
        }
        File optionsFile = getExtension(testTask).getOptionsFile();
        if (optionsFile != null) {
            programArgs.add("-debug");
            programArgs.add(optionsFile.getAbsolutePath());
        }
        programArgs.add("-version");
        programArgs.add("4");
        programArgs.add("-port");
        programArgs.add(Integer.toString(pluginTestPort));
        programArgs.add("-testLoaderClass");
        programArgs.add("org.eclipse.jdt.internal.junit4.runner.JUnit4TestLoader");
        programArgs.add("-loaderpluginname");
        programArgs.add("org.eclipse.jdt.junit4.runtime");
        programArgs.add("-classNames");
        for (String clzName : collectTestNames(testTask)) {
            programArgs.add(clzName);
        }
        programArgs.add("-application");
        programArgs.add(getExtension(testTask).getApplicationName());
        programArgs.add("-product org.eclipse.platform.ide");
        // alternatively can use URI for -data and -configuration (file:///path/to/dir/)
        programArgs.add("-data");
        programArgs.add(config.getWorkspace().getAbsolutePath());
        programArgs.add("-configuration");
        programArgs.add(configIniFile.getParentFile().getAbsolutePath());

        programArgs.add("-testpluginname");
        String fragmentHost = getExtension(testTask).getFragmentHost();
        if (fragmentHost != null) {
            programArgs.add(fragmentHost);
        } else {
            programArgs.add(this.project.getName());
        }

        javaExecHandleBuilder.setArgs(programArgs);
        javaExecHandleBuilder.setSystemProperties(testTask.getSystemProperties());
        javaExecHandleBuilder.setEnvironment(testTask.getEnvironment());

        // TODO this should be specified when creating the task (to allow override in build script)
        List<String> jvmArgs = new ArrayList<String>();
        jvmArgs.add("-XX:MaxPermSize=256m");
        jvmArgs.add("-Xms40m");
        jvmArgs.add("-Xmx1024m");

        if(getExtension(testTask).isDebug()) {
            jvmArgs.add("-Xdebug");
            jvmArgs.add("-Xrunjdwp:transport=dt_socket,address=" + getExtension(testTask).getDebugPort() + ",server=y");
        }

        if (Config.getOs().equals("macosx")) {
            jvmArgs.add("-XstartOnFirstThread");
        }

        javaExecHandleBuilder.setJvmArgs(jvmArgs);
        javaExecHandleBuilder.setWorkingDir(this.project.getBuildDir());
        return javaExecHandleBuilder;
    }

    private JavaExecAction getSWTBOTTestJavaExecAction(Test testTask, int pluginTestPort, File configIniFile, File equinoxLauncherFile) {
        final JavaExecAction javaExecHandleBuilder = new DefaultJavaExecAction(getFileResolver(testTask));
        javaExecHandleBuilder.setClasspath(this.project.files(equinoxLauncherFile));
        javaExecHandleBuilder.setMain("org.eclipse.equinox.launcher.Main");
        List<String> programArgs = new ArrayList<String>();

        programArgs.add("-application");
        programArgs.add(getExtension(testTask).getApplicationName());


        programArgs.add("-os");
        programArgs.add(Config.getOs());
        programArgs.add("-ws");
        programArgs.add(Config.getWs());
        programArgs.add("-arch");
        programArgs.add(Config.getArch());

        if (getExtension(testTask).isConsoleLog()) {
            programArgs.add("-consoleLog");
        }
        File optionsFile = getExtension(testTask).getOptionsFile();
        if (optionsFile != null) {
            programArgs.add("-debug");
            programArgs.add(optionsFile.getAbsolutePath());
        }

        programArgs.add("-version");
        programArgs.add("4");
        programArgs.add("-port");
        programArgs.add(Integer.toString(pluginTestPort));
        programArgs.add("-testLoaderClass");
        programArgs.add("org.eclipse.jdt.internal.junit4.runner.JUnit4TestLoader");
        programArgs.add("-loaderpluginname");
        programArgs.add("org.eclipse.jdt.junit4.runtime");


        programArgs.add("-product");
        programArgs.add(getExtension(testTask).getProduct());
        programArgs.add("-testApplication");
        programArgs.add(getExtension(testTask).getApplication());

        programArgs.add("-classNames");
        for (String clzName : collectTestNames(testTask)) {
            programArgs.add(clzName);
        }
        programArgs.add("-data");
        programArgs.add(config.getWorkspace().getAbsolutePath());
        programArgs.add("-testPluginName");
        programArgs.add(this.project.getName());

        javaExecHandleBuilder.setArgs(programArgs);
        javaExecHandleBuilder.setSystemProperties(testTask.getSystemProperties());
        javaExecHandleBuilder.setEnvironment(testTask.getEnvironment());

        // TODO this should be specified when creating the task (to allow override in build script)
        List<String> jvmArgs = new ArrayList<String>();
        jvmArgs.add("-XX:MaxPermSize=256m");
        jvmArgs.add("-Xms40m");
        jvmArgs.add("-Xmx1024m");

        if(getExtension(testTask).isDebug()) {
            jvmArgs.add("-Xdebug");
            jvmArgs.add("-Xrunjdwp:transport=dt_socket,address=" + getExtension(testTask).getDebugPort() + ",server=y");
        }

        if (Config.getOs().equals("macosx")) {
            jvmArgs.add("-XstartOnFirstThread");
        }

        jvmArgs.add("-Dosgi.framework.extensions=org.eclipse.fx.osgi");

        javaExecHandleBuilder.setJvmArgs(jvmArgs);
        javaExecHandleBuilder.setWorkingDir(this.project.getBuildDir());
        return javaExecHandleBuilder;
    }

    private File getEquinoxLauncherFile(File testEclipseDir) {
         File[] plugins = new File(testEclipseDir, "plugins").listFiles();
         for (File plugin : plugins) {
             if (plugin.getName().startsWith("org.eclipse.equinox.launcher_")) {
                 return plugin;
             }
         }
        return null;
    }

    private FileResolver getFileResolver(Test testTask) {
        return testTask.getProject().getPlugins().findPlugin(PluginTestPlugin.class).fileResolver;
    }

    private List<String> collectTestNames(Test testTask) {
        ClassNameCollectingProcessor processor = new ClassNameCollectingProcessor();
        Runnable detector;
        final FileTree testClassFiles = testTask.getCandidateClassFiles();
        if (testTask.isScanForTestClasses()) {
            TestFrameworkDetector testFrameworkDetector = testTask.getTestFramework().getDetector();
            testFrameworkDetector.setTestClassesDirectory(testTask.getTestClassesDir());
            testFrameworkDetector.setTestClasspath(testTask.getClasspath().getFiles());
            detector = new PluginTestClassScanner(testClassFiles, processor, this.getExtension(testTask).getTestClassClosure());
        } else {
            detector = new PluginTestClassScanner(testClassFiles, processor, this.getExtension(testTask).getTestClassClosure());
        }

        new TestMainAction(detector, processor, new NoOpTestResultProcessor(), new TrueTimeProvider(), testTask.toString(), testTask.getPath(), String.format("Gradle Eclipse Test Run %s", testTask.getPath())).run();
        LOGGER.info("collected test class names: {}", processor.classNames);
        System.out.println("collected test class names: ");
        for(String name : processor.classNames) {
            System.out.print("   ");
            System.out.println(name);
        }
        return processor.classNames;
    }

    public int locatePluginTestPortNumber() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            return socket.getLocalPort();
        } catch (IOException e) {
            // ignore
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return -1;
    }
    
    public static final class NoOpTestResultProcessor implements TestResultProcessor {

        @Override
        public void started(TestDescriptorInternal testDescriptorInternal, TestStartEvent testStartEvent) {
        }

        @Override
        public void completed(Object o, TestCompleteEvent testCompleteEvent) {
        }

        @Override
        public void output(Object o, TestOutputEvent testOutputEvent) {
        }

        @Override
        public void failure(Object o, Throwable throwable) {
        }
    }

    private class ClassNameCollectingProcessor implements TestClassProcessor {
        public List<String> classNames = new ArrayList<String>();

        @Override
        public void startProcessing(TestResultProcessor testResultProcessor) {
            // no-op
        }

        @Override
        public void processTestClass(TestClassRunInfo testClassRunInfo) {
            this.classNames.add(testClassRunInfo.getTestClassName());
        }

        @Override
        public void stop() {
            // no-op
        }
    }
}
