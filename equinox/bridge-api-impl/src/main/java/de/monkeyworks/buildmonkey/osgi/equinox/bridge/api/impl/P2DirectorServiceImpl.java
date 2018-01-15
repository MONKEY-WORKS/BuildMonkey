package de.monkeyworks.buildmonkey.osgi.equinox.bridge.api.impl;

import de.monkeyworks.buildmonkey.equinox.api.P2DirectorService;
import de.monkeyworks.buildmonkey.equinox.api.parameter.InstallFeatureParameter;
import de.monkeyworks.buildmonkey.equinox.api.parameter.MergeRepositoryParameter;
import org.eclipse.equinox.internal.p2.director.app.DirectorApplication;

import java.util.List;

public class P2DirectorServiceImpl implements P2DirectorService {

    @Override
    public void installFeaturesToPlatform(InstallFeatureParameter parameter) {
        List<String> arguments = parameter.getArgumentList();

        arguments.add("-tag");
        arguments.add("target-platform");
        arguments.add("-roaming");
        arguments.add("-nosplash");

        new DirectorApplication().run(arguments.toArray(new String[arguments.size()]));
    }

    @Override
    public void mergeRepositories(MergeRepositoryParameter parameter) {
        List<String> arguments = parameter.getArgumentList();

        arguments.add("-roaming");
        arguments.add("-nosplash");

        new DirectorApplication().run(arguments.toArray(new String[arguments.size()]));
    }
}
