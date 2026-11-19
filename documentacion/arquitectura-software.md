# Arquitectura de software

Diagrama editable: [arquitectura-software.drawio](arquitectura-software.drawio) (se abre en https://app.diagrams.net).

**En lenguaje llano.** La app "Mi Tienda de Super" está organizada en tres capas que se hablan de arriba hacia abajo: lo que ve el usuario (pantallas), las reglas del negocio (casos de uso) y el almacenamiento y servicios del teléfono (datos). Toda la información vive primero en el teléfono, en una base de datos que funciona sin internet. La nube (Firebase) es un respaldo opcional: si falla o no hay conexión, la app sigue funcionando igual.

**Detalle técnico.** Kotlin, Jetpack Compose, patrón MVVM con Clean Architecture simplificada, Hilt para inyección de dependencias, paquete base `com.app.marketlist`.

## Capas y componentes

### Capa de presentación (`ui/`)

| Componente | Qué hace |
|---|---|
| Pantallas Compose (`ui/screens/*`) | Home (productos), Tiendas, Listas de una tienda (`StoreListsScreen`), Historial de listas, Detalle de lista, Formulario y detalle de producto, Escáner de código de barras, Lectura de ticket (`TicketScanScreen`), Lectura de lista por foto (`ListSheetScreen`, desde Listas mensuales > "Leer lista"), Historial de precios, Exportar. Los componentes comunes están en `ui/components`. |
| Navegación (`ui/navigation`) | `Screen` define las rutas, `NavGraph` las conecta y `BottomNavBar` es la barra inferior. |
| ViewModels (`@HiltViewModel`) | Guardan el estado de cada pantalla con `StateFlow`, reciben los eventos del usuario y llaman a casos de uso o repositorios. `HomeViewModel`, `StoreViewModel`, `StoreListsViewModel`, `MonthlyListViewModel`, `ListDetailViewModel`, `ProductViewModel`, `ProductDetailViewModel`, `ScannerViewModel`, `TicketScanViewModel`, `ListSheetViewModel`, `ExportViewModel`. |

### Capa de dominio (`domain/`)

Kotlin sin dependencias de Android.

| Componente | Qué hace |
|---|---|
| Casos de uso (`domain/usecase`) | Reglas de negocio y validaciones: `CreateMonthlyListUseCase`, `CopyMonthlyListUseCase`, `CreateProductUseCase`, `UpdateProductUseCase`, `DeleteProductUseCase`, `CreateStoreUseCase`, `DeleteStoreUseCase`, `RegisterPriceUseCase`, `GetPriceComparisonUseCase`, `SearchProductsUseCase`, `ScanBarcodeUseCase`, `ImportTicketUseCase`, `ImportListSheetUseCase`, `ImportProductsFromExcelUseCase`, `ExportToExcelUseCase`, `SyncDataUseCase`. También utilidades puras: `ListNameSuggester` (sugiere el mes siguiente), `TicketParser` (interpreta el texto OCR; agrupa por departamento y une descripción y precio de renglones distintos), `SheetListParser` (interpreta una hoja de lista: quita viñetas y cantidades, precio opcional al final, sin duplicados), `ProductMatcher` (empareja artículos del ticket con productos), `TicketPeriodResolver` (elige el período libre de la lista), `BarcodeValidator`. |
| Modelos (`domain/model`) | `Product`, `Store`, `StoreType`, `MonthlyList`, `PriceRecord`, `TicketImport`, `ListSheetImport`, `ExcelImport`, `SyncResult`, `AppError`. |
| Interfaces de repositorio (`domain/repository`) | Contratos: `ProductRepository`, `StoreRepository`, `MonthlyListRepository`, `PriceRepository`, `TicketImportRepository`, `ListSheetImportRepository`, `ExcelImportRepository`, `ExcelExportRepository`, `SyncRepository`. El dominio solo conoce estas interfaces. |

### Capa de datos (`data/`)

| Componente | Qué hace |
|---|---|
| Repositorios (`data/repository/*Impl`) | Implementan las interfaces del dominio. Traducen entre entidades Room y modelos (`EntityMappers`). `TicketImportRepositoryImpl` guarda un ticket completo en una sola transacción; `ListSheetImportRepositoryImpl` hace lo mismo con una hoja de lista (solo guarda los precios confirmados por el usuario); `ExcelImportRepositoryImpl` guarda los productos leídos de un Excel. `PriceRepository.getLatestPrice` devuelve el último precio registrado de un producto. |
| Room (`data/local/db`) | `AppDatabase` v2 con 7 entidades y sus DAOs, migración `MIGRATION_1_2` y `PrepopulateCallback` (tiendas iniciales). Es la fuente de verdad. Ver [base-de-datos-er.md](base-de-datos-er.md). |
| Fotos (`data/local/photo`) | `PhotoManager` y `PhotoFileProvider` guardan fotos de productos y archivos temporales. |
| Cámara y escáner (`data/camera`) | `CameraManager` configura CameraX (vista previa y análisis de imagen). `BarcodeAnalyzer` decodifica EAN-13, UPC-A y Code 128 con ML Kit en el teléfono. `BarcodeTimeoutHandler` pasa a captura manual a los 5 segundos sin lectura. |
| OCR de tickets (`data/ocr`) | `TicketTextRecognizer` usa ML Kit text-recognition (modelo latino incluido en la app) y agrupa los fragmentos por renglón: nombre a la izquierda, precio a la derecha. |
| Exportación Excel (`data/export`) | `ExcelImporter` lee la hoja "Lista" con la misma plantilla (importar productos desde Excel). `ExcelExporter` crea `Lista_[Mes]_[Año].xlsx` con poi-android (columnas Producto, Categoría, Tienda, Precio, Fecha_Registro) en `cache/exports`. |
| Sincronización (`data/remote/firebase`) | `SyncScheduler` pide la sincronización (al arrancar, al recuperar red y tras cambios). `SyncRepositoryImpl.scheduleSyncWork` encola un trabajo único `market_list_sync` en WorkManager (requiere red, reintento exponencial). `SyncWorker` lo ejecuta y reintenta hasta 5 veces. `FirestoreDataSource` autentica de forma anónima y escribe en lotes de hasta 500 documentos en `users/{uid}/products`, `stores`, `monthlyLists` y `priceRecords`. `ConflictResolver` aplica "gana la última modificación". Los errores se ignoran en la UI: la sincronización es solo respaldo. |

### Inyección de dependencias (`di/`)

Hilt (`@HiltAndroidApp` en `MarketListApplication`):

- `DatabaseModule`: crea `AppDatabase` (con migración y datos iniciales) y cada DAO.
- `RepositoryModule`: `@Binds` de cada interfaz a su implementación.
- `FirebaseModule`: `FirebaseFirestore` y `FirebaseAuth`.
- `WorkManagerModule`: `WorkManager`. La aplicación usa `HiltWorkerFactory` para que `SyncWorker` reciba sus dependencias.

### Servicios externos

Firebase (Auth anónima y Firestore, opcional) y el archivo `.xlsx` generado, que el usuario comparte desde la pantalla Exportar. Sin el archivo `google-services.json` real, la sincronización no llega a la nube y la app sigue funcionando con Room.

## Flujos del diagrama

Cada flecha del diagrama lleva su código (letra del flujo y número de paso).

### Flujo A: guardar o consultar datos (azul)

1. **A1** La pantalla envía un evento del usuario al ViewModel (por ejemplo, "guardar precio").
2. **A2** El ViewModel llama al caso de uso, que valida las reglas (precio entre 0.01 y 999999.99, nombres de 2 a 100 caracteres, mes único por lista).
3. **A3** El caso de uso pide o entrega datos a través de la interfaz de repositorio.
4. **A4** Hilt entrega la implementación del repositorio que cumple esa interfaz.
5. **A5** El repositorio lee y escribe en Room mediante los DAOs. Las consultas devuelven `Flow`, así que el ViewModel actualiza el estado y la pantalla se redibuja sola.

### Flujo B: escanear código de barras (verde)

1. **B1** La pantalla del escáner abre la cámara con CameraX y analiza los fotogramas con ML Kit. Al leer un código, `ScannerViewModel` lo valida con `ScanBarcodeUseCase` y continúa con el flujo A (buscar el producto o abrir el formulario con el código). Si pasan 5 segundos sin lectura, se ofrece escribirlo a mano.

### Flujo C: leer un ticket de compra (naranja)

1. **C1** `TicketScanViewModel` recibe la foto del ticket y la pasa a `TicketTextRecognizer` (OCR en el teléfono).
2. El texto se interpreta con `TicketParser`, se emparejan artículos con `ProductMatcher` y se sugiere tienda y período (`TicketPeriodResolver`).
3. El usuario revisa y corrige tienda, nombre de lista y a qué producto corresponde cada precio.
4. Al confirmar, `ImportTicketUseCase` valida y `TicketImportRepositoryImpl` crea la lista, los productos nuevos y los precios en una sola transacción de Room (flujo A, pasos A2 a A5). Si algo falla, no queda nada a medias.

### Flujo D: exportar a Excel (morado)

1. **D1** `ExportViewModel` llama a `ExportToExcelUseCase`, que usa `ExcelExportRepositoryImpl`. Este lee la lista, sus productos, tiendas y precios, y se los pasa a `ExcelExporter`.
2. **D2** `ExcelExporter` genera el `.xlsx` en la caché de la app y la pantalla lo ofrece para compartir.

### Flujo F: leer una lista por foto (Listas mensuales > Leer lista)

1. `ListSheetViewModel` recibe la foto (cámara o galería, lista impresa o escrita a mano) y la pasa a `TicketTextRecognizer` (mismo OCR; la calidad del manuscrito depende de la letra).
2. `SheetListParser` obtiene los artículos y `ProductMatcher` sugiere un producto registrado parecido. Para cada emparejamiento, `PriceRepository.getLatestPrice` da el último precio y su tienda; se muestra como "Sugerido $X · Tienda · fecha" con el botón "Usar".
3. El usuario revisa, cambia el producto, edita precios y elige una tienda de referencia (opcional, obligatoria solo si hay precios escritos sin tienda sugerida).
4. `ImportListSheetUseCase` valida y `ListSheetImportRepositoryImpl` crea, en una transacción, una lista nueva en el siguiente período libre (`ListNameSuggester`), los productos nuevos (categoría "Otros") y solo los precios aceptados. Un precio sugerido que no se usa no se guarda.

### Otras reglas

- **Inicio (Home)**: muestra todos los productos registrados; un interruptor "Solo lista activa" limita a la lista activa.
- **Tickets por departamento**: `DepartmentCategory` reconoce los encabezados de departamento y los usa como categoría de los productos nuevos; el precio en un renglón aparte se une a su descripción.
### Flujo E: sincronización de respaldo (rojo)

1. **E1** Tras guardar cambios, los ViewModels llaman a `SyncScheduler.requestSync()`. También se pide al arrancar la app y cuando vuelve la conexión. WorkManager encola el trabajo `market_list_sync`.
2. **E2** `SyncWorker` ejecuta `SyncRepositoryImpl.syncToCloud()`, que inicia sesión anónima y lee de Room las filas con `sync_status = PENDING` (productos, tiendas, listas y precios).
3. **E3** `FirestoreDataSource` las sube en lotes. Al terminar, marca las filas como `SYNCED` y guarda la hora en `sync_metadata`. Si falla, WorkManager reintenta con espera creciente (hasta 5 intentos). Si Firestore responde que se superó la cuota gratuita, se guarda un aviso para la UI.

## Decisiones de diseño

- **Offline primero:** Room es la fuente de verdad. La nube no es necesaria para usar la app.
- **El dominio no depende de Android ni de Room:** se puede probar con facilidad y se pueden cambiar las implementaciones sin tocar las reglas.
- **Todo el reconocimiento (códigos de barras y texto) ocurre en el teléfono:** no se envían imágenes a ningún servidor.
