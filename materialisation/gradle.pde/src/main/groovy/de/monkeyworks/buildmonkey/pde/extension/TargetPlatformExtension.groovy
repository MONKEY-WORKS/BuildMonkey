/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.pde.extension

import org.gradle.api.Project

class TargetPlatformExtension {
    File targetDefinition
    Project project

    TargetPlatformExtension(Project project) {
        this.project = project
    }
}
