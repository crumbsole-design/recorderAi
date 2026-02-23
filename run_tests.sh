#!/bin/bash
# Script mejorado para ejecutar tests de RecorderAI
MODE=$1
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
case $MODE in
    "unit")
        echo "🧪 Ejecutando tests unitarios..."
        ./gradlew testDebugUnitTest --console=plain
        ;;
    "integration")
        echo "📱 Ejecutando tests de integración..."
        ./gradlew connectedDebugAndroidTest --tests "DataCollectionIntegrationTest"
        ;;
    "e2e")
        echo "🎭 Ejecutando tests E2E..."
        ./gradlew connectedDebugAndroidTest --tests "CellDataCollectionE2ETest"
        ;;
    "all")
        echo "🚀 Ejecutando TODOS los tests..."
        ./gradlew testDebugUnitTest && ./gradlew connectedDebugAndroidTest
        ;;
    "coverage")
        echo "📊 Generando reporte de cobertura..."
        ./gradlew koverHtmlReportDebug
        echo "Ver reporte: app/build/reports/kover/htmlDebug/index.html"
        ;;
    "clean")
        echo "🧹 Limpiando proyecto..."
        ./gradlew clean
        ;;
    *)
        echo "Uso: ./run_tests.sh [unit|integration|e2e|all|coverage|clean]"
        exit 1
        ;;
esac
