/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.projectsetup

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication

class FixDependencyVersion implements Plugin<Project> {

    @Override
    public void apply(Project project) {
    	
    	project.publishing.publications.each {
    		if(it instanceof DefaultMavenPublication) {
	    		DefaultMavenPublication publication = it as DefaultMavenPublication
	    		println publication.pom
		    	publication.pom.withXml {
		            def node = asNode()
		            def dependenciesNode = node.dependencies

		            dependenciesNode.each { dependencyListNode ->

		                def blackList = []

		                dependencyListNode.each { dependencyNode ->
		                    def groupId = dependencyNode.groupId.text()
		                    def artifactId = dependencyNode.artifactId.text()
		                    def scope = dependencyNode.scope.text()

		                    if(dependencyNode.version.size() > 0) {

		                        def versionNode = dependencyNode.version.get(0)
		                        def version = versionNode.value()

		                        if(version.toString().contains('+')) {
		                            def matches = project
		                                .configurations."${scope}"
		                                .resolvedConfiguration.resolvedArtifacts.findAll{
		                                    def dep = it.moduleVersion.id
		                                    return dep.group == groupId && dep.name == artifactId
		                            }
		                            if(matches.size() > 0) {
		                                def newVersion = matches.first().moduleVersion.id.version
		                                versionNode.setValue(newVersion)
		                            }
		                        }
		                    }
		                    else {
		                        println "No version node found for ${groupId}:${artifactId}"
		                        blackList.add(dependencyNode)
		                    }
		                }
		                blackList.each {
		                    dependencyListNode.remove(it)
		                }
		            }
		        }
		    }
		}
    }
}