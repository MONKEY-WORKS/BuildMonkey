/**
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.equinox.api;

import org.eclipse.equinox.p2.metadata.IVersionedId;

import java.net.URI;
import java.util.List;

/**
 *
 * Service to do metadata related stuff
 *
 * Created by Johannes Tandler on 06/03/2017.
 */
public interface MetadataRepositoryLoaderService {

    /**
     * Publishs a product into a specified repository
     * @param repositoryLocation location of the p2 repository
     * @param productFile location of a product definition to publish
     */
    String publishProductMetaData(String buildPath, URI repositoryLocation, String productFile);

    // Should be part of P2Director service or removed
    void materialiseProduct(String buildPath, String productId);
    // No need for a public method like that
    String getProductID(String productFilePath);

    // No need for a public method like that
    List<IVersionedId> getFeatures(String productFilePath);
}
