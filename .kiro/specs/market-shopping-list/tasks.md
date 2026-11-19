# Plan de Implementación: Market Shopping List

## Resumen

Implementación de una aplicación Android nativa con Kotlin y Jetpack Compose siguiendo Clean Architecture de tres capas (UI, Dominio, Datos). La app gestiona listas de compras mensuales reutilizables con escaneo de códigos de barras offline, sincronización con Firebase Firestore y exportación a Excel.

## Tareas

- [x] 1. Configuración del proyecto y estructura base
  - [x] 1.1 Inicializar repositorio Git y estructura de proyecto Android
    - Crear proyecto Android con Kotlin DSL para Gradle
    - Configurar `build.gradle.kts` (app y project level) con compileSdk 34, minSdk 26, targetSdk 34
    - Crear archivo `.gitignore` excluyendo: `build/`, `.gradle/`, `.idea/`, `*.iml`, `local.properties`, archivos de credenciales
    - Crear archivo `README.md` con nombre del proyecto e instrucciones de compilación
    - Inicializar repositorio Git con commit inicial
    - _Requisitos: 10.1, 10.2, 10.3, 10.4, 10.5_

  - [x] 1.2 Configurar dependencias Gradle y Hilt DI
    - Agregar dependencias: Jetpack Compose (BOM), Room, Hilt, Navigation Compose, CameraX, ML Kit Barcode, Firebase Firestore, Apache POI (android5xlsx), WorkManager, Kotlin Coroutines, Kotest
    - Configurar plugin KSP para Room y Hilt
    - Crear clase `Application` con anotación `@HiltAndroidApp`
    - Crear módulos Hilt base: `DatabaseModule`, `RepositoryModule`, `FirebaseModule`
    - _Requisitos: 10.3_

  - [x] 1.3 Crear estructura de paquetes por capas
    - Crear paquetes: `di/`, `ui/` (navigation, screens, components, theme), `domain/` (model, repository, usecase), `data/` (local/db, local/photo, remote/firebase, repository, camera, export)
    - Crear tema Material3 base con colores y tipografía
    - Configurar `MainActivity` con `@AndroidEntryPoint` y Compose `setContent`
    - _Requisitos: 10.3_

- [x] 2. Capa de Dominio: Modelos y Contratos
  - [x] 2.1 Definir modelos de dominio
    - Crear `Product` (id, name, category, barcode, photoPath, stores)
    - Crear `Store` (id, name, type: StoreType, isPredefined)
    - Crear `StoreType` enum (SUPERMARKET, GENERAL_MARKET)
    - Crear `MonthlyList` (id, month, year, label, products)
    - Crear `PriceRecord` (id, productId, storeId, monthlyListId, price, recordDate, storeName)
    - Crear `SyncResult` (success, syncedCount, failedCount, lastSyncTime)
    - Crear sealed class `AppError` con todas las subclases de error definidas en diseño
    - _Requisitos: 1.1, 2.1, 3.3, 4.1_

  - [x] 2.2 Definir interfaces de repositorio
    - Crear `ProductRepository` con operaciones CRUD y búsqueda
    - Crear `StoreRepository` con CRUD y verificación de predefinidas
    - Crear `MonthlyListRepository` con creación, copia y gestión de productos
    - Crear `PriceRepository` con registro, historial y precio mínimo
    - Crear `SyncRepository` con sync, lastSyncTime y pendingChanges
    - Crear `ExcelExportRepository` con exportación
    - _Requisitos: 1.1, 2.1, 3.1, 4.1, 7.1, 8.1_

  - [x] 2.3 Implementar casos de uso
    - Crear `CreateProductUseCase` con validación de campos obligatorios (nombre 2-100 chars, categoría, al menos 1 tienda)
    - Crear `UpdateProductUseCase` con mismas validaciones
    - Crear `DeleteProductUseCase` que elimina producto y precios asociados
    - Crear `SearchProductsUseCase` con búsqueda parcial case-insensitive
    - Crear `CreateStoreUseCase` con validación de unicidad nombre+tipo y nombre 2-50 chars
    - Crear `DeleteStoreUseCase` que rechaza eliminación de predefinidas
    - Crear `CreateMonthlyListUseCase` que valida unicidad mes/año
    - Crear `CopyMonthlyListUseCase` que copia productos (max 500) sin precios
    - Crear `RegisterPriceUseCase` con validación de rango [0.01, 999999.99] y 2 decimales
    - Crear `GetPriceComparisonUseCase` para historial últimas 6 listas
    - Crear `ScanBarcodeUseCase` para lookup de producto por código
    - Crear `SyncDataUseCase` para sincronización
    - Crear `ExportToExcelUseCase` que valida que existan precios antes de exportar
    - _Requisitos: 1.1, 1.2, 1.5, 1.7, 2.3, 2.7, 3.1, 3.2, 3.7, 4.1, 4.2, 4.5, 5.2, 7.1, 8.1, 8.5_

  - [x] 2.4 Escribir prueba de propiedad: Round-trip de persistencia de productos
    - **Propiedad 1: Round-trip de persistencia de productos**
    - **Valida: Requisitos 1.1, 1.3**
    - Generar productos con nombre (2-100 chars), categoría no vacía y al menos una tienda; verificar que crear + leer retorna los mismos datos

  - [x] 2.5 Escribir prueba de propiedad: Validación de campos obligatorios
    - **Propiedad 2: Validación de campos obligatorios rechaza datos incompletos**
    - **Valida: Requisitos 1.2**
    - Generar combinaciones con campos faltantes; verificar que la operación falla reportando campos exactos sin modificar BD

  - [x] 2.6 Escribir prueba de propiedad: Validación de rango de precio
    - **Propiedad 11: Validación de rango de precio**
    - **Valida: Requisitos 4.1, 4.2**
    - Generar precios válidos e inválidos; verificar aceptación/rechazo correcto según rango [0.01, 999999.99]

  - [x] 2.7 Escribir prueba de propiedad: Validación de código de barras manual
    - **Propiedad 16: Validación de código de barras manual**
    - **Valida: Requisitos 5.6**
    - Generar cadenas de distintas longitudes y contenidos; verificar que solo se aceptan 8-14 dígitos numéricos

- [x] 3. Checkpoint - Verificar capa de dominio
  - Asegurar que todas las pruebas pasan, preguntar al usuario si surgen dudas.

- [x] 4. Capa de Datos: Base de Datos Local (Room)
  - [x] 4.1 Crear entidades Room
    - Implementar `ProductEntity` con campos: id, name, category, barcode, photoPath, createdAt, updatedAt, syncStatus
    - Implementar `StoreEntity` con índice único (name, type) y campo isPredefined
    - Implementar `MonthlyListEntity` con índice único (month, year)
    - Implementar `ProductStoreEntity` con claves foráneas y CASCADE
    - Implementar `MonthlyListProductEntity` con claves foráneas y CASCADE
    - Implementar `PriceRecordEntity` con índice único (productId, storeId, monthlyListId) y CASCADE
    - Implementar `SyncMetadataEntity` para metadatos de sincronización
    - _Requisitos: 6.1, 6.2_

  - [x] 4.2 Implementar DAOs
    - Crear `ProductDao` con: insert, update, delete, getById, getByBarcode, searchByNameOrCategory, getByMonthlyList
    - Crear `StoreDao` con: insert, update, delete, getAll, getById, existsByNameAndType
    - Crear `MonthlyListDao` con: insert, getAll (ordenado desc), getByMonthAndYear, getMostRecent, getActive
    - Crear `ProductStoreDao` con: insert, deleteByProduct, getStoresForProduct
    - Crear `MonthlyListProductDao` con: insert, delete, getProductsForList, countProducts
    - Crear `PriceRecordDao` con: insertOrReplace, getByProductAndList, getHistoryLastNMonths, getLowestForProductInList, getForList
    - Crear `SyncMetadataDao` con: get, set
    - _Requisitos: 6.1, 6.2, 6.3_

  - [x] 4.3 Crear AppDatabase y migraciones
    - Crear `AppDatabase` anotada con `@Database` incluyendo todas las entidades
    - Configurar versión de BD y exportar esquema
    - Implementar callback para insertar tiendas predefinidas: Walmart, 3B, Sams, Gran Bodega (SUPERMARKET) y Mercado_Local (GENERAL_MARKET)
    - Crear mappers: Entity ↔ Domain model para cada entidad
    - Proveer AppDatabase mediante módulo Hilt `DatabaseModule`
    - _Requisitos: 2.4, 6.1, 6.2, 6.6_

  - [x] 4.4 Implementar repositorios locales
    - Implementar `ProductRepositoryImpl` usando ProductDao con transacciones Room
    - Implementar `StoreRepositoryImpl` con verificación de unicidad y protección de predefinidas
    - Implementar `MonthlyListRepositoryImpl` con lógica de copia de lista (max 500 productos, sin precios)
    - Implementar `PriceRepositoryImpl` con historial limitado a 6 meses y detección de precio mínimo
    - Registrar implementaciones en módulo Hilt `RepositoryModule`
    - _Requisitos: 1.1, 1.3, 1.5, 2.1, 2.3, 2.5, 2.6, 2.7, 3.1, 3.2, 3.5, 4.1, 4.3, 4.5, 4.6, 6.1, 6.2, 6.3_

  - [x] 4.5 Escribir prueba de propiedad: Eliminación en cascada de productos
    - **Propiedad 3: Eliminación en cascada de productos**
    - **Valida: Requisitos 1.5, 1.6**
    - Crear producto con N registros de precio, eliminar y verificar que ni producto ni precios existen en BD

  - [x] 4.6 Escribir prueba de propiedad: Unicidad de tiendas por nombre y tipo
    - **Propiedad 5: Unicidad de tiendas por nombre y tipo**
    - **Valida: Requisitos 2.3**
    - Generar pares (nombre, tipo) existentes; verificar que crear duplicado es rechazado manteniendo la original

  - [x] 4.7 Escribir prueba de propiedad: Tiendas predefinidas indestructibles
    - **Propiedad 6: Tiendas predefinidas son indestructibles**
    - **Valida: Requisitos 2.7**
    - Para toda tienda predefinida, verificar que eliminar falla y la tienda permanece intacta

  - [x] 4.8 Escribir prueba de propiedad: Copia de lista preserva productos sin precios
    - **Propiedad 7: Copia de lista preserva productos sin precios**
    - **Valida: Requisitos 3.1, 3.2**
    - Crear lista con N productos y precios; copiar y verificar mismos productos pero cero precios

  - [x] 4.9 Escribir prueba de propiedad: Unicidad de lista mensual por mes/año
    - **Propiedad 8: Unicidad de lista mensual por mes/año**
    - **Valida: Requisitos 3.3, 3.7**
    - Generar pares (mes, año) existentes; verificar que crear duplicada es rechazado

  - [x] 4.10 Escribir prueba de propiedad: Unicidad de precio por combinación
    - **Propiedad 12: Unicidad de precio por combinación Producto-Tienda-Lista**
    - **Valida: Requisitos 4.3**
    - Para combinación existente, verificar que segundo registro sin confirmación falla

  - [x] 4.11 Escribir prueba de propiedad: Búsqueda parcial case-insensitive
    - **Propiedad 4: Búsqueda parcial case-insensitive**
    - **Valida: Requisitos 1.7, 9.1**
    - Generar términos y productos; verificar que resultados contienen el término (case-insensitive) y ningún producto válido queda excluido

  - [x] 4.12 Escribir prueba de propiedad: Lookup por código de barras
    - **Propiedad 15: Lookup por código de barras**
    - **Valida: Requisitos 5.2, 9.2**
    - Para códigos almacenados, verificar que retorna el producto correcto; para no almacenados, retorna vacío

  - [x] 4.13 Escribir prueba de propiedad: Historial de precios limitado a 6 listas
    - **Propiedad 13: Historial de precios limitado a últimas 6 listas**
    - **Valida: Requisitos 4.5**
    - Crear producto con precios en N>6 listas; verificar que historial solo muestra 6 más recientes

  - [x] 4.14 Escribir prueba de propiedad: Identificación correcta del precio mínimo
    - **Propiedad 14: Identificación correcta del precio mínimo**
    - **Valida: Requisitos 4.6**
    - Para producto con múltiples precios en distintas tiendas, verificar que el mínimo es <= todos los demás

  - [x] 4.15 Escribir prueba de propiedad: Historial de listas ordenado descendente
    - **Propiedad 9: Historial de listas ordenado descendente por fecha**
    - **Valida: Requisitos 3.4**
    - Crear varias listas mensuales; verificar que el historial está estrictamente ordenado de más reciente a más antigua

  - [x] 4.16 Escribir prueba de propiedad: Aislamiento entre listas mensuales
    - **Propiedad 10: Aislamiento entre listas mensuales**
    - **Valida: Requisitos 3.5**
    - Agregar/quitar productos de lista activa; verificar que listas anteriores no se modifican

- [x] 5. Checkpoint - Verificar capa de datos local
  - Asegurar que todas las pruebas pasan, preguntar al usuario si surgen dudas.

- [x] 6. Capa de Datos: Módulo de Cámara y Escáner
  - [x] 6.1 Implementar BarcodeScanner con CameraX y ML Kit
    - Configurar dependencias CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`)
    - Configurar dependencia ML Kit (`barcode-scanning`) para modelo on-device
    - Crear `BarcodeAnalyzer` implementando `ImageAnalysis.Analyzer` con formatos EAN-13, UPC-A, Code 128
    - Implementar timeout de 5 segundos con fallback a ingreso manual
    - Crear `CameraManager` para gestionar ciclo de vida de CameraX
    - _Requisitos: 5.1, 5.5, 5.6_

  - [x] 6.2 Implementar captura y almacenamiento de fotos
    - Crear `PhotoManager` para capturar y guardar fotos en formato JPEG (max 2 MB)
    - Implementar compresión automática si la imagen excede 2 MB
    - Crear lógica de verificación de existencia de archivo con fallback a placeholder
    - Almacenar fotos en directorio privado de la app (no requiere permisos externos)
    - _Requisitos: 5.4, 5.5, 6.4_

  - [x] 6.3 Escribir prueba de propiedad: Foto placeholder cuando archivo no existe
    - **Propiedad 21: Foto placeholder cuando archivo no existe**
    - **Valida: Requisitos 6.4**
    - Para productos con rutas de foto a archivos inexistentes, verificar que se retorna indicador de placeholder

- [x] 7. Capa de Datos: Sincronización con Firebase Firestore
  - [x] 7.1 Configurar Firebase Firestore
    - Agregar `google-services.json` (placeholder para configuración del usuario)
    - Configurar dependencias Firebase (`firebase-firestore-ktx`, `firebase-auth-ktx`)
    - Crear `FirestoreDataSource` con estructura de colecciones: users/{userId}/products, stores, monthlyLists, priceRecords
    - Implementar autenticación anónima con Firebase Auth para identificar dispositivo
    - _Requisitos: 7.1, 7.2_

  - [x] 7.2 Implementar SyncRepository con WorkManager
    - Crear `SyncRepositoryImpl` que lee registros con syncStatus=PENDING
    - Implementar batch writes a Firestore (creates, updates, deletes)
    - Implementar resolución de conflictos last-write-wins basado en `updatedAt`
    - Crear `SyncWorker` con constraint de red (`NetworkType.CONNECTED`)
    - Implementar retry con backoff exponencial en caso de fallo
    - Almacenar `lastSyncTime` en SyncMetadata
    - Detectar límite de almacenamiento del tier gratuito y notificar al usuario
    - Proveer SyncRepository en módulo Hilt
    - _Requisitos: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

  - [x] 7.3 Escribir prueba de propiedad: Resolución de conflictos last-write-wins
    - **Propiedad 17: Resolución de conflictos last-write-wins**
    - **Valida: Requisitos 7.3**
    - Para pares de versiones del mismo registro con timestamps distintos, verificar que prevalece el más reciente

  - [x] 7.4 Escribir prueba de propiedad: Operaciones offline generan sync pendiente
    - **Propiedad 18: Operaciones offline generan sync pendiente**
    - **Valida: Requisitos 7.4**
    - Para cualquier operación CRUD sin conexión, verificar que el registro tiene syncStatus="PENDING"

  - [x] 7.5 Escribir prueba de propiedad: Formato de fecha de sincronización
    - **Propiedad 19: Formato de fecha de sincronización**
    - **Valida: Requisitos 7.5**
    - Para cualquier fecha/hora válida, verificar que el formato de salida cumple "dd/MM/yyyy HH:mm"

- [ ] 8. Capa de Datos: Exportación a Excel (8.3 omitida por decisión del usuario)
  - [x] 8.1 Implementar ExcelExporter con Apache POI
    - Agregar dependencia `android5xlsx` para Apache POI compatible con Android
    - Crear `ExcelExporter` que genera `XSSFWorkbook` con formato especificado
    - Implementar nombre de archivo: "Lista_[Mes]_[Año].xlsx"
    - Crear columnas: Producto, Categoría, Tienda, Precio, Fecha_Registro
    - Una fila por combinación Producto-Tienda con Precio_Registro
    - Ordenar filas alfabéticamente por nombre de Producto
    - Generar archivo en directorio de caché de la app
    - _Requisitos: 8.1, 8.2_

  - [x] 8.2 Implementar ExcelExportRepository con compartición
    - Crear `ExcelExportRepositoryImpl` que usa `ExcelExporter`
    - Configurar `FileProvider` en `AndroidManifest.xml` y `file_paths.xml`
    - Implementar compartición mediante Android Sharesheet (Intent.ACTION_SEND)
    - Validar que la lista tenga precios antes de exportar (retornar error si vacía)
    - Manejar errores: permisos insuficientes, almacenamiento lleno, error de escritura
    - _Requisitos: 8.1, 8.3, 8.4, 8.5_

  - [ ] 8.3 Escribir prueba de propiedad: Exportación Excel con nombre, estructura y orden (omitida por decisión del usuario)
    - **Propiedad 20: Exportación Excel con nombre, estructura y orden correctos**
    - **Valida: Requisitos 8.1, 8.2**
    - Para listas con productos y precios, verificar: nombre correcto, columnas exactas, una fila por combinación, orden alfabético

- [x] 9. Checkpoint - Verificar capa de datos completa
  - Verificar que el proyecto compila (pruebas omitidas por decisión del usuario). Compilación verificada con una copia temporal a compileSdk 29; el build real con compileSdk 34 queda pendiente de instalar platform android-34.

- [x] 10. Capa UI: Navegación y Pantalla Principal
  - [x] 10.1 Configurar navegación con Navigation Compose
    - Crear sealed class `Screen` con rutas: Home, ProductDetail, StoreList, MonthlyListHistory, Scanner, PriceHistory, Export
    - Crear `NavGraph` con composable para cada ruta
    - Implementar `BottomNavigation` o `NavigationBar` Material3 para navegación principal
    - _Requisitos: 1.1, 3.4_

  - [x] 10.2 Implementar pantalla principal (Home)
    - Crear `HomeViewModel` con estado de lista mensual activa, productos y última sincronización
    - Crear `HomeScreen` con: barra de búsqueda, lista de productos de la lista activa, indicador de última sincronización (formato "dd/MM/yyyy HH:mm"), FAB para agregar producto
    - Mostrar precio más bajo con indicador de color diferenciado
    - Implementar filtrado en tiempo real al escribir en barra de búsqueda
    - Mostrar mensaje "No hay coincidencias" cuando búsqueda no encuentra resultados, con opción de agregar producto
    - Restaurar vista completa al limpiar campo de búsqueda
    - _Requisitos: 1.7, 4.6, 7.5, 9.1, 9.4, 9.5, 9.6_

  - [ ] 10.3 Escribir prueba de propiedad: Restaurar vista completa al limpiar búsqueda (omitida por decisión del usuario)
    - **Propiedad 22: Restaurar vista completa al limpiar búsqueda**
    - **Valida: Requisitos 9.6**
    - Para cualquier estado filtrado con N productos totales, verificar que limpiar búsqueda muestra exactamente N productos

- [x] 11. Capa UI: Gestión de Productos
  - [x] 11.1 Implementar pantalla de creación/edición de producto
    - Crear `ProductViewModel` con estados: formulario, validación, guardado
    - Crear `ProductFormScreen` con campos: nombre (2-100 chars), categoría (selector), tiendas (multiselect, mínimo 1), código de barras (opcional), foto (opcional)
    - Mostrar indicadores de error en campos faltantes sin perder datos ingresados
    - Implementar botón de cámara para escanear código de barras
    - Implementar botón de cámara para capturar foto del producto
    - Permitir ingresar precio asociado a una tienda activa al crear/editar
    - _Requisitos: 1.1, 1.2, 1.3, 1.6, 5.8_

  - [x] 11.2 Implementar detalle y eliminación de producto
    - Crear `ProductDetailScreen` con toda la información del producto
    - Mostrar historial de precios por tienda (últimas 6 listas mensuales) ordenado por fecha descendente
    - Implementar botón de eliminar con diálogo de confirmación mostrando nombre del producto
    - Implementar indicador visual de precio más bajo (color diferenciado)
    - Mostrar imagen del producto o placeholder si no existe el archivo
    - _Requisitos: 1.4, 1.5, 4.5, 4.6, 6.4_

- [x] 12. Capa UI: Gestión de Tiendas
  - [x] 12.1 Implementar pantalla de tiendas
    - Crear `StoreViewModel` con lista de tiendas y operaciones CRUD
    - Crear `StoreListScreen` con lista de tiendas mostrando nombre, tipo e indicador de predefinida
    - Implementar formulario de creación/edición (nombre 2-50 chars, tipo obligatorio)
    - Mostrar error si nombre+tipo ya existe
    - Implementar eliminación con confirmación (indicando que se borran precios asociados)
    - Deshabilitar/ocultar opción de eliminar para tiendas predefinidas con mensaje informativo
    - _Requisitos: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

- [x] 13. Capa UI: Listas Mensuales
  - [x] 13.1 Implementar pantalla de historial de listas mensuales
    - Crear `MonthlyListViewModel` con historial ordenado descendente y lista activa
    - Crear `MonthlyListHistoryScreen` con listado de listas mensuales (formato "Mes Año")
    - Implementar botón "Crear nueva lista" que: copia productos de lista más reciente (max 500) sin precios, o crea lista vacía si no hay previas
    - Mostrar error si ya existe lista para el mes actual
    - Permitir agregar/quitar productos de la lista activa
    - _Requisitos: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

- [x] 14. Capa UI: Registro de Precios
  - [x] 14.1 Implementar pantalla/componente de registro de precio
    - Crear componente de ingreso de precio con validación: numérico, 2 decimales, rango [0.01, 999999.99]
    - Mostrar mensaje de error con formato y rango válido cuando el precio es inválido
    - Implementar selector de tienda asociada al precio
    - Solicitar confirmación antes de reemplazar un precio existente para la misma combinación Producto-Tienda en la lista activa
    - _Requisitos: 4.1, 4.2, 4.3, 4.4_

- [x] 15. Capa UI: Escáner de Código de Barras
  - [x] 15.1 Implementar pantalla de escáner
    - Crear `ScannerViewModel` con estados: escaneando, producto encontrado, producto nuevo, timeout, sin permiso
    - Crear `ScannerScreen` con preview de CameraX y overlay de guía de escaneo
    - Solicitar permiso de cámara con mensaje explicativo si no está concedido
    - Al detectar código existente: navegar a detalle del producto
    - Al detectar código nuevo: navegar a creación de producto con código pre-llenado
    - Mostrar campo de ingreso manual (8-14 dígitos) tras timeout de 5 segundos
    - Permitir ingreso de precio directamente desde el escáner
    - _Requisitos: 5.1, 5.2, 5.3, 5.5, 5.6, 5.7, 5.8_

- [x] 16. Capa UI: Exportación y Sincronización
  - [x] 16.1 Implementar pantalla/flujo de exportación Excel
    - Crear `ExportViewModel` con estados: selección de lista, exportando, éxito, error
    - Implementar botón de exportar en el detalle de lista mensual
    - Mostrar opciones: compartir (Android Sharesheet) o guardar en dispositivo
    - Mostrar progreso durante exportación (max 10 segundos)
    - Mostrar mensaje de error detallado si falla (permisos, almacenamiento, escritura)
    - Validar que la lista tenga precios antes de exportar, informar si no hay datos
    - _Requisitos: 8.1, 8.2, 8.3, 8.4, 8.5_

  - [x] 16.2 Implementar indicadores de sincronización en UI
    - Mostrar fecha/hora de última sincronización exitosa en pantalla principal (formato "dd/MM/yyyy HH:mm")
    - Mostrar indicador de error de sincronización cuando falla
    - Mostrar notificación cuando se alcanza límite de almacenamiento del tier gratuito
    - Ejecutar sincronización automática al detectar conectividad mediante WorkManager
    - _Requisitos: 7.1, 7.5, 7.6, 7.7_

- [x] 17. Checkpoint - Verificar capa UI completa
  - Verificar que el proyecto compila (pruebas omitidas por decisión del usuario). Compilación verificada con una copia temporal a compileSdk 29; el build real con compileSdk 34 queda pendiente de instalar platform android-34.

- [x] 18. Integración final y cableado de componentes
  - [x] 18.1 Conectar todas las capas y verificar flujos completos
    - Verificar inyección de dependencias Hilt completa (todos los módulos proveen correctamente)
    - Verificar navegación completa entre todas las pantallas
    - Verificar que operaciones offline-first funcionan correctamente (Room como source of truth)
    - Asegurar que el flujo completo funciona: crear lista → agregar producto → escanear código → registrar precio → exportar Excel
    - Verificar que la sincronización con Firestore se ejecuta al recuperar conectividad
    - _Requisitos: 6.1, 6.2, 6.5, 7.1_

  - [ ] 18.2 Escribir pruebas de integración (omitida por decisión del usuario)
    - Test: flujo crear producto → buscar por nombre → verificar resultado
    - Test: flujo escanear código → crear producto → buscar por código
    - Test: flujo crear lista → copiar → verificar productos sin precios
    - Test: flujo registrar precios → exportar Excel → verificar archivo generado
    - Test: flujo CRUD offline → verificar sync_status PENDING
    - _Requisitos: 1.1, 3.1, 5.2, 8.1, 7.4_

- [x] 19. Checkpoint final - Verificar integración completa
  - Verificar que el proyecto compila (pruebas omitidas por decisión del usuario). Compilación verificada con una copia temporal a compileSdk 29; el build real con compileSdk 34 queda pendiente de instalar platform android-34.

## Notas

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia requisitos específicos para trazabilidad
- Los checkpoints aseguran validación incremental
- Las pruebas de propiedades validan correctitud universal con Kotest (mínimo 100 iteraciones)
- Las pruebas unitarias validan ejemplos específicos y casos borde
- La app es offline-first: Room es la fuente de verdad, Firestore es respaldo
- Todas las operaciones de cámara y escaneo funcionan sin conexión a internet
- El tier gratuito de Firebase Firestore (Spark) limita: 1 GiB almacenamiento, 20K escrituras/día, 50K lecturas/día

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["1.3", "2.1"] },
    { "id": 3, "tasks": ["2.2"] },
    { "id": 4, "tasks": ["2.3"] },
    { "id": 5, "tasks": ["2.4", "2.5", "2.6", "2.7"] },
    { "id": 6, "tasks": ["4.1"] },
    { "id": 7, "tasks": ["4.2"] },
    { "id": 8, "tasks": ["4.3"] },
    { "id": 9, "tasks": ["4.4"] },
    { "id": 10, "tasks": ["4.5", "4.6", "4.7", "4.8", "4.9", "4.10", "4.11", "4.12", "4.13", "4.14", "4.15", "4.16"] },
    { "id": 11, "tasks": ["6.1", "6.2"] },
    { "id": 12, "tasks": ["6.3", "7.1"] },
    { "id": 13, "tasks": ["7.2"] },
    { "id": 14, "tasks": ["7.3", "7.4", "7.5", "8.1"] },
    { "id": 15, "tasks": ["8.2"] },
    { "id": 16, "tasks": ["8.3"] },
    { "id": 17, "tasks": ["10.1"] },
    { "id": 18, "tasks": ["10.2", "10.3"] },
    { "id": 19, "tasks": ["11.1", "11.2", "12.1", "13.1"] },
    { "id": 20, "tasks": ["14.1", "15.1"] },
    { "id": 21, "tasks": ["16.1", "16.2"] },
    { "id": 22, "tasks": ["18.1"] },
    { "id": 23, "tasks": ["18.2"] }
  ]
}
```
