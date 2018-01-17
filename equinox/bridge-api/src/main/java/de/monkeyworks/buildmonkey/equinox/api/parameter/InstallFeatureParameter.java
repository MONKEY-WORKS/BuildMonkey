/**
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.equinox.api.parameter;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * Parameter object for installing features into a p2 repository
 *
 * @author Michael Barth on 12/01/2018.
 *
 */
public class InstallFeatureParameter extends BaseParameter {

    private String repository;

    private String installIU;

    private String destination;

    private String bundlepool;

    private PlatformArchitecture architecture;

    private String profile;


    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public void setRepository(List<String> repository) {
        this.repository = concatListMembers(repository);
    }

    public String getInstallIU() {
        return installIU;
    }

    public void setInstallIU(String installIU) {
        this.installIU = installIU;
    }

    public void setInstallIU(List<String> installIU) {
        this.installIU = concatListMembers(installIU);
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getBundlepool() {
        return bundlepool;
    }

    public void setBundlepool(String bundlepool) {
        this.bundlepool = bundlepool;
    }

    public PlatformArchitecture getArchitecture() {
        return architecture;
    }

    public void setArchitecture(PlatformArchitecture architecture) {
        this.architecture = architecture;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    @Override
    public List<String> getArgumentList() {
        List<String> arguments = new ArrayList<>();
        arguments.add("-repository");
        arguments.add(repository);
        arguments.add("-installIU");
        arguments.add(installIU);
        arguments.add("-destination");
        arguments.add(destination);
        arguments.add("-profile");
        arguments.add(profile);
        arguments.add("-bundlepool");
        arguments.add(bundlepool);
        arguments.add("-p2.os");
        arguments.add(architecture.getOperatingSystem());
        arguments.add("-p2.ws");
        arguments.add(architecture.getWindowingSystem());
        arguments.add("-p2.arch");
        arguments.add(architecture.getProcessorArchitecture());

        for(String arg : arguments) {
            System.out.print(arg + " ");
        }

        System.out.println();

        return arguments;
    }
}
