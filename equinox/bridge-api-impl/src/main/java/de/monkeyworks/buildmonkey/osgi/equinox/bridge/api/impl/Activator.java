package de.monkeyworks.buildmonkey.osgi.equinox.bridge.api.impl;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Created by jake on 06/03/2017.
 */
public class Activator implements BundleActivator {
    private static BundleContext context;

    public static BundleContext getContext() {
        return context;
    }

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        context = bundleContext;
    }

    @Override
    public void stop(BundleContext context) throws Exception {

    }
}
