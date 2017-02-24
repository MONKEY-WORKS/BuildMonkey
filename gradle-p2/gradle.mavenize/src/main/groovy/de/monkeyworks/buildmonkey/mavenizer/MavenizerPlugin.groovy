package de.monkeyworks.buildmonkey.mavenizer

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.internal.os.OperatingSystem
import de.monkeyworks.buildmonkey.mavenizer.tools.DownloadEclipseSdkTask
import de.monkeyworks.buildmonkey.mavenizer.mavenize.BundleMavenDeployer
import groovy.xml.MarkupBuilder

class MavenizerPlugin implements Plugin<Project> {

	static final String TASK_NAME_CONVERT_P2_M2 = "mavenizeP2Repository"
	// Must be a common constant
	static final String TASK_NAME_MIRROR_P2 = "mirrorP2Repository"

	static class MavenizeExtension {
		def sourceP2Repository
		def targetDir

		def groupId

	    MavenizeExtension() {
	    	groupId = "eclipse"
	    	targetDir = "build/m2-repository"
	    	sourceP2Repository = ""
	    }
	}

	@Override
    public void apply(Project project) {
        project.extensions.create("mavenize", MavenizeExtension)

        // if eclipse plugin is configured, use it as source path 
        project.afterEvaluate {
			if(project.tasks.findByPath(TASK_NAME_MIRROR_P2) != null) {
				project.mavenize.sourceP2Repository = "$project.buildDir/p2-repository" 					
			}
		}

        addTaskToConvertP2ToMaven(project)
    }

    static void addTaskToConvertP2ToMaven(Project project) {
        project.task('cleanM2Repository') {
            doFirst {
				MavenizeExtension parameter = project.mavenize
                new File(parameter.targetDir).deleteDir()
            }
        }
        if (project.tasks.findByPath('clean') == null) {project.tasks.create('clean')}
        project.tasks.clean.dependsOn 'cleanM2Repository'

    	project.task(TASK_NAME_CONVERT_P2_M2) {
    		description = "Converts created p2 repository into m2 repository"

			MavenizeExtension parameter = project.mavenize
    		
    		project.afterEvaluate {
	    		if(project.tasks.findByPath(TASK_NAME_MIRROR_P2) != null) {
	    			if(project.mavenize.sourceP2Repository.equals("$project.buildDir/p2-repository")) {
						dependsOn TASK_NAME_MIRROR_P2
					}
				}
			}

    		doLast {
    			def converter = new BundleMavenDeployer(project.ant, parameter.groupId, project.logger)
    			converter.deploy(new File(parameter.sourceP2Repository), new File(parameter.targetDir))
    		}
    	}
    }

}