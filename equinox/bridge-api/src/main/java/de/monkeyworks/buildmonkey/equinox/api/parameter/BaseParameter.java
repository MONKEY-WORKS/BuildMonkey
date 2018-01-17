/**
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
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
