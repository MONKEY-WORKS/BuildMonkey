package de.monkeyworks.buildmonkey.product.publish

import de.monkeyworks.buildmonkey.equinox.api.MetadataRepositoryLoaderService
import de.monkeyworks.buildmonkey.equinox.embedding.EquinoxEmbedder
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.nio.file.Files
import java.nio.file.Path

/**
 * Created by Johannes Tandler on 06/03/2017.
 */
class ProductPublishPlugin implements Plugin<Project> {

    private final static String extensionName = "product"

    @Override
    void apply(Project project) {

        EquinoxEmbedder embedder = new EquinoxEmbedder()
        embedder.configure(project)

        // create project extension if it doesn't exist yet
        if (project.extensions.findByName(extensionName) == null) {
            project.extensions.create(extensionName, ProductExtension, project)
        }

        def task = project.task("publish-product")
        task.doFirst {
            description: "publish my product"
        }

        task.doLast {
            Path targetDir = project.buildDir.toPath().resolve("equinox")
            Files.createDirectories(targetDir)

            if(Files.exists(targetDir.resolve("configuration")))
                targetDir.resolve("configuration").deleteDir()

            embedder.bootstrap(targetDir.toAbsolutePath().toString())


            MetadataRepositoryLoaderService service = embedder.getService(MetadataRepositoryLoaderService.class)
            URI metaRepolocation = project.buildDir.toPath().resolve("merge-repository").toUri()


            ProductExtension extension = project.product

            service.publishProduct(metaRepolocation, extension.productFile)
        }
    }

}
