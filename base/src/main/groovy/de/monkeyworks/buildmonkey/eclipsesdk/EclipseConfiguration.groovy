package de.monkeyworks.buildmonkey.eclipsesdk

/**
 * Created by jake on 24/02/2017.
 */
class EclipseConfiguration {

    def eclipseSdkURL
    def eclipseVersion
    def launcherVersion

    EclipseConfiguration() {
        eclipseSdkURL = 'http://ftp-stud.hs-esslingen.de/Mirrors/eclipse/eclipse/downloads/drops4/R-4.6.1-201609071200'
        eclipseVersion = '4.6.1'
        launcherVersion = '1.3.200.v20160318-1642'
    }
}
