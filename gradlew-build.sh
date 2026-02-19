#!/bin/bash
# Script para ejecutar gradlew con el JDK correcto
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew "$@"
