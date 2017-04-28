package de.monkeyworks.buildmonkey.equinox.api;

import java.net.URI;

/**
 * Created by jake on 06/03/2017.
 */
public interface MetadataRepositoryLoaderService {

    void publishProduct(URI repositoryLocation, String productFile);

}
