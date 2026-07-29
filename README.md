# Market Shopping List (Lista de Mercado)

Aplicación Android de lista de compras de mercado reutilizable mensualmente. Permite gestionar productos organizados por tipo de tienda, capturar información mediante cámara (código de barras y precio) sin conexión a internet, registrar precios variables por ubicación, sincronizar con la nube y exportar listas a Excel.

## Requisitos

- Android Studio Hedgehog (2023.1.1) o superior
- JDK 17
- Android SDK 34
- Dispositivo o emulador con Android 8.0 (API 26) o superior

## Compilación

1. Clonar el repositorio:
   ```bash
   git clone <url-del-repositorio>
   cd appmarketlist
   ```

2. Abrir el proyecto en Android Studio o compilar desde terminal:
   ```bash
   ./gradlew assembleDebug
   ```

3. Instalar en dispositivo conectado:
   ```bash
   ./gradlew installDebug
   ```

## Estructura del Proyecto

```
com.app.marketlist/
├── di/          # Módulos de inyección de dependencias (Hilt)
├── ui/          # Capa de presentación (Compose screens, ViewModels)
├── domain/      # Capa de dominio (modelos, casos de uso, interfaces)
└── data/        # Capa de datos (Room, Firebase, cámara, exportación)
```

## Tecnologías

- **Lenguaje**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Base de datos local**: Room (SQLite)
- **Sincronización**: Firebase Firestore (tier gratuito)
- **Escáner**: CameraX + ML Kit Barcode Scanning
- **Exportación**: Apache POI (Excel .xlsx)
- **DI**: Hilt
- **Async**: Kotlin Coroutines + Flow
- **Testing**: JUnit + Kotest Property Testing

## Licencia

Proyecto privado. Todos los derechos reservados.
