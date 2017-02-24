package de.monkeyworks.buildmonkey.p2

import de.monkeyworks.buildmonkey.eclipsesdk.DownloadHelper
import groovy.xml.MarkupBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class P2MirrorPlugin implements Plugin<Project> {

    static final String DSL_EXTENSION_NAME = "eclipseMirror"
	static final String TASK_NAME_MIRROR_P2 = "mirrorP2Repository"
	static final String TASK_NAME_CREATE_ANT = "createAntScript"
    static final String TASK_NAME_CREATE_TARGET_FILE = "createTargetPlatform"
    static final String TASK_NAME_CREATE_TARGET_FEATURE = "createTargetFeature"

	static class EclipseMirror {

	    String target
	    def sliceStrict
        def includeFeatures
        def latestVersionOnly
        def targetDefinition
        def updateURL
        def targetFeatureName
        def targetPlatform
        def targetFile

	    EclipseMirror() {
	        sliceStrict = true
            includeFeatures = true
            latestVersionOnly = false
            targetFile = 'p2.target'
	    }
	}

    private static class TargetPlatform {
        def locations

        TargetPlatform() {
            locations = []
        }
    }

    private static class Location {
        def installableUnits
        def url

        Location() {
            url = ""
            installableUnits = []
        }
    }

	private static class InstallableUnit {
		String id
		String version

		InstallableUnit() {}
	}

	@Override
    public void apply(Project project) {
        project.extensions.create(DSL_EXTENSION_NAME, EclipseMirror)

        DownloadHelper.addEclipseConfigurationExtension(project)
        DownloadHelper.addTaskDownloadEclipseSdk(project)

        addTaskToCreateP2MirrorAntScript(project)
        addTaskToMirrorP2Repository(project)
        addTaskToCreateTargetFile(project)
        addTaskToCreateTargetFeature(project)

        project.gradle.taskGraph.whenReady {
            loadTargetFile(project)
        }
    }

    static void addTaskToCreateTargetFeature(Project project) {
        project.task(TASK_NAME_CREATE_TARGET_FEATURE) {
            doLast {
                createTargetFeature(project)
            }
        }
    }

    static void createTargetFeature(Project project) {
        def mirror = project.eclipseMirror
        if(mirror.targetFeatureName== null || mirror.targetFeatureName.length() == 0) {
            return
        }


        def targetDir = project.buildDir.toPath().resolve("targetFeature/features/")
        def targetFile = targetDir.resolve("${mirror.targetFeatureName}_1.0.0.jar").toFile()

        if(!targetFile.getParentFile().exists()) {
            targetDir.toFile().mkdirs()
            project.buildDir.toPath().resolve("targetFeature/plugins/").toFile().mkdirs()
        }

        targetFile.withOutputStream {
            ZipOutputStream zipStream = new ZipOutputStream(it)
            zipStream.putNextEntry(new ZipEntry('feature.xml'))
            createFeatureXml(project, zipStream)
            zipStream.closeEntry()
            zipStream.putNextEntry(new ZipEntry('p2.inf'))
            createP2Inf(project, zipStream)
            zipStream.closeEntry()
            zipStream.close()
        }
    }

    static void createP2Inf(Project project, OutputStream stream) {
        Writer w = new OutputStreamWriter(stream, 'UTF-8')
        def pw = new PrintWriter(w)

        pw.println "properties.1.name=org.eclipse.equinox.p2.type.category"
        pw.println "properties.1.value=true"

        pw.flush()
        w.flush()
    }

    static void createFeatureXml(Project project, OutputStream stream) {
        def mirror = project.eclipseMirror

        Writer w = new OutputStreamWriter(stream, 'UTF-8')
        def xmlMarkup = new MarkupBuilder(w)


        xmlMarkup.'feature'('id': mirror.targetFeatureName, 'label':"TargetPlatform feature for ${mirror.targetFeatureName}", 'version':"1.0.0", 'provider-name':"Generated with monkeyworks p2 tooling") {
            mirror.targetPlatform.locations.each { location -> 
                location.installableUnits.each { unit -> 
                    if(unit.id.endsWith('feature.group')) {
                        xmlMarkup.'includes'('id': unit.id.take(unit.id.indexOf('.feature.group')), 'version':unit.version, unpack:false, 'download-size':"0", 'install-size':"0")
                    } else {
                        xmlMarkup.'plugin'('id': unit.id, 'version':unit.version, unpack:false, 'download-size':"0", 'install-size':"0")
                    }
                }
            }
        }
    }

    static void loadTargetFile(Project project) {
        def mirror = project.eclipseMirror

        def rootNode = new XmlSlurper().parseText(mirror.targetDefinition.text)
        if(mirror.targetPlatform == null) {
            mirror.targetPlatform = new TargetPlatform()
        }

        rootNode.locations.location.each { location ->
            
            Location loc = new Location()
            loc.url = location.repository.@location.text()
            mirror.targetPlatform.locations.add(loc)
            
            location.unit.each {unit -> 
                def installableUnit = new InstallableUnit();
                installableUnit.id=unit.@id
                installableUnit.version=unit.@version  
                loc.installableUnits.add(installableUnit) 
            }
        }
    }

    static void addTaskToMirrorP2Repository(Project project) {
        project.task('cleanP2Repository') {
            doFirst {
                project.buildDir.toPath().resolve("p2-repository").toFile().deleteDir()
            }
        }
        if (project.tasks.findByPath('clean') == null) {project.tasks.create('clean')}
        project.tasks.clean.dependsOn 'cleanP2Repository'

        project.task(TASK_NAME_MIRROR_P2, dependsOn: [DownloadHelper.TASK_NAME_DOWNLOAD_ECLIPSE_SDK, TASK_NAME_CREATE_ANT]) {
            description = "Mirrors a p2 repository"
            def targetDir = project.buildDir.toPath().resolve("p2-repository").toFile()            
            outputs.upToDateWhen { 
                return false 
            }

            doLast { mirrorP2Repository(project) }
        }
    }

    static void addTaskToCreateTargetFile(Project project) {
        project.task(TASK_NAME_CREATE_TARGET_FILE) {
            description = "Creates Target Platform file"    
            outputs.upToDateWhen { 
                return false 
            }

            doLast { 
                def targetFilename = project.eclipseMirror.targetFile
                def targetDir = project.buildDir.toPath().resolve("${targetFilename}").toFile()
                println "Create target platform $targetDir"
                println "File: ${targetFilename}" 
                createTargetFile(project, targetDir) 
	        }
        }
    }

    static void createTargetFile(Project project, File targetFile) {
        def xmlWriter = new StringWriter()
        def xmlMarkup = new MarkupBuilder(xmlWriter)

        def mirror = project.eclipseMirror

        Closure<MarkupBuilder> buildInstallableUnits = { MarkupBuilder builder ->
            mirror.targetPlatform.locations.each { location ->
                location.installableUnits.each { iu ->
                    if(iu.version != null) {
                        builder.'unit'(id: iu.id, version: iu.version)
                    } else {
                        builder.'unit'(id: iu.id)
                    }
                }
            }

            return builder
        }

        xmlMarkup.'target'(name :"buildmonkey", 'sequenceNumber' :"1") {
            xmlMarkup.'locations'() {
                xmlMarkup.'location'(includeAllPlatforms: "false", includeConfigurePhase: "true", includeMode:"planner", includeSource:"true", type: "InstallableUnit") {
                    buildInstallableUnits(xmlMarkup)
                    xmlMarkup.'repository'(location : mirror.updateURL)
                }
            }
            xmlMarkup.'targetJRE'(path:"org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.8")
        }

        if(!targetFile.exists()) {
            def buildFolder = project.buildDir
            if(!buildFolder.exists()) {
                buildFolder.mkdirs()
            }
        }

        targetFile.write(xmlWriter.toString())
    }

    static void mirrorP2Repository(Project project) {
    	project.exec {
            environment['eclipse.p2.mirrors'] = "false"
            // redirect the external process output to the logging
            //standardOutput = new LogOutputStream(project.logger, LogLevel.INFO)
            //errorOutput = new LogOutputStream(project.logger, LogLevel.INFO)
            def config = project.eclipseConfiguration
            commandLine("java",
            		'-cp', project.buildDir.toPath().resolve("eclipse/eclipse/plugins/org.eclipse.equinox.launcher_${config.launcherVersion}.jar").toFile(),
            		'org.eclipse.core.launcher.Main', 
                    '-application', 'org.eclipse.ant.core.antRunner',
                    '-consoleLog',
                    '-nosplash',
                    '-buildfile', project.buildDir.toPath().resolve('ant.xml')
            )
        }
    }

    static void addTaskToCreateP2MirrorAntScript(Project project) {
        project.task(TASK_NAME_CREATE_ANT, dependsOn:[TASK_NAME_CREATE_TARGET_FEATURE]) {
        	description = "Creates ant task to mirror specific p2 repository"

        	doLast {
	        	def xmlWriter = new StringWriter()
				def xmlMarkup = new MarkupBuilder(xmlWriter)

				Closure<MarkupBuilder> buildP2MirrorTask = { MarkupBuilder builder, Location location ->
					location.installableUnits.each { iu ->
                        if(iu.version != null) {
                            builder.'iu'(id: iu.id, version: iu.version)
                        } else {
                            builder.'iu'(id: iu.id)
                        }
					}

					return builder
				}

				xmlMarkup.'project'(name :"Create Mirror", 'default' :"create-mirror", basedir:".") {
					xmlMarkup.'target'(name:"create-mirror") {
                        xmlMarkup.'p2.mirror'(destination: project.eclipseMirror.target, references: 'false', ignoreErrors: 'true', verbose:'true') {
                            project.eclipseMirror.targetPlatform.locations.each { location ->    
                                xmlMarkup.'source'('location': location.url)
                            }

                            xmlMarkup.'slicingOptions'(followStrict: project.eclipseMirror.sliceStrict, 
                                includeFeatures: project.eclipseMirror.includeFeatures, 
                                latestVersionOnly: project.eclipseMirror.latestVersionOnly)

                            project.eclipseMirror.targetPlatform.locations.each { location ->    
                                buildP2MirrorTask(xmlMarkup, location)
                            }
                        }

                        xmlMarkup."p2.publish.featuresAndBundles"(metadataRepository:"file:${project.eclipseMirror.target}", artifactRepository:"file:${project.eclipseMirror.target}", source:"${project.eclipseMirror.target}", compress:"false")
                        xmlMarkup."p2.publish.featuresAndBundles"(metadataRepository:"file:${project.eclipseMirror.target}", artifactRepository:"file:${project.eclipseMirror.target}", source:"${project.buildDir}/targetFeature", publishArtifacts:"true", compress:"true", append:"true")
					}
				}

				def antFile = project.buildDir.toPath().resolve("ant.xml").toFile()
				if(!antFile.exists()) {
					def buildFolder = project.buildDir
					if(!buildFolder.exists()) {
						buildFolder.mkdirs()
					}
				}

				antFile.write(xmlWriter.toString())
			}
        }
    }

}
