/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.mavenizer

import de.monkeyworks.buildmonkey.mavenizer.mavenize.BundleMavenDeployer
import org.gradle.api.Plugin
import org.gradle.api.Project


class MavenizeTaskExtension {
    String sourceP2Repository
    String targetDir
    String groupId
    boolean useP2MirrorOutput

    Project project

    MavenizeTaskExtension(Project project) {
        groupId = "eclipse"
        targetDir = "build/m2-repository"
        sourceP2Repository = ""
        useP2MirrorOutput = false
        this.project = project
    }
}

class MavenizeExtension {
    def mavenizeTasks = []
    def project

    MavenizeExtension(Project project) {
        this.project = project
    }

    void mavenizeTask(Closure closure) {
        def task = new MavenizeTaskExtension()
        project.configure(task, closure)
        mavenizeTasks.add(task)
    }
}

class MavenizerPlugin implements Plugin<Project> {

	static final String TASK_NAME_CONVERT_P2_M2 = "mavenizeP2Repository"
	// Must be a common constant
	static final String TASK_NAME_MIRROR_P2 = "mirrorP2Repository"

	@Override
    public void apply(Project project) {
        project.extensions.create("mavenize", MavenizeExtension, project)

        def name = TASK_NAME_CONVERT_P2_M2
        def rootTask = project.tasks.create(name)

        project.afterEvaluate {
            MavenizeExtension config = project.mavenize
            // create clan task if necessary
            if (project.tasks.findByPath('clean') == null) {project.tasks.create('clean')}

            def i = 1
            for(MavenizeTaskExtension task : config.mavenizeTasks) {
                def cleanTask = project.task("cleanM2Repository_$i") {
                    doFirst {
                        MavenizeExtension parameter = project.mavenize
                        new File(task.targetDir).deleteDir()
                    }
                }
                project.tasks.clean.dependsOn cleanTask

                project.task(name + "_$i") {
                    description = "Converts created p2 repository into m2 repository"

                    if(project.tasks.findByPath(TASK_NAME_MIRROR_P2) != null) {
                        task.sourceP2Repository = "$project.buildDir/p2-repository"
                    }

                    doLast {
                        def converter = new BundleMavenDeployer(project.ant, task.groupId, project.logger)
                        converter.deploy(new File(task.sourceP2Repository), new File(task.targetDir))
                    }
                }
                rootTask.dependsOn(name + "_$i")

                i++
            }
        }
    }
}