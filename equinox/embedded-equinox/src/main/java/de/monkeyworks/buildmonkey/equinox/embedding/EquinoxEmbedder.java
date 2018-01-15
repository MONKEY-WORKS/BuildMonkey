/**
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.equinox.embedding;

import org.eclipse.core.runtime.adaptor.EclipseStarter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 *
 *
 * Created by Johannes Tandler on 06/03/2017.
 */
public class EquinoxEmbedder {

    private BundleContext eclipseContext;

    private Project project;

    public void configure(Project project) {
        this.project = project;

        // add external tycho dependency
        project.getConfigurations().create("build");
        project.getDependencies().add("build", "org.eclipse.tycho:tycho-bundles-external:1.0.0@zip");
        project.getDependencies().add("build", "de.monkeyworks.buildmonkey:bridge.api.impl:+");
    }

    /**
     * Bootstraps the eclipse instance and starts it
     *
     * @param installationLocation points to the location of the eclipse repository
     * @throws IOException
     */
    public void bootstrap(String installationLocation) throws IOException {
        if(eclipseContext != null) {
            return;
        }
        if ("Eclipse".equals(System.getProperty("org.osgi.framework.vendor"))) {
            throw new IllegalStateException(
                    "Can not start Eclipse inside eclipse");
        }

        // get framework directory
        File frameworkDir = new File(installationLocation);

        List<File> additionalBundles = new ArrayList<>();

        // check resolved configurations to load additional bundles
        ResolvedConfiguration config = project.getConfigurations().getByName("build").getResolvedConfiguration();
        for(ResolvedArtifact artifact : config.getResolvedArtifacts()) {
            if(artifact.getName().equals("tycho-bundles-external")) {
                if(!Files.exists(frameworkDir.toPath().resolve("eclipse"))) {
                    Utils.unzip(artifact.getFile().getAbsolutePath(), frameworkDir.getAbsolutePath());
                }
            } else if(artifact.getName().equals("bridge.api.impl")) {
                additionalBundles.add(artifact.getFile());
            }
        }

        frameworkDir = new File(frameworkDir, "eclipse");

        // create osgi configuration
        final Map<String, String> platformProperties = new LinkedHashMap<>();
        String frameworkLocation = frameworkDir.getAbsolutePath() + "/";

        platformProperties.put("osgi.install.area", frameworkLocation);
        platformProperties.put("osgi.syspath", frameworkLocation + "plugins");

        File configurationFile = new File(frameworkDir.getParentFile(), "configuration");

        platformProperties.put("osgi.configuration.area", configurationFile.getAbsolutePath());

        StringBuilder bundles = new StringBuilder();
        addBundlesDir(bundles, new File(frameworkDir, "plugins").listFiles(), false);
        for (File location : additionalBundles) {
            if (bundles.length() > 0) {
                bundles.append(',');
            }
            bundles.append(location.toPath().toAbsolutePath().toUri());
        }

        platformProperties.put("osgi.bundles", bundles.toString());

        List<String> extraSystemPackages = new ArrayList<>();
        extraSystemPackages.add("de.monkeyworks.buildmonkey.equinox.api");

        if (extraSystemPackages.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (String pkg : extraSystemPackages) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(pkg);
            }
            // make the system bundle export the given packages and load them from the parent class loader
            platformProperties.put("org.osgi.framework.system.packages.extra", sb.toString());
        }

        platformProperties.put("osgi.parentClassloader", "fwk");

        // set initial properties
        EclipseStarter.setInitialProperties(platformProperties);

        Logger logger = project.getLogger();

        // start eclipse
        try {
            logger.info("Starting Eclipse");
            EclipseStarter.startup(new String[0], null);
            logger.info("Eclipse started");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // get context
        eclipseContext = EclipseStarter.getSystemBundleContext();

        // activate addiontal bundles
        activateBundlesInWorkingOrder();


        if(logger.isInfoEnabled()) {
            logger.info("Eclipse Plugin States:");
            for (Bundle bundle : eclipseContext.getBundles()) {
                logger.info("\t" + bundle.getSymbolicName() + " : " + bundle.getState());
            }
        }
    }

    private void activateBundlesInWorkingOrder() {
        tryActivateBundle("org.eclipse.equinox.ds");
        tryActivateBundle("org.eclipse.equinox.registry");
        tryActivateBundle("org.eclipse.core.net");
        tryActivateBundle("de.monkeyworks.buildmonkey.osgi.equinox.bridge.api.impl");
    }

    /**
     * Returns a given class from the eclipse side of life to somewhere else!
     * @param serviceClass
     * @param <T>
     * @return
     */
    public <T> T getService(Class<T> serviceClass) {
        ServiceReference<T> ref = eclipseContext.getServiceReference(serviceClass);
        return eclipseContext.getService(ref);
    }

    /**
     * Tries to activate a given bundle
     * @param symbolicName name of the bundle to activate
     */
    private void tryActivateBundle(String symbolicName) {
        for (Bundle bundle : eclipseContext.getBundles()) {
            if (symbolicName.equals(bundle.getSymbolicName())) {
                try {
                    bundle.start(Bundle.START_TRANSIENT); // don't have OSGi remember the autostart setting; want to start these bundles manually to control the start order
                } catch (BundleException e) {
                    System.out.println(e);
                }
            }
        }
    }

    private void addBundlesDir(StringBuilder bundles, File[] files, boolean absolute) {
        if (files != null) {
            for (File file : files) {
                if (isFrameworkBundle(file)) {
                    continue;
                }

                if (bundles.length() > 0) {
                    bundles.append(',');
                }

                if (absolute) {
                    bundles.append(file.toPath().toUri());
                } else {
                    String name = file.getName();
                    int verIdx = name.indexOf('_');
                    if (verIdx > 0) {
                        bundles.append(name.substring(0, verIdx));
                    } else {
                        throw new UnsupportedOperationException("File name doesn't match expected pattern: " + file);
                    }
                }
            }
        }
    }

    protected boolean isFrameworkBundle(File file) {
        return file.getName().startsWith("org.eclipse.osgi_");
    }
}
