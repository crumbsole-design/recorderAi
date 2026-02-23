#!/bin/bash

# Script para ejecutar tests E2E de CellDataCollection
# Fecha: 2026-02-23

echo "=========================================="
echo "Ejecutando Tests E2E Corregidos"
echo "=========================================="
echo ""

# Colores para output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Compilando proyecto...${NC}"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

if [ $? -ne 0 ]; then
    echo -e "${RED}Error en compilación${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}Ejecutando tests E2E...${NC}"
echo ""

# Ejecutar tests con timeout de 5 minutos
# Para tests instrumentados Android, usamos -Pandroid.testInstrumentationRunnerArguments.class
timeout 300 ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.example.recorderai.CellDataCollectionE2ETest

TEST_RESULT=$?

echo ""
echo "=========================================="

if [ $TEST_RESULT -eq 0 ]; then
    echo -e "${GREEN}✓ TESTS PASARON EXITOSAMENTE${NC}"
    echo ""
    echo "Resultados detallados en:"
    echo "  app/build/reports/androidTests/connected/index.html"
else
    echo -e "${RED}✗ TESTS FALLARON${NC}"
    echo ""
    echo "Revisa los logs en:"
    echo "  app/build/reports/androidTests/connected/index.html"
    echo "  app/build/outputs/androidTest-results/"
fi

echo "=========================================="
echo ""

# Mostrar resumen de últimas líneas del log
if [ -f "app/build/outputs/androidTest-results/connected/TEST-*.xml" ]; then
    echo "Resumen de resultados:"
    grep -E "(testcase|failure)" app/build/outputs/androidTest-results/connected/TEST-*.xml | tail -20
fi

exit $TEST_RESULT

n