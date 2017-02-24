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

    // eclipse home
    File eclipseHome

    // target repository dir
    File targetRepository

    // project
    final Project project

    // default constructor
    P2DeploymentExtension(Project project) {
        this.project = project
        targetRepository = new File(project.getBuildDir(), "p2-repo")
    }
}
