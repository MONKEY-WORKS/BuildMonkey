/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.internal.os.OperatingSystem

class ArtifactoryUploadsExtension {
    List<ArtifactoryUploadExtension> uploads = []
    Project project

    ArtifactoryUploadsExtension(Project project) {
        this.project = project
    }

    void artifactoryUpload(Closure closure) {
        def asg = new ArtifactoryUploadExtension(project)
        project.configure(asg, closure)
        uploads.add(asg)
    }
}

class ArtifactoryUploadExtension {
    String sourceDirectory

    String artifactoryURL

    String repositoryPath

    ArtifactoryCredentials credentials
    
    Project project

    String threads

    ArtifactoryUploadExtension(Project project) {
        this.project = project
        threads = '4'
    }

    void credentials(Closure closure) {
        def asg = new ArtifactoryCredentials()
        project.configure(asg, closure)
        credentials = (asg)
    }
}

class ArtifactoryCredentials {
    def username
    def password
}



class ArtifactoryUploadPlugin implements Plugin<Project> {

	static final String DSL_EXTENSION_NAME = "artifactoryUploads"
	static final String TASK_NAME_EXTRACT_TOOLING = "extractArtifactoryTooling"
	static final String TASK_NAME_UPLOAD = "artifactoryUpload"
    static final String TASK_NAME_CLEAR_REPOSITORY = "artifactoryClearRepository"

    File toolDir

	@Override
    void apply(Project project) {
        project.extensions.create(DSL_EXTENSION_NAME, ArtifactoryUploadsExtension, project)

        toolDir = project.buildDir.toPath().resolve("tools").toFile()

        addTaskToExtractTooling(project)
        addTasksForUploadJobs(project)
        //addTaskToClearRepositoryPath(project)
    }

    static def getUploadTool() {
        def os = org.gradle.internal.os.OperatingSystem.current()
        def tool = ""
        if (os.windows) {
            tool = "jfrog-windows.exe"
        } else if (os.macOsX) {
            tool = "jfrog"
        } else if (os.linux) {
            tool = "jfrog-linux"
        }
        return tool
    }

    def toolDirectory() {
        return toolDir
    }

    def toolPath() {
        return toolDirectory().toPath().resolve("jfrog").toFile()
    }

    void addTaskToExtractTooling(Project project) {
        ClassLoader clazzloader = this.getClass().getClassLoader()

        project.task(TASK_NAME_EXTRACT_TOOLING) {
            description = "Extracts tooling from plugin"
            outputs.file toolPath()

            String toolName = getUploadTool()
            println "executables/$toolName"

            doLast {
                InputStream stream = clazzloader.getResourceAsStream("executables/$toolName")

                if(!toolDirectory().exists()) {
                    toolDirectory().mkdirs()
                }

                def fos = new FileOutputStream(toolPath())
                
                fos << stream
                fos.flush()
                fos.close()


                def os = org.gradle.internal.os.OperatingSystem.current()
                if(!os.windows) {
                    project.exec {
                        commandLine(
                            "chmod", "+x", toolPath()
                        )
                    }
                }
            }
        }
    }

    void addTasksForUploadJobs(Project project) {
        def name = TASK_NAME_UPLOAD

        project.tasks.create("$name")

        project.afterEvaluate {
            ArtifactoryUploadsExtension uploads = project.artifactoryUploads

            for(int i=0;i<uploads.uploads.size();i++) {
                def upload = uploads.uploads.get(i)

                project.task("${name}_${i}", dependsOn: [TASK_NAME_EXTRACT_TOOLING]) {
                    def tool = toolPath()
                    ArtifactoryUploadExtension parameters = upload
                    def credentials = parameters.credentials

                    doLast {
                        project.exec {
                            ignoreExitValue = true
                            commandLine(
                                    "$tool", "rt", "u", parameters.sourceDirectory + "/(*)", parameters.repositoryPath + '/{1}',
                                    "--url=$parameters.artifactoryURL", "--user=$credentials.username", "--password=$credentials.password", "--threads=${parameters.threads}"
                            )
                        }
                    }
                }

                project.tasks."${name}".dependsOn "${name}_${i}"
            }
        }
    }

    void addTaskToClearRepositoryPath(Project project) {

        project.task(TASK_NAME_CLEAR_REPOSITORY, dependsOn: [TASK_NAME_EXTRACT_TOOLING]) {
            def tool = toolPath()
            ArtifactoryUploadExtension parameters = project.artifactoryUpload
            def credentials = parameters.credentials

            doLast {
                project.exec {
                    ignoreExitValue = true
                    commandLine(
                            "$tool", "rt", "del", parameters.repositoryPath,
                            "--url=$parameters.artifactoryURL", "--user=$credentials.username", "--password=$credentials.password", "--threads=4"
                    )
                }
            }
        }
    }
}