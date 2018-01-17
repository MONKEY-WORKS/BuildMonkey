package de.monkeyworks.buildmonkey.pde.extension

import org.gradle.api.Project

class ProductExtension {

    String productFile

    String productID

    String eclipseUpdateSite = "http://download.eclipse.org/eclipse/updates/4.7.1/"

    // project
    final Project project

    // default constructor
    ProductExtension(Project project) {
        this.project = project
    }

}
