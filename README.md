# Attention
During the last 2 years the MONKEY WORKS GmbH was integrated into the Elco Industrie Automation GmbH and changing the focus of development. Therefore we switched to GoLang and don't use Gradle - Eclipse anymore. We have no resources to support this project any longer. There are some forks, so it seems, that some of you want to proceed with this project. If it is so, then please contact me at m.barth@elco-automation.de and I will grant you to the project as lead. I really hope, that this project becomes a success.

Sincerly

Michael Barth

# BuildMonkey
[![Build Status](https://travis-ci.org/MONKEY-WORKS/BuildMonkey.svg?branch=master)](https://travis-ci.org/MONKEY-WORKS/BuildMonkey)

Build artefacts for creating an Eclipse RCP application with gradle.

You can contact us per Gitter: https://gitter.im/BuildMonkeys

Find the documentation  here: https://github.com/MONKEY-WORKS/BuildMonkey/wiki

# Subprojects
## Gradle artifact deployer

A tool to easify uploading of generic artifacts to artifactory repositories.

## Gradle build

A plugin to use the Manifest.MF of eclipse plugins for gradle dependency resolution. So no additional modification of `build.gradle` files anymore.

## Gradle p2

Transformation between m2 (maven) and p2 (eclipse world) repositories made easy.

## Gradle pde

Plugin to materialize eclipse rcp based products and execute eclipse plugin tests.
