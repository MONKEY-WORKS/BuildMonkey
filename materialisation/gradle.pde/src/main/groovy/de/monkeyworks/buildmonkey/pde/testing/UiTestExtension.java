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

import groovy.lang.Closure;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.impldep.com.amazonaws.services.kms.model.UnsupportedOperationException;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

public class UiTestExtension {

    private String fragmentHost;

    /**
     * Application launched in Eclipse.
     * {@code org.eclipse.swtbot.eclipse.core.swtbottestapplication} can be used to runUI tests with swt bot.
     */
    private String applicationName = "org.eclipse.swtbot.eclipse.core.swtbottestapplication";

    private String testClass;

    private File optionsFile;

    /** Boolean toggle to control whether to show Eclipse log or not. */
    private boolean consoleLog;

    private boolean debug;

    private int debugPort = 8998;

    private long testTimeoutSeconds = 60 * 60L;

    private List<String> projectPattern;

    private Closure testClassClosure;

    private String application;

    private String product;

    public String getApplicationName() {
        return this.applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public File getOptionsFile() {
        return this.optionsFile;
    }

    public void setOptionsFile(File optionsFile) {
        this.optionsFile = optionsFile;
    }

    public boolean isConsoleLog() {
        return this.consoleLog;
    }

    public void setConsoleLog(boolean consoleLog) {
        this.consoleLog = consoleLog;
    }

    public long getTestTimeoutSeconds() {
        return this.testTimeoutSeconds;
    }

    public void setTestTimeoutSeconds(long testTimeoutSeconds) {
        this.testTimeoutSeconds = testTimeoutSeconds;
    }

    public String getFragmentHost() {
        return this.fragmentHost;
    }

    public void setFragmentHost(String fragmentHost) {
        this.fragmentHost = fragmentHost;
    }

    public boolean isDebug() {
        return this.debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public int getDebugPort() {
        return this.debugPort;
    }

    public void setDebugPort(int debugPort) {
        this.debugPort = debugPort;
    }

    public List<String> getProjectPattern() {
        return this.projectPattern;
    }

    public void setProjectPattern(List<String> pattern) {
        this.projectPattern = pattern;
    }

    public Closure getTestClassClosure() {
        return testClassClosure;
    }

    public void isTestClass(Closure testClassClosure) {
        this.testClassClosure = testClassClosure;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getTestClass() {
        return testClass;
    }

    public void setTestClass(String testClass) {
        this.testClass = testClass;
    }
}
