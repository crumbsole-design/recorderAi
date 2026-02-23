# Test E2E: Recopilación de Datos en 2 Minutos

## 📋 Descripción

Este test E2E (End-to-End) verifica que la aplicación pueda recopilar datos correctamente durante 2 minutos. El flujo es:

1. **Crear una estancia** denominada "Test Room E2E"
2. **Seleccionar la estancia**
3. **Entrar en la celda 1**
4. **Configurar la celda como enlazable** con nombre "Test Cell 1"
5. **Iniciar recopilación de datos**
6. **Esperar 2 minutos** (120 segundos) para que se recopilen datos
7. **Detener recopilación**
8. **Verificar que se recopilaron al menos 2 registros de cada tipo**:
   - ✅ WiFi: >= 2 registros
   - ✅ Cell (Celular): >= 2 registros
   - ✅ Bluetooth: >= 2 registros
   - ✅ Magnetometer: >= 2 registros

## 🎯 Qué mide este test

- **Integración completa UI + Servicio + BD**
- **Recopilación de datos en tiempo real**
- **Persistencia de datos en BD**
- **Correctos tipos de datos** (WIFI, CELL, BLUETOOTH, MAGNETOMETER)

## 🚀 Cómo ejecutar el test

### Opción 1: Desde Android Studio

1. Abre el proyecto en Android Studio
2. Navega a: `app/src/androidTest/kotlin/com/example/recorderai/DataCollectionE2EFullFlowTest.kt`
3. Click derecho en la clase o en el método `testFullDataCollectionFlowWith2MinutesCollection`
4. Selecciona: **"Run 'DataCollectionE2EFullFlowTest'"** o **"Run 'testFullDataCollectionFlowWith2MinutesCollection()'"**

### Opción 2: Desde terminal

```bash
# Asegúrate de estar en la raíz del proyecto
cd /home/crumbsole/AndroidStudioProjects/recorderAi

# Ejecutar el test específico
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.recorderai.DataCollectionE2EFullFlowTest#testFullDataCollectionFlowWith2MinutesCollection

# O ejecutar todos los tests de ese archivo
./gradlew connectedAndroidTest --tests "com.example.recorderai.DataCollectionE2EFullFlowTest*"
```

## ⚠️ Requisitos

- **Dispositivo físico o emulador conectado** (el test usa la UI de Compose)
- **Permisos**: El test pedirá permisos para:
  - Ubicación (GPS)
  - Micrófono (audio)
  - Bluetooth
  - WiFi
  - Estado del teléfono (cell info)
  
  **Debes aceptar todos los permisos cuando aparezca el diálogo**

- **WiFi y Bluetooth deben estar habilitados** en el dispositivo para que se recopilen datos
- **Al menos 2 minutos de espera** - ¡No interrumpas el test!

## 📊 Qué esperar

Durante la ejecución verás en la consola:

```
=== PASO 1: Crear estancia ===
✅ Estancia creada: Test Room E2E
=== PASO 2: Seleccionar estancia ===
✅ Estancia seleccionada
=== PASO 3: Entrar en celda 1 ===
✅ Celda 1 seleccionada
=== PASO 4: Configurar como enlazable ===
✅ Celda 1 configurada como enlazable
=== PASO 5: Iniciar recopilación de datos ===
✅ Recopilación iniciada
⏳ Esperando 2 minutos (120 segundos) para recopilar datos...
✅ 2 minutos completados
=== PASO 6: Detener recopilación ===
✅ Recopilación detenida
=== PASO 7: Verificar datos recopilados ===
📍 Room ID: 1
📍 Session ID: 1

📊 Datos recopilados:
   - WIFI: 4 registros
   - CELL: 4 registros
   - BLUETOOTH: 12 registros
   - MAGNETOMETER: 12 registros
   - TOTAL: 32 registros

✅ Verificaciones:
   ✅ WIFI: 4 >= 2
   ✅ CELL: 4 >= 2
   ✅ BLUETOOTH: 12 >= 2
   ✅ MAGNETOMETER: 12 >= 2

🎉 TEST PASADO - Todos los datos se recopilaron correctamente
```

## 🔍 Solución de problemas

### El test falla porque los datos no aparecen
**Posibles causas:**
1. **WiFi/Bluetooth no habilitado**: Asegúrate de que WiFi y Bluetooth estén activados
2. **Permisos rechazados**: El test necesita aceptar los permisos que aparezcan
3. **Dispositivo sin cobertura celular**: El test usa datos del teléfono, necesita acceso a la información de celdas
4. **La estancia se crea pero no aparece en la lista**: Espera más tiempo, la UI puede tardar

### El test se detiene a mitad de camino
- Probablemente fue interrumpido mientras se recopilaban datos (los 2 minutos)
- Ejecuta de nuevo sin interrupciones

### Los conteos de datos son bajos
- **WiFi/CELL**: Se recopilan cada ~30 segundos (menos ciclos en 2 minutos)
- **BLUETOOTH/MAGNETOMETER**: Se recopilan cada ~10 segundos (más ciclos)
- El test requiere al menos 2 de cada, pero deberías tener más

## 📈 Flujo esperado de recopilación

En 120 segundos (2 minutos):

| Tipo | Ciclo | Ciclos en 2 min | Registros esperados |
|------|-------|-----------------|---------------------|
| BLUETOOTH | ~6 seg | ~20 | ~20 |
| MAGNETOMETER | ~6 seg | ~20 | ~20 |
| WIFI | ~25 seg | ~5 | ~5 |
| CELL | ~25 seg | ~5 | ~5 |
| **TOTAL** | - | - | **~50** |

## 🧹 Limpieza automática

El test automáticamente limpia la BD después de terminar (elimina la estancia y todos los datos creados).

## 📝 Notas

- Este es el **test más completo** que hay
- Verifica que todo el flujo (UI + Servicio + BD) funcione correctamente
- Si este test pasa, la recopilación de datos está funcionando perfectamente

