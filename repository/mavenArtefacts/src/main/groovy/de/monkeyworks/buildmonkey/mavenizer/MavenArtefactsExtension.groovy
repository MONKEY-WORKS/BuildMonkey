/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.mavenizer

import org.gradle.api.Project

/**
 * Created by micha on 13.03.17.
 */
class MavenArtefactsExtension {
    def artefacts = []
    def prefix
    def updateSiteDir
    def targetDir
    def uploadThreads

    def featureId
    def featureName
    def featureVersion

    def repositoryServerURL
    def repositoryUser
    def repositoryPassword

    def mavenRepositoryServerURL
    def mavenRepositoryName
    def mavenRepositoryUser
    def mavenRepositoryPassword

    def p2RepositoryServerURL
    def p2RepositoryName
    def p2RepositoryUser
    def p2RepositoryPassword

    private final Project project

    MavenArtefactsExtension(Project project) {
        this.project = project

        uploadThreads = '4'
    }

}
