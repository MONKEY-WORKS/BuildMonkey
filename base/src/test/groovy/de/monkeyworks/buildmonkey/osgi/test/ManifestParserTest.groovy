package de.monkeyworks.buildmonkey.osgi.test

import de.monkeyworks.buildmonkey.osgi.ManifestParser
import org.junit.Test

import static org.junit.Assert.*

import java.util.jar.Manifest

/**
 * Created by jake on 02.06.17.
 */
class ManifestParserTest {

    @Test
    void testManifestParsing() {
        Manifest manifest = new Manifest()
        manifest.getMainAttributes().putValue("Require-Bundle", "bla,test,asd;bundle-version=\"1.4.0\",noob")

        ManifestParser parser = new ManifestParser(manifest)

        def requiredBundles = parser.parseRequireBundles()

        assertEquals("bundle should match", "bla", requiredBundles.get(0).bundleName)
        assertEquals("bundle should match", "test", requiredBundles.get(1).bundleName)
        assertEquals("bundle should match", "asd", requiredBundles.get(2).bundleName)
        assertEquals("bundle should match", "noob", requiredBundles.get(3).bundleName)
    }

    @Test
    void testManifestParsing2() {
        Manifest manifest = new Manifest()
        manifest.getMainAttributes().putValue("Require-Bundle", "test,asd;bundle-version=\"[1.4.0,]\",noob")

        ManifestParser parser = new ManifestParser(manifest)

        def requiredBundles = parser.parseRequireBundles()

        assertEquals("bundle should match", "test", requiredBundles.get(0).bundleName)
        assertEquals("bundle should match", "asd", requiredBundles.get(1).bundleName)
        assertEquals("bundle should match", "noob", requiredBundles.get(2).bundleName)

        assertTrue("dependency asd should containt version", requiredBundles.get(1).attributes.containsKey("bundle-version"))
        assertEquals("version was not derived correctly", "[1.4.0,]", requiredBundles.get(1).attributes.get("bundle-version"))
    }
}
