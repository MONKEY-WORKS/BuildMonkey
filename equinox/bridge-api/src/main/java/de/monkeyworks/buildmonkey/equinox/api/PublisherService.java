package de.monkeyworks.buildmonkey.equinox.api;

import de.monkeyworks.buildmonkey.equinox.api.parameter.FeatureAndBundlePublishParameter;

/**
 *
 * Service to publish artefacts into p2 repositories
 *
 * Created by Michael Barth on 12/01/2018.
 */
public interface PublisherService {

    void publishFeaturesAndBundles(FeatureAndBundlePublishParameter parameter);
}
