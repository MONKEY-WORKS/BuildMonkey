package de.monkeyworks.buildmonkey.equinox.api.parameter;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * Parameter object for publishing features and bundles from a p2 repository
 *
 * @author Michael Barth on 12/01/2018.
 *
 */
public class FeatureAndBundlePublishParameter extends BaseParameter{
    String metadataRepository = null;

    String artifactRepository = null;

    String source = null;

    public String getMetadataRepository() {
        return metadataRepository;
    }

    public void setMetadataRepository(String metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    public void setMetadataRepository(List<String> metadataRepository) {
        this.metadataRepository = concatListMembers(metadataRepository);
    }

    public String getArtifactRepository() {
        return artifactRepository;
    }

    public void setArtifactRepository(String artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    public void setArtifactRepository(List<String> artifactRepository) {
        this.artifactRepository =  concatListMembers(artifactRepository);
    }



    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public List<String> getArgumentList() {
        List<String> arguments = new ArrayList<>();
        arguments.add("-artifactRepository");
        arguments.add(artifactRepository);
        arguments.add("-metadataRepository");
        arguments.add(metadataRepository);
        arguments.add("-source");
        arguments.add(source);

        return arguments;
    }
}
