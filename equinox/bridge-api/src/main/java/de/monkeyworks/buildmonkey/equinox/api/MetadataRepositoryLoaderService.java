package de.monkeyworks.buildmonkey.equinox.api;

import java.net.URI;

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

    void materialiseProduct(String buildPath, String productId);

    String getProductID(String productFilePath);
}
