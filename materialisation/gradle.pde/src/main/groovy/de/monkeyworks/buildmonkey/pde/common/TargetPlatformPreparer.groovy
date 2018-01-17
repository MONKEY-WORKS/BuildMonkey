/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.pde.common

import de.monkeyworks.buildmonkey.equinox.api.P2DirectorService
import de.monkeyworks.buildmonkey.equinox.api.parameter.InstallFeatureParameter
import de.monkeyworks.buildmonkey.equinox.api.parameter.PlatformArchitecture
import de.monkeyworks.buildmonkey.equinox.embedding.EquinoxEmbedder
import de.monkeyworks.buildmonkey.pde.utils.FileSemaphore

class TargetPlatformPreparer {

    private final EquinoxEmbedder embedder
    private final File projectFolder

    TargetPlatformPreparer(EquinoxEmbedder embedder, File projectFolder) {

        this.projectFolder = projectFolder
        this.embedder = embedder
    }

    void prepareTargetPlatform(File targetPlatform, File destinationPath, PlatformArchitecture architecture) {
        // if multiple builds start on the same machine (which is the case with a CI server)
        // we want to prevent them assembling the same target platform at the same time
        def lock = new FileSemaphore(destinationPath)
        try {
            lock.lock()
            prepareTargetPlatformUnprotected(targetPlatform, destinationPath, architecture)
        } finally  {
            lock.unlock()
        }
    }

    private void prepareTargetPlatformUnprotected(File targetPlatform, File destinationPath, PlatformArchitecture architecture) {
        File p2Folder = new File("${destinationPath.path}/p2")
        if(p2Folder.exists()) {
            return
        }

        // collect  update sites and feature names
        def updateSites = []
        def features = []
        def rootNode = new XmlSlurper().parseText(targetPlatform.text)
        rootNode.locations.location.each { location ->
            updateSites.add(location.repository.@location.text().replace('\${project_loc}', 'file://' +  projectFolder.absolutePath))
            location.unit.each {unit ->
                features.add("${unit.@id}/${unit.@version}")
            }
        }

        //println("Assemble target platform in '${destinationPath.absolutePath}'.\n    Update sites: '${updateSites.join(' ')}'\n    Features: '${features.join(' ')}'")

        P2DirectorService service = embedder.getService(P2DirectorService.class)

        InstallFeatureParameter parameter = new InstallFeatureParameter()

        parameter.setRepository(updateSites.join(','))
        parameter.setInstallIU(features.join(','))
        parameter.setBundlepool(destinationPath.path)
        parameter.setDestination(destinationPath.path)
        parameter.setProfile('PluginProfile')
        parameter.setArchitecture(architecture)

        service.installFeaturesToPlatform(parameter)
    }

}
