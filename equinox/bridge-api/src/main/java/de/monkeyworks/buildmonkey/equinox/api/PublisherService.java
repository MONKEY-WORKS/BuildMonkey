/**
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
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
