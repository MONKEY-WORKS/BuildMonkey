package de.monkeyworks.buildmonkey.equinox.api.parameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameter object for merging p2 repositories
 *
 * @author Michael Barth on 12/01/2018.
 */
public class MergeRepositoryParameter extends BaseParameter {

    private String metadataRepository = null;

    private String artifactRepository = null;

    private String repository;

    private String installIU;

    private String destination;

    private String profile;

    private PlatformArchitecture architecture;


    public String getRepository() {
        return repository;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public void setRepository(List<String> repository) {
        this.repository = concatListMembers(repository);
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getInstallIU() {
        return installIU;
    }

    public void setInstallIU(List<String> installIU) {
        this.installIU = concatListMembers(installIU);
    }

    public void setInstallIU(String installIU) {
        this.installIU = installIU;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getMetadataRepository() {
        return metadataRepository;
    }

    public void setMetadataRepository(List<String> metadataRepository) {
        this.metadataRepository = concatListMembers(metadataRepository);
    }

    public void setMetadataRepository(String metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    public String getArtifactRepository() {
        return artifactRepository;
    }

    public void setArtifactRepository(List<String> artifactRepository) {
        this.artifactRepository = concatListMembers(artifactRepository);
    }

    public void setArtifactRepository(String artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    public PlatformArchitecture getArchitecture() {
        return architecture;
    }

    public void setArchitecture(PlatformArchitecture architecture) {
        this.architecture = architecture;
    }

    @Override
    public List<String> getArgumentList() {
        List<String> arguments = new ArrayList<>();
        arguments.add("-repository");
        arguments.add(repository);
        arguments.add("-artifactRepository");
        arguments.add(artifactRepository);
        arguments.add("-metadataRepository");
        arguments.add(metadataRepository);
        arguments.add("-installIU");
        arguments.add(installIU);
        arguments.add("-destination");
        arguments.add(destination);
        arguments.add("-profile");
        arguments.add(profile);
        arguments.add("-p2.os");
        arguments.add(architecture.getOperatingSystem());
        arguments.add("-p2.ws");
        arguments.add(architecture.getWindowingSystem());
        arguments.add("-p2.arch");
        arguments.add(architecture.getProcessorArchitecture());

        return arguments;
    }
}
