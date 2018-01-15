package de.monkeyworks.buildmonkey.equinox.api.parameter;

import java.util.Iterator;
import java.util.List;

public abstract class BaseParameter {
    protected String concatListMembers(List<String> artifactRepository) {
        StringBuffer repositoryString = new StringBuffer();
        Iterator<String> it = artifactRepository.iterator();
        while(it.hasNext()) {
            String repo = it.next();
            repositoryString.append(repo);
            if(it.hasNext()) {
                repositoryString.append(",");
            }
        }
        return repositoryString.toString();
    }

    public abstract List<String> getArgumentList();
}
