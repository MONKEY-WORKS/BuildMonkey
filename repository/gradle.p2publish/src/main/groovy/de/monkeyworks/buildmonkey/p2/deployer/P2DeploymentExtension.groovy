/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.p2.deployer

import org.gradle.api.Project

/**
 * Created by Johannes Tandler on 29.01.17.
 */
class P2DeploymentExtension {

    // feature id
    String featureId = "platform.feature"
    
    // feature label
    String featureLabel = "My Feature"
    
    // feature version 
    String version = "1.0.0"

    // target repository dir
    File targetRepository

    // project
    final Project project

    boolean generateFeature

    String qualifier

    boolean generateQualifier

    String sourceRepository

    // default constructor
    P2DeploymentExtension(Project project) {
        this.project = project
        targetRepository = new File(project.getBuildDir(), "p2-repo")
    }
}
