package de.monkeyworks.buildmonkey.equinox.api;

import de.monkeyworks.buildmonkey.equinox.api.parameter.InstallFeatureParameter;
import de.monkeyworks.buildmonkey.equinox.api.parameter.MergeRepositoryParameter;

/**
 *
 * Service to start p2 director for p2 repository creation
 * and manipulation
 *
 * Created by Michael Barth on 12/01/2018.
 */
public interface P2DirectorService {

    void installFeaturesToPlatform (InstallFeatureParameter parameter);

    void mergeRepositories (MergeRepositoryParameter parameter);
}
