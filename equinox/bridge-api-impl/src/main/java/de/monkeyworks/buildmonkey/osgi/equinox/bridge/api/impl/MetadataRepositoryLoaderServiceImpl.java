package de.monkeyworks.buildmonkey.osgi.equinox.bridge.api.impl;

import de.monkeyworks.buildmonkey.equinox.api.MetadataRepositoryLoaderService;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.internal.p2.director.app.DirectorApplication;
import org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.Publisher;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.eclipse.ProductAction;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jake on 06/03/2017.
 */
public class MetadataRepositoryLoaderServiceImpl implements MetadataRepositoryLoaderService {

    public MetadataRepositoryLoaderServiceImpl() {}

    @Override
    public void publishProduct(URI repositoryLocation, String productFilePath) {

        BundleContext ctx = Activator.getContext();
        ServiceReference<IProvisioningAgentProvider> managerRef = ctx.getServiceReference(IProvisioningAgentProvider.class);
        IProvisioningAgentProvider agentProvider = ctx.getService(managerRef);

        IProvisioningAgent agent = null;
        try {
            agent = agentProvider.createAgent(Paths.get("/Users/jake/code/hackathon/BuildMonkey/examples/exampleApp/build/equinox/p2").toUri());
        } catch (ProvisionException e) {
            e.printStackTrace();
        }

        IMetadataRepositoryManager manager = (IMetadataRepositoryManager) agent.getService(IMetadataRepositoryManager.SERVICE_NAME);
        IArtifactRepositoryManager artiManager = (IArtifactRepositoryManager) agent.getService(IArtifactRepositoryManager.SERVICE_NAME);

        IMetadataRepository metadataRepository = null;
        IArtifactRepository artifactRepository = null;

        try {
            metadataRepository = manager.loadRepository(repositoryLocation, null);
            artifactRepository = artiManager.loadRepository(repositoryLocation, null);
        } catch (org.eclipse.equinox.p2.core.ProvisionException e) {
            e.printStackTrace();
        }

        final PublisherInfo publisherInfo = new PublisherInfo();

        publisherInfo.setMetadataRepository(metadataRepository);
        publisherInfo.setArtifactRepository(artifactRepository);
        publisherInfo.setArtifactOptions(IPublisherInfo.A_INDEX | IPublisherInfo.A_PUBLISH);


        ProductFile productFile = null;
        try {
            productFile = new ProductFile(Paths.get(productFilePath).toAbsolutePath().toFile().toString());
        } catch (Exception e) {
            e.printStackTrace();
        }


        Publisher publisher = new Publisher(publisherInfo);
        String exeFeaturePath =  "/Users/jake/code/hackathon/BuildMonkey/examples/exampleApp/build/merge-repository/features/org.eclipse.equinox.executable_3.6.300.v20161122-1740";


        ProductAction action = new ProductAction(null, productFile, "tooling", new File(exeFeaturePath));
        IPublisherAction[] actions = new IPublisherAction[] { action };

        IStatus result = publisher.publish(actions, null);
        System.out.println(result);
        System.out.println("-------");

        List<String> arguments = new ArrayList<>();
        arguments.add("-metadataRepository");
        arguments.add(repositoryLocation.toString());
        arguments.add("-artifactRepository");
        arguments.add(repositoryLocation.toString());
        arguments.add("-installIU");
        arguments.add(productFile.getId());
        arguments.add("-destination");
        arguments.add("/Users/jake/code/hackathon/BuildMonkey/examples/exampleApp/build/product/Eclipse.app");
        arguments.add("-profile");
        arguments.add("DefaultProfile");
        arguments.add("-profileProperties");
        arguments.add("org.eclipse.update.install.features=true");
        arguments.add("-roaming");
        arguments.add("-p2.os");
        arguments.add("macosx");
        arguments.add("-p2.ws");
        arguments.add("cocoa");
        arguments.add("-p2.arch");
        arguments.add("x86_64");

        new DirectorApplication().run(arguments.toArray(new String[arguments.size()]));
    }
}
