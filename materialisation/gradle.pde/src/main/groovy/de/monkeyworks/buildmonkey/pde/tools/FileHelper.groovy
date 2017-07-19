/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.pde.tools

/**
 * Created by micha on 02.06.17.
 */
class FileHelper {

    static File findSubFolder(File parent, String folderName) {
        if(parent == null || !parent.exists()) {
            return
        }
        File retval = null
        def files = parent.listFiles()
        files.each {
            if(it.isDirectory() && it.getName().equals(folderName)) {
                retval = it
            } else {
                File tmp = findSubFolder(it, folderName)
                if(tmp != null)
                    retval = tmp
            }
        }

        return retval
    }
}
