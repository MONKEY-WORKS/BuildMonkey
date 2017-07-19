/**
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.osgi;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by jake on 02.06.17.
 */
public class RequiredBundle {

    Map<String, String> attributes = new HashMap<>();

    String bundleName;

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append(bundleName);
        for(String key : attributes.keySet()) {
            builder.append(";");
            builder.append(key);
            builder.append("=\"");
            builder.append(attributes.get(key));
            builder.append("\"");
        }

        return builder.toString();
    }
}
