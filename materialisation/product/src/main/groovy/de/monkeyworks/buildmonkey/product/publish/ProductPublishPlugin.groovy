package de.monkeyworks.buildmonkey.product.publish

import de.monkeyworks.buildmonkey.equinox.api.MetadataRepositoryLoaderService
import de.monkeyworks.buildmonkey.equinox.embedding.EquinoxEmbedder
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

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

            ProductExtension extension = project.product


            MetadataRepositoryLoaderService service = embedder.getService(MetadataRepositoryLoaderService.class)
            URI metaRepolocation = Paths.get(extension.repository).toUri()
            println(metaRepolocation)


            String productID = service.publishProductMetaData(project.buildDir.toString(), metaRepolocation, extension.productFile)
            if(extension.productID == null)
                extension.productID = productID
        }

        task = project.task("materialise-product")
        task.description = "materialises a product"
        task.doLast {
            MetadataRepositoryLoaderService service = embedder.getService(MetadataRepositoryLoaderService.class)
            ProductExtension extension = project.product
            if(extension.productID == null) {
                extension.productID = service.getProductID(extension.productFile)
            }

            service.materialiseProduct(project.buildDir.toString(), extension.productID)
        }
    }

}
