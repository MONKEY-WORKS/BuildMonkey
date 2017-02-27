package de.monkeyworks.buildmonkey.projectsetup

import org.gradle.api.Plugin
import org.gradle.api.Project

class ProjectSetupPlugin implements Plugin<Project> {

    private static final String xtextVersion = '+'

    @Override
    public void apply(Project project) {
    }


    private void applyXtendIfPresent(Project project) {
/*        if (project.file('src/main/xtend').exists() || project.file('src/test/xtend').exists()) {
            project.configure(project) {
                apply plugin: 'org.xtext.xtend'
            }
            project.sourceSets.create("src/main/xtend")
            project.sourceSets.create("src/test/xtend")


            Configuration config = project.configurations.getByName('compile')
            config.dependencies.create("org.eclipse.xtext:org.eclipse.xtext.builder:$xtextVersion")
            config.dependencies.create("org.eclipse.xtext:org.eclipse.xtext.generator:$xtextVersion")

            project.task("cleanXtend", type: Clean) {
                description = "Clean up generated files"

                delete fileTree(dir: xtendGenDir , include: '**//*.java')
	            delete fileTree(dir: xtendTestDir , include: '**//*.java')
            }
            clean.dependsOn "cleanXtend"
        }
        */
    }

    
}