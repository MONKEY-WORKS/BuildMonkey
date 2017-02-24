# BuildMonkey
[![Build Status](https://travis-ci.org/MONKEY-WORKS/BuildMonkey.svg?branch=master)](https://travis-ci.org/MONKEY-WORKS/BuildMonkey)

Build artefacts for creating an Eclipse RCP application with gradle.
This repository will be filled in the next few days with the initial version of the plugins.
A first release will be made after the Hackathon: Make Eclipse RCP gradle again!
Join this event at https://www.xing.com/events/make-eclipse-rcp-gradle-again-1778922

# Subprojects
## Gradle artifact deployer

A tool to easify uploading of generic artifacts to artifactory repositories.

## Gradle build

A plugin to use the Manifest.MF of eclipse plugins for gradle dependency resolution. So no additional modification of `build.gradle` files anymore.

## Gradle p2

Transformation between m2 (maven) and p2 (eclipse world) repositories made easy.

## Gradle pde

Plugin to materialize eclipse rcp based products and execute eclipse plugin tests.
