package de.monkeyworks.buildmonkey.product.config

import org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile

import de.monkeyworks.buildmonkey.product.common.ProductExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.nio.file.Paths

class ProductConfigPlugin implements Plugin<Project>{

    private final static String extensionName = "product"
    private ProductExtension extension
    private ProductFile productFile = null

    @Override
    void apply(Project project) {
        // create project extension if it doesn't exist yet
        if (project.extensions.findByName(extensionName) == null) {
            project.extensions.create(extensionName, ProductExtension, project)
        }

        extension = project.product

        def task = project.task("readProduct")
        task.doFirst {
            description = "reads the .product file"
            try {
                productFile = new ProductFile(Paths.get(extension.productFile).toAbsolutePath().toFile().toString())
            } catch (Exception e) {
                e.printStackTrace()
            }
        }

        task.doLast {
            if (productFile != null){
                println("ID: " + productFile.getId())
                println("Application: " + productFile.getApplication())
                println("Name: "+ productFile.getProductName())
                println("Args: " + productFile.getProgramArguments("win32"))
                println("VM-Args: "+ productFile.getVMArguments("win32"))
                println("Plugins:")
                productFile.getFeatures().each { obj ->
                    println("  - " + obj.id + " : " + obj.version.toString())
                }
            }
        }

        def task1 = project.task("createLauncherIni")
        task1.dependsOn(task)
        task1.doFirst {
            description = "creates launcher ini file"
        }
        task1.doLast {
            def file = new File(project.getBuildDir(), productFile.getLauncherName()+".ini")
            file.newWriter().withWriter { w ->
                w << "-startup\n//TODO\n"
                w << "--launcher.library\n//TODO\n"
                productFile.getProgramArguments("win32").split(" ").each { str ->
                    w << str + "\n"
                }
                w << "-vmargs\n"
                productFile.getVMArguments("win32").split(" ").each { str ->
                    w << str + "\n"
                }
            }
        }

        def task2 = project.task("createConfigIni")
        task2.dependsOn(task)
        task2.doFirst {
            description = "creates the config.ini file"
        }

        task2.doLast {
            def file = new File(project.getBuildDir(), "config.ini")
            file.newWriter().withWriter {w ->
                w << "eclipse.p2.profile=DefaultProfile\n"
                w << "framework.osqi= //TODO\n"
                w << "equinox.use.ds=true\n"
                w << "ds.delayed.keepInstances=true\n"
                w << "osgi.bundles= // TODO\n"
                w << "eclipse.product=" + productFile.getId() + "\n"
                w << "osgi.splashPath=platform\\\\:/base/plugins/hmi.workbench.ultimate\n"
                w << "osgi.framework.extensions= //TODO\n"
                w << "osgi.bundles.defaultStartLevel=4\n"
                w << "eclipse.p2.data.area=@config.dir/../p2\n"
                w << "eclipse.application=" + productFile.getApplication() + "\n"
            }
        }

    }
}
