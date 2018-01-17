/**
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.equinox.api.parameter;

public enum PlatformArchitecture {

    WIN_32_64 ("windows", "win32", "win32"),
    LINUX_GTK_64 ("linux", "gtk", "x86_64");

    private final String operatingSystem;
    private final String windowingSystem;
    private final String processorArchitecture;

    PlatformArchitecture(String operatingSystem, String windowingSystem, String processorArchitecture) {
        this.operatingSystem = operatingSystem;
        this.windowingSystem = windowingSystem;
        this.processorArchitecture = processorArchitecture;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public String getWindowingSystem() {
        return windowingSystem;
    }

    public String getProcessorArchitecture() {
        return processorArchitecture;
    }
}
