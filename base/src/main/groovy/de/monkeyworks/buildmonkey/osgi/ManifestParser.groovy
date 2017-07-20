/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.monkeyworks.buildmonkey.osgi

import java.util.jar.Manifest

/**
 * Created by jake on 02.06.17.
 */
class ManifestParser {

    protected File file

    protected Manifest manifest

    ManifestParser(File file) {
        this.file = file
    }

    ManifestParser(Manifest manifest) {
        this.manifest = manifest
    }

    def read() {
        if(manifest)
            return

        FileInputStream fis = new FileInputStream(file)
        manifest = new Manifest(fis)
        fis.close()
    }

    List<RequiredBundle> parseRequireBundles() {
        if(!manifest) {
            read()
        }

        def requiredBundleString = manifest.getMainAttributes().getValue('Require-Bundle')

        List<String> bundleStrings = []

        while(requiredBundleString != null && requiredBundleString.length() > 0) {
            if(!requiredBundleString.contains(",")) {
                bundleStrings.add(requiredBundleString)
                break
            }


            String bundle = requiredBundleString.substring(0, requiredBundleString.indexOf(","))
            requiredBundleString = requiredBundleString.substring(bundle.length())

            int quoteCount = countQuotes(bundle)
            while(quoteCount % 2 != 0) {
                int nextIndexOfComma = requiredBundleString.indexOf(",", 1)
                if(nextIndexOfComma == -1)
                    nextIndexOfComma = requiredBundleString.length()

                bundle += requiredBundleString.substring(0, nextIndexOfComma)
                quoteCount = countQuotes(bundle)

                requiredBundleString = requiredBundleString.substring(nextIndexOfComma)
            }

            bundleStrings.add(bundle)
            if(requiredBundleString.length() == 0) {
                break
            }

            requiredBundleString = requiredBundleString.substring(1)
        }

        List<RequiredBundle> result = []
        for(String bundleString : bundleStrings) {
            result.add(parseRequireBundle(bundleString))
        }

        return result
    }

    private int countQuotes(String txt) {
        return txt.length() - txt.replace("\"", "").length()
    }

    private RequiredBundle parseRequireBundle(String s) {
        RequiredBundle bundle = new RequiredBundle()

        if(!s.contains(";")) {
            bundle.bundleName = s
            return bundle
        }

        String[] sections = s.split(";")
        bundle.bundleName = sections[0]
        for(int i=1;i<sections.length;i++) {
            String element = sections[i]

            String key = element.substring(0, element.indexOf("="))
            element = element.substring(key.length() + 1)
            if(element.startsWith("\"")) {
                element = element.substring(1, element.length()-1)
            }

            bundle.attributes.put(key, element)
        }

        return bundle
    }
}
