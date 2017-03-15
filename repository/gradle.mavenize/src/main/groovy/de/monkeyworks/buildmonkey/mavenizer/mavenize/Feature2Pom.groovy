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
        def id

        if (bundleFileOrDirectory.isDirectory()) {
            featurexmlStream = new FileInputStream(new File(bundleFileOrDirectory, 'feature.xml'))
            pom.packaging = 'dir'
        } else {
            def jarFile = new JarFile(bundleFileOrDirectory)
            featurexmlStream = jarFile.getInputStream(jarFile.getJarEntry("feature.xml"))
        }

        def parsedXML = new XmlSlurper().parse(featurexmlStream)
        featurexmlStream.close()
        id = parsedXML['@id']

        pom.artifact = id

        pom.group = group ?: pom.artifact
        def version = new Version(parsedXML['@version'].toString())
        pom.version = "${version.major}.${version.minor}.${version.release}"

        parseDependencies(pom.dependencyBundles, parsedXML)

        return pom
    }

    private void parseDependencies(List<DependencyBundle> depBundles, GPathResult xml) {
        def plugins = xml.'**'.findAll{ node-> node.name() == 'plugin'}
        plugins.forEach { plugin ->
            DependencyBundle bundle = new DependencyBundle(name: plugin['@id'], resolution: Constants.RESOLUTION_MANDATORY, visibility: Constants.VISIBILITY_PRIVATE, version: "[1.0,)")
            depBundles.add(bundle)
        }
        def required = xml.'**'.findAll{node -> node.name() == 'import'}
        required.forEach { it ->
            DependencyBundle bundle = new DependencyBundle(name: it['@plugin'], resolution: Constants.RESOLUTION_MANDATORY, visibility: Constants.VISIBILITY_PRIVATE, version: "[1.0,)")
            depBundles.add(bundle)
        }
        def includes = xml.'**'.findAll{node -> node.name() == 'includes'}
        includes.forEach { it ->
            DependencyBundle bundle = new DependencyBundle(name: it['@id'], resolution: Constants.RESOLUTION_MANDATORY, visibility: Constants.VISIBILITY_PRIVATE, version: "[1.0,)")
            depBundles.add(bundle)
        }
    }
}
