# 🚀 Guía Rápida: Ejecutar el Test E2E de 2 Minutos

## ⏱️ Tiempo Total: ~3-4 minutos

### 📱 Requisitos Previos

1. **Dispositivo Android conectado** (físico o emulador)
   - Debe tener WiFi habilitado
   - Debe tener Bluetooth habilitado
   - Debe tener ubicación habilitada (GPS)

2. **Android Studio abierto** con el proyecto

3. **Gradle sincronizado** (Build → Sync Now si es necesario)

---

## 📋 Pasos para Ejecutar el Test

### Paso 1: Abre el archivo del test

```
Navega a: app/src/androidTest/kotlin/com/example/recorderai/DataCollectionE2EFullFlowTest.kt
```

O directamente:
- **Ctrl+Shift+O** (macOS: **Cmd+Shift+O**)
- Busca: `DataCollectionE2EFullFlowTest`
- Presiona Enter

### Paso 2: Ejecuta el test

**Opción A: Desde Android Studio (Recomendado)**

1. Busca la función: `testFullDataCollectionFlowWith2MinutesCollection()`
2. Haz **click derecho** en el nombre de la función
3. Selecciona: **Run 'testFullDataCollectionFlowWith2MinutesCollection()'**

![run-test](https://example.com/run-test.png)

**Opción B: Desde el margen izquierdo**

1. Busca la función
2. Haz **click izquierdo** en el ícono **▶ (play)** que aparece en el margen

**Opción C: Desde terminal**

```bash
cd /home/crumbsole/AndroidStudioProjects/recorderAi

# Ejecutar el test específico
./gradlew connectedAndroidTest \
  --tests "com.example.recorderai.DataCollectionE2EFullFlowTest.testFullDataCollectionFlowWith2MinutesCollection"
```

### Paso 3: Selecciona el dispositivo (si tienes múltiples)

- Aparecerá un diálogo pidiendo que selecciones dispositivo
- Elige tu dispositivo/emulador
- Haz click en **OK**

### Paso 4: ¡Ahora viene lo importante! 🎯

El test comenzará a ejecutarse. Verás en la pantalla:

```
[PASO 1] Creando estancia "Test Room E2E"...
[PASO 2] Seleccionando estancia...
[PASO 3] Entrando en celda 1...
[PASO 4] Configurando como enlazable...
[PASO 5] Iniciando recopilación...
```

**⚠️ MUY IMPORTANTE**: 
- **El test pedirá permisos** → Debes aceptar TODOS los permisos que aparezcan
- La recopilación iniciará automáticamente
- **No interrumpas el test durante los próximos 2 MINUTOS**

### Paso 5: Espera 2 minutos ⏳

Durante 120 segundos (2 minutos) el test estará recopilando datos:
- WiFi
- Bluetooth
- Magnetómetro
- Información de celdas

**Verás en Logcat**:
```
⏳ Esperando 2 minutos (120 segundos) para recopilar datos...
```

### Paso 6: El test se detiene automáticamente

Después de 2 minutos:

```
✅ 2 minutos completados
✅ Recopilación detenida
```

### Paso 7: Verifica los resultados

En la ventana de **Logcat** verás algo como:

```
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

**Si ves esto → ✅ TEST PASÓ CORRECTAMENTE**

---

## 🐛 ¿Qué hacer si el test FALLA?

### ❌ "Test FAILED - Se esperaban al menos 2 registros de WIFI"

**Causa**: No se recopilaron datos de WiFi

**Solución**:
1. Verifica que WiFi esté **habilitado** en el dispositivo
2. Verifica que estés **conectado a una red WiFi**
3. Vuelve a ejecutar el test

### ❌ "Test FAILED - Se esperaban al menos 2 registros de CELL"

**Causa**: No se recopilaron datos de celda

**Solución**:
1. Verifica que tengas **cobertura de red celular** (SIM activa)
2. En emulador: Los datos de celda pueden no estar disponibles
3. Prueba con un dispositivo físico

### ❌ "Test FAILED - Se esperaban al menos 2 registros de BLUETOOTH"

**Causa**: Bluetooth no está habilitado o sin dispositivos cercanos

**Solución**:
1. Verifica que Bluetooth esté **habilitado**
2. Verifica que haya **dispositivos Bluetooth cercanos**
3. Si no hay: El test aún puede pasar porque espera ">= 2"

### ❌ El test se detiene a mitad de camino

**Causa**: Probablemente fue interrumpido

**Solución**:
- No cierres el logcat ni presiones Stop
- El test se ejecutará completamente
- Si presionas Stop, verás: "Test interrupted"

### ❌ "Permission denied"

**Causa**: No aceptaste los permisos

**Solución**:
1. Cuando aparezca el diálogo de permisos **debes aceptar**
2. Especialmente: Ubicación, Micrófono, Bluetooth
3. Si los rechazas, el test fallará

---

## 📊 Dashboard de Ejecución

Durante la ejecución verás en Logcat en **tiempo real**:

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
   - WIFI: X registros
   - CELL: X registros
   - BLUETOOTH: X registros
   - MAGNETOMETER: X registros
   - TOTAL: X registros

✅ Verificaciones:
   ✅ WIFI: X >= 2
   ✅ CELL: X >= 2
   ✅ BLUETOOTH: X >= 2
   ✅ MAGNETOMETER: X >= 2

🎉 TEST PASADO - Todos los datos se recopilaron correctamente
```

---

## 💡 Consejos

✅ **Antes de ejecutar**:
- Asegúrate de que WiFi y Bluetooth estén HABILITADOS
- Asegúrate de que haya WiFi disponible para conectar
- Cierra otras aplicaciones que usen mucha memoria
- Ten el dispositivo completamente cargado (o enchufa)

✅ **Durante la ejecución**:
- No cierres la aplicación
- No presiones el botón de atrás (back)
- No interrumpas el terminal/logcat
- Deja que la pantalla se mantenga encendida

✅ **Después**:
- Los datos de prueba se limpian automáticamente
- Puedes ejecutar el test múltiples veces
- Cada ejecución es independiente

---

## 🎯 ¿Qué significa que el test pase?

Cuando el test **PASA**, significa:

✅ La aplicación está recopilando datos correctamente
✅ Los datos se guardan en la BD con los tipos correctos
✅ Todos los sensores están funcionando
✅ La integración UI + Servicio + BD es correcta
✅ **Los números ahora aparecerán en la UI** 🎉

---

## 📞 Preguntas Frecuentes

**P: ¿Cuánto tiempo toma el test?**
R: 2 minutos de recopilación + 1 minuto de setup/teardown = ~3 minutos total

**P: ¿Puedo interrumpir el test?**
R: No, debes esperar los 2 minutos completos para que funcione

**P: ¿Qué pasa si el emulador no tiene WiFi?**
R: Es normal, pero el test intentará recopilar de todas formas

**P: ¿Se borra la estancia después?**
R: Sí, se limpia automáticamente

**P: ¿Puedo ejecutar el test varias veces?**
R: Sí, sin problemas

---

## ✨ ¡Listo!

Ahora ejecuta el test y verifica que los datos se recopilan correctamente. 🚀

