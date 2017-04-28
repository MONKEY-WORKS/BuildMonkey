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

        /*task.doLast {
            ProductExtension extension = project.product

            // extract executable feature
            String exeFeaturePath = extension.executableFeature
            if (extension.executableFeature == null || extension.executableFeature.isEmpty()) {
                exeFeaturePath = "/Users/jake/code/hackathon/BuildMonkey/examples/exampleApp/build/merge-repository/features/org.eclipse.equinox.executable_3.6.300.v20161122-1740"
            }

            ProductFile productFile = new ProductFile(Paths.get(extension.productFile).toAbsolutePath().toFile().toString())

            ProductAction productAction = new ProductAction(null, productFile, "tooling",
                    Paths.get(exeFeaturePath).toFile())

            //IProvisioningAgent agent = new ProvisioningAgent()
            /*agent.setBundleContext(new BundleContext() {

                @Override
                String getProperty(String key) {
                    return null
                }

                @Override
                Bundle getBundle() {
                    return null
                }

                @Override
                Bundle installBundle(String location, InputStream input) throws BundleException {
                    return null
                }

                @Override
                Bundle installBundle(String location) throws BundleException {
                    return null
                }

                @Override
                Bundle getBundle(long id) {
                    return null
                }

                @Override
                Bundle[] getBundles() {
                    return new Bundle[0]
                }

                @Override
                void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException {

                }

                @Override
                void addServiceListener(ServiceListener listener) {

                }

                @Override
                void removeServiceListener(ServiceListener listener) {

                }

                @Override
                void addBundleListener(BundleListener listener) {

                }

                @Override
                void removeBundleListener(BundleListener listener) {

                }

                @Override
                void addFrameworkListener(FrameworkListener listener) {

                }

                @Override
                void removeFrameworkListener(FrameworkListener listener) {

                }

                @Override
                ServiceRegistration<?> registerService(String[] clazzes, Object service, Dictionary<String, ?> properties) {
                    return null
                }

                @Override
                ServiceRegistration<?> registerService(String clazz, Object service, Dictionary<String, ?> properties) {
                    return null
                }

                @Override
                def <S> ServiceRegistration<S> registerService(Class<S> clazz, S service, Dictionary<String, ?> properties) {
                    return null
                }

                @Override
                def <S> ServiceRegistration<S> registerService(Class<S> clazz, ServiceFactory<S> factory, Dictionary<String, ?> properties) {
                    return null
                }

                @Override
                ServiceReference<?>[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
                    return new ServiceReference[0]
                }

                @Override
                ServiceReference<?>[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
                    return new ServiceReference[0]
                }

                @Override
                ServiceReference<?> getServiceReference(String clazz) {
                    return null
                }

                @Override
                def <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
                    return null
                }

                @Override
                def <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter) throws InvalidSyntaxException {
                    return null
                }

                @Override
                def <S> S getService(ServiceReference<S> reference) {
                    return null
                }

                @Override
                boolean ungetService(ServiceReference<?> reference) {
                    return false
                }

                @Override
                def <S> ServiceObjects<S> getServiceObjects(ServiceReference<S> reference) {
                    return null
                }

                @Override
                File getDataFile(String filename) {
                    return null
                }

                @Override
                Filter createFilter(String filter) throws InvalidSyntaxException {
                    return null
                }

                @Override
                Bundle getBundle(String location) {
                    return null
                }
            })*/
            /*agent.registerService(IProvisioningEventBus.SERVICE_NAME, new ProvisioningEventBus())
            agent.registerService(IAgentLocation.SERVICE_NAME, new IAgentLocation() {
                @Override
                URI getDataArea(String namespace) {
                    return Paths.get(project.buildDir).resolve("provisioning").resolve(namespace).toUri()
                }

                @Override
                URI getRootLocation() {
                    return Paths.get(project.buildDir).resolve("provisioning").toUri()
                }
            })

            IMetadataRepositoryManager metaDataManager = new MetadataRepositoryManager(agent) {
                @Override
                IMetadataRepository loadRepository(URI location, IProgressMonitor monitor) throws ProvisionException {
                    if(repositories == null) {
                        repositories = new HashMap<>()
                    }
                    return super.loadRepository(location, monitor)
                }
            }
            IArtifactRepositoryManager artifactDataManager = new ArtifactRepositoryManager(agent) {
                @Override
                IArtifactRepository loadRepository(URI location, IProgressMonitor monitor) throws ProvisionException {
                    if(repositories == null) {
                        repositories = new HashMap<>()
                    }
                    return super.loadRepository(location, monitor)
                }
            }

            ExtensionRegistry registry = RegistryFactory.createRegistry(null, null, null)
            RegistryFactory.setDefaultRegistryProvider(new IRegistryProvider() {
                @Override
                IExtensionRegistry getRegistry() {
                    return registry
                }
            })*/

        /*URI metaRepolocation = project.buildDir.toPath().resolve("merge-repository").toUri()
            URI artiRepolocation = metaRepolocation


            SimpleMetadataRepositoryFactory metadataRepositoryFactory = new SimpleMetadataRepositoryFactory()
            def metaRepository = metadataRepositoryFactory.load(metaRepolocation, 0, new NullProgressMonitor())

            SimpleArtifactRepositoryFactory artifactRepositoryFactory = new SimpleArtifactRepositoryFactory()
            def artiRepository = artifactRepositoryFactory.load(artiRepolocation, 0, new NullProgressMonitor())

            PublisherInfo publisherInfo = new PublisherInfo()
            publisherInfo.setArtifactOptions(IPublisherInfo.A_INDEX | IPublisherInfo.A_PUBLISH)
            publisherInfo.setMetadataRepository(metaRepository)
            publisherInfo.setArtifactRepository(artiRepository)

            Publisher publisher = new Publisher(publisherInfo)
            publisher.publish([productAction], new NullProgressMonitor())

        }*/

    }

}
