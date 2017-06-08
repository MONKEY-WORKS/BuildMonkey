package de.monkeyworks.buildmonkey.osgi

/**
 * Created by jake on 02.06.17.
 */
class ManifestUpdaterExtension {

    String version

    boolean addQualifier = true

    Closure dependencyUpdates


    void depdencyUpdate(Closure closure) {
        dependencyUpdates = closure
    }
}
