/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.p2.deployer.util

import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Helper to create a eclipse feature.jar for all related projects
 *
 * Created by Johannes Tandler on 28.01.17.
 */
class FeatureHelper {

    static void createFeatureXml(String featureId, String label, String version, String provider, Set<Jar> projects, OutputStream target) {
        Writer w = new OutputStreamWriter(target, 'UTF-8')
        createFeatureXml(featureId, label, version, provider, projects, w)
    }

    static void createFeatureXml(String featureId, String label, String version, String provider, Set<Jar> projects, Writer target) {
        def xml = new groovy.xml.MarkupBuilder(target)
        xml.setDoubleQuotes(true)
        xml.mkp.xmlDeclaration(version:'1.0', encoding: 'UTF-8')

        xml.feature(id: featureId, label: label, version: version, 'provider-name': provider) {
            for (Jar project : projects) {
                plugin(
                    id: project.baseName,
                    'download-size': 0,
                    'install-size': 0,
                    version: project.version,
                    unpack: false
                )
            }
        }
    }

    static void createJar(String featureId, String label, String version, String provider, Set<Jar> projects, File jarFile) {
        File target = jarFile
        target.parentFile.mkdirs()

        if(!target.exists())
            target.createNewFile()

        target.withOutputStream {
            ZipOutputStream zipStream = new ZipOutputStream(it)
            zipStream.putNextEntry(new ZipEntry('feature.xml'))
            createFeatureXml(featureId, label, version, provider, projects, zipStream)
            zipStream.closeEntry()
            zipStream.close()
        }
    }

}
