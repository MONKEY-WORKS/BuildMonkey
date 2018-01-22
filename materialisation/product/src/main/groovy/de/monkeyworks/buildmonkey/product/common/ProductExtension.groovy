/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.product.common

import org.gradle.api.Project

/**
 * Created by jake on 06/03/2017.
 */
class ProductExtension {

    String productFile

    String repository

    String executableFeature

    String productID

    // project
    final Project project

    // default constructor
    ProductExtension(Project project) {
        this.project = project
    }
}
