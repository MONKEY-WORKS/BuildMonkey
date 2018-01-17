/**
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
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

    void installFeaturesToNew(InstallFeatureParameter parameter);

    void mergeRepositories (MergeRepositoryParameter parameter);
}
