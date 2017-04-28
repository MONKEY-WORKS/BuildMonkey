package de.monkeyworks.buildmonkey.product.publish

import org.gradle.api.Project

/**
 * Created by jake on 06/03/2017.
 */
class ProductExtension {

    String productFile

    String repository

    String executableFeature

    // project
    final Project project

    // default constructor
    ProductExtension(Project project) {
        this.project = project
    }
}
