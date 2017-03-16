/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.mavenizer.mavenize

import groovy.util.slurpersupport.GPathResult
import org.osgi.framework.Constants

import java.util.jar.JarFile

/**
 * Created by jake on 14/03/2017.
 */
class Feature2Pom {
    String group
    String dependencyGroup


    Feature2Pom(String group = null, String dependencyGroup = null) {
        this.group = group
        this.dependencyGroup = dependencyGroup
    }

    /**
     * Converts bundle to POM.
     * @param bundleFileOrDirectory - jar-file or directory containing OSGi bundle.
     * @return the converted POM.
     */
    Pom convert(File bundleFileOrDirectory) {
        def pom = new Pom()
        def featurexmlStream

        if (bundleFileOrDirectory.isDirectory()) {
            featurexmlStream = new FileInputStream(new File(bundleFileOrDirectory, 'feature.xml'))
            pom.packaging = 'dir'
        } else {
            def jarFile = new JarFile(bundleFileOrDirectory)
            featurexmlStream = jarFile.getInputStream(jarFile.getJarEntry("feature.xml"))
        }

        def parsedXML = new XmlSlurper().parse(featurexmlStream)
        featurexmlStream.close()

        pom.artifact = parsedXML['@id']

        pom.group = group ?: pom.artifact
        def version = new Version(parsedXML['@version'].toString())
        pom.version = "${version.major}.${version.minor}.${version.release}"

        parseDependencies(pom.dependencyBundles, parsedXML)

        return pom
    }

    /**
     *
     * This method extracts dependencies from a given feature xml using a node name for identifying elements that hold a dependency
     * and an attribute which holds the artifact name
     *
     * @param depBundles
     * @param xml
     */
    private void parseDependencies(List<DependencyBundle> depBundles, GPathResult xml) {
        def extractDependenciesFromXML = { nodeName, attribute ->
            xml.'**'.findAll{ node-> node.name() == nodeName}.forEach { plugin ->
                depBundles.add(new DependencyBundle(name: plugin["@${attribute}"], resolution: Constants.RESOLUTION_MANDATORY, visibility: Constants.VISIBILITY_PRIVATE, version: "[1.0,)"))
            }
        }

        extractDependenciesFromXML("plugin", "id")
        extractDependenciesFromXML("import", "plugin")
        extractDependenciesFromXML("includes", "id")
    }
}
