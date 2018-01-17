/**
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.osgi.equinox.bridge.api.impl;

import de.monkeyworks.buildmonkey.equinox.api.MetadataRepositoryLoaderService;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.internal.p2.director.app.DirectorApplication;
import org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IVersionedId;
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
    public String publishProductMetaData(String buildPath, URI repositoryLocation, String productFilePath) {

        BundleContext ctx = Activator.getContext();
        ServiceReference<IProvisioningAgentProvider> managerRef = ctx.getServiceReference(IProvisioningAgentProvider.class);
        IProvisioningAgentProvider agentProvider = ctx.getService(managerRef);

        IProvisioningAgent agent = null;
        try {
            agent = agentProvider.createAgent(Paths.get(buildPath,"equinox", "p2").toUri());
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

        // PUBLISH product meta data
        Publisher publisher = new Publisher(publisherInfo);
        //String exeFeaturePath =  "/Users/jake/code/hackathon/BuildMonkey/examples/exampleApp/build/p2-repository/features/org.eclipse.equinox.executable_3.6.300.v20161122-1740";

        ProductAction action = new ProductAction(null, productFile, "tooling", null);
        IPublisherAction[] actions = new IPublisherAction[] { action };

        IStatus result = publisher.publish(actions, null);


        // materialise product
        System.out.println(result);
        System.out.println("-------");

        return productFile.getId();
    }

    @Override
    public String getProductID(String productFilePath) {
        ProductFile productFile = null;
        try {
            productFile = new ProductFile(Paths.get(productFilePath).toAbsolutePath().toFile().toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return productFile.getId();
    }

    public List<IVersionedId> getFeatures(String productFilePath) {
        ProductFile productFile = null;
        try {
            productFile = new ProductFile(Paths.get(productFilePath).toAbsolutePath().toFile().toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return productFile.getFeatures();
    }

    @Override
    public void materialiseProduct(String buildPath, String productId) {

        List<String> arguments = new ArrayList<>();
        arguments.add("-metadataRepository");
        arguments.add("file:/Users/jake/code/playground/org.eclipse.tycho-demo/itp04-rcp/eclipse-repository/target/repository,file:/Users/jake/code/playground/org.eclipse.tycho-demo/itp04-rcp/root-files/target");
        arguments.add("-artifactRepository");
        arguments.add("file:/Users/jake/code/playground/org.eclipse.tycho-demo/itp04-rcp/eclipse-repository/target/repository,file:/Users/jake/code/playground/org.eclipse.tycho-demo/itp04-rcp/root-files/target");
        arguments.add("-installIU");
        arguments.add("example.product.id");
        arguments.add("-destination");
        arguments.add("/Users/jake/code/hackathon/BuildMonkey/examples/exampleApp/build/product/Eclipse2.app");
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
