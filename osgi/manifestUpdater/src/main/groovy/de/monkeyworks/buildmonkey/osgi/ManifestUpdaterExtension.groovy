/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.osgi

/**
 * Created by jake on 02.06.17.
 */
class ManifestUpdaterExtension {

    String version

    boolean addQualifier = true

    Closure dependencyUpdates


    void depdencyUpdate(Closure closure) {
        dependencyUpdates = closure
    }
}
