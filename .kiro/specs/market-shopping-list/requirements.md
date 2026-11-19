# Documento de Requisitos

## Introducción

Aplicación Android de lista de compras de mercado reutilizable mensualmente. La aplicación permite gestionar productos organizados por tipo de tienda (supermercados y mercados generales), capturar información de productos mediante cámara (código de barras y precio) sin conexión a internet, registrar precios variables por ubicación, sincronizar datos con una base de datos en la nube gratuita, y exportar listas a formato Excel.

## Glosario

- **App**: La aplicación Android de lista de compras de mercado
- **Producto**: Un artículo de compra con nombre, código de barras, precio, categoría y foto opcional
- **Lista_Mensual**: Conjunto de productos a comprar en un mes específico
- **Supermercado**: Tienda de tipo cadena comercial (Walmart, 3B, Sams, Gran Bodega, etc.)
- **Mercado_General**: Mercado tradicional de verduras, frutas y productos frescos
- **Tienda**: Lugar de compra que puede ser un Supermercado o un Mercado_General
- **Precio_Registro**: Registro del precio de un Producto en una Tienda específica en una fecha determinada
- **Base_Datos_Local**: Base de datos SQLite almacenada en el dispositivo Android
- **Base_Datos_Nube**: Base de datos remota gratuita (Firebase Firestore o similar) para respaldo y acceso permanente
- **Escáner_Código**: Módulo que captura y decodifica códigos de barras usando la cámara del dispositivo
- **Exportador_Excel**: Módulo que genera archivos en formato Excel (.xlsx) a partir de una Lista_Mensual

## Requisitos

### Requisito 1: Gestión de Productos

**Historia de Usuario:** Como usuario, quiero agregar, editar y eliminar productos en mi lista de compras, para poder mantener un catálogo actualizado de lo que necesito comprar.

#### Criterios de Aceptación

1. WHEN el usuario ingresa nombre (entre 2 y 100 caracteres), categoría y al menos una Tienda asociada, THE App SHALL crear el Producto en la Base_Datos_Local con un identificador único generado automáticamente
2. IF el usuario intenta crear o editar un Producto sin completar los campos obligatorios (nombre, categoría, tienda), THEN THE App SHALL indicar al usuario cuáles campos faltan sin perder los datos ya ingresados
3. WHEN el usuario edita un Producto existente, THE App SHALL permitir modificar nombre, categoría, tiendas asociadas, código de barras y foto, y persistir los cambios en la Base_Datos_Local
4. WHEN el usuario solicita eliminar un Producto, THE App SHALL mostrar un diálogo de confirmación indicando el nombre del Producto antes de proceder
5. WHEN el usuario confirma la eliminación de un Producto, THE App SHALL remover el Producto y todos sus Precio_Registro asociados de la Base_Datos_Local
6. THE App SHALL clasificar cada Producto en al menos una Tienda (Supermercado o Mercado_General)
7. WHEN el usuario busca un Producto por nombre o categoría, THE App SHALL mostrar los resultados que coincidan parcial o totalmente con el término de búsqueda

### Requisito 2: Gestión de Tiendas

**Historia de Usuario:** Como usuario, quiero registrar diferentes tiendas donde compro, para poder comparar precios entre ellas.

#### Criterios de Aceptación

1. THE App SHALL permitir registrar Tiendas con un nombre de entre 2 y 50 caracteres y un tipo obligatorio (Supermercado o Mercado_General)
2. WHEN el usuario crea una nueva Tienda, THE App SHALL almacenarla en la Base_Datos_Local con un identificador único
3. IF el usuario intenta crear una Tienda con un nombre idéntico a una Tienda existente del mismo tipo, THEN THE App SHALL rechazar la creación y mostrar un mensaje indicando que la Tienda ya existe
4. THE App SHALL incluir tiendas predefinidas no eliminables: Walmart, 3B, Sams y Gran Bodega como Supermercado, y Mercado_Local como Mercado_General
5. WHEN el usuario edita una Tienda, THE App SHALL actualizar el nombre o tipo de la Tienda en la Base_Datos_Local manteniendo sus Precios_Registro asociados
6. WHEN el usuario solicita eliminar una Tienda creada por el usuario, THE App SHALL mostrar confirmación indicando que se eliminarán los Precios_Registro asociados, y al confirmar, remover la Tienda y todos sus Precios_Registro de la Base_Datos_Local
7. IF el usuario intenta eliminar una Tienda predefinida, THEN THE App SHALL rechazar la operación y mostrar un mensaje indicando que las tiendas predefinidas no pueden eliminarse

### Requisito 3: Lista Mensual Reutilizable

**Historia de Usuario:** Como usuario, quiero copiar mi lista de productos cada mes, para no tener que recrearla desde cero y poder reutilizarla.

#### Criterios de Aceptación

1. WHEN el usuario solicita crear una nueva Lista_Mensual y existe al menos una Lista_Mensual previa, THE App SHALL copiar todos los Productos (hasta un máximo de 500) de la Lista_Mensual más reciente a la nueva Lista_Mensual
2. WHEN se copia una Lista_Mensual, THE App SHALL mantener los datos del Producto (nombre, categoría, tienda) pero establecer el campo de precio como vacío en la nueva Lista_Mensual
3. THE App SHALL identificar cada Lista_Mensual con el mes y año correspondiente en formato "mes año" (por ejemplo, "Enero 2025") y no permitir más de una Lista_Mensual por mes
4. WHEN el usuario consulta el historial, THE App SHALL mostrar todas las Listas_Mensuales almacenadas ordenadas por fecha descendente
5. WHILE una Lista_Mensual está activa (la Lista_Mensual del mes en curso), THE App SHALL permitir agregar o quitar Productos de esa lista sin afectar listas de meses anteriores
6. IF el usuario solicita crear una nueva Lista_Mensual y no existe ninguna Lista_Mensual previa, THEN THE App SHALL crear una Lista_Mensual vacía para el mes actual
7. IF el usuario solicita crear una Lista_Mensual para un mes que ya tiene una lista existente, THEN THE App SHALL notificar al usuario que ya existe una lista para ese mes y no crear una duplicada

### Requisito 4: Registro de Precios por Tienda

**Historia de Usuario:** Como usuario, quiero registrar el precio de cada producto en diferentes tiendas, para poder comparar dónde me conviene comprar.

#### Criterios de Aceptación

1. WHEN el usuario registra un precio para un Producto, THE App SHALL almacenar el Precio_Registro asociado a la Tienda, la fecha actual del dispositivo y la Lista_Mensual activa, donde el precio es un valor numérico con exactamente 2 decimales en el rango de 0.01 a 999,999.99
2. IF el usuario ingresa un precio fuera del rango 0.01 a 999,999.99 o con formato no numérico, THEN THE App SHALL rechazar el registro y mostrar un mensaje de error indicando el formato y rango válido
3. THE App SHALL permitir registrar múltiples precios para el mismo Producto en diferentes Tiendas dentro de la misma Lista_Mensual, limitado a un máximo de un Precio_Registro por combinación de Producto y Tienda por Lista_Mensual
4. IF el usuario registra un precio para una combinación de Producto y Tienda que ya tiene un Precio_Registro en la Lista_Mensual activa, THEN THE App SHALL solicitar confirmación para reemplazar el precio anterior
5. WHEN el usuario consulta un Producto, THE App SHALL mostrar el historial de precios por Tienda de las últimas 6 Listas_Mensuales, ordenados por fecha descendente
6. WHEN existen múltiples precios para un Producto en diferentes Tiendas dentro de la misma Lista_Mensual, THE App SHALL distinguir visualmente el precio más bajo mediante un indicador de color diferenciado del resto de precios

### Requisito 5: Captura de Producto con Cámara y Código de Barras (Offline)

**Historia de Usuario:** Como usuario, quiero tomar foto de un producto y escanear su código de barras sin necesidad de internet, para registrar productos rápidamente en cualquier lugar.

#### Criterios de Aceptación

1. WHEN el usuario activa la cámara para escanear un producto, THE Escáner_Código SHALL decodificar códigos de barras en formatos EAN-13, UPC-A y Code 128 sin requerir conexión a internet, mostrando el resultado en un máximo de 3 segundos desde el enfoque del código
2. WHEN el Escáner_Código decodifica un código de barras que ya existe en la Base_Datos_Local, THE App SHALL asociar el código al Producto correspondiente y mostrar sus datos almacenados
3. WHEN el Escáner_Código decodifica un código de barras que no existe en la Base_Datos_Local, THE App SHALL iniciar el flujo de creación de un nuevo Producto con el código de barras pre-llenado
4. WHEN el usuario toma una foto de un producto, THE App SHALL almacenar la imagen en formato JPEG con un tamaño máximo de 2 MB en el almacenamiento local del dispositivo y asociarla al Producto
5. WHILE el dispositivo no tiene conexión a internet, THE App SHALL permitir todas las operaciones de captura de foto y escaneo de código de barras sin degradación de funcionalidad
6. IF el código de barras no puede ser decodificado después de 5 segundos de intento, THEN THE App SHALL mostrar un mensaje en pantalla indicando el fallo y presentar un campo de ingreso manual que acepte entre 8 y 14 caracteres numéricos
7. IF el usuario no ha otorgado permiso de cámara, THEN THE App SHALL mostrar un mensaje solicitando el permiso y no proceder con la captura hasta que el permiso sea concedido
8. WHEN el usuario captura un producto, THE App SHALL permitir ingresar el precio manualmente con un valor entre 0.01 y 999,999.99, con hasta 2 decimales, asociado al Producto y la Tienda activa de la Lista_Mensual actual

### Requisito 6: Base de Datos Local

**Historia de Usuario:** Como usuario, quiero que mis datos estén almacenados localmente, para poder usar la aplicación sin depender de internet.

#### Criterios de Aceptación

1. THE Base_Datos_Local SHALL almacenar todos los Productos, Tiendas, Listas_Mensuales y Precios_Registro en una base de datos SQLite, soportando hasta 10,000 Productos, 100 Tiendas, 120 Listas_Mensuales y 100,000 Precios_Registro
2. THE Base_Datos_Local SHALL permitir todas las operaciones de lectura y escritura sin conexión a internet, y los datos SHALL persistir entre reinicios de la aplicación y reinicios del dispositivo
3. WHEN el usuario realiza cualquier operación CRUD, THE Base_Datos_Local SHALL persistir los cambios en un máximo de 2 segundos y confirmar la escritura antes de mostrar el resultado al usuario
4. THE Base_Datos_Local SHALL almacenar las rutas de las fotos de productos capturadas, y WHEN el usuario consulta un Producto cuya ruta de foto referencia un archivo inexistente, THE App SHALL mostrar una imagen placeholder indicando que la foto no está disponible
5. IF una operación de escritura en la Base_Datos_Local falla, THEN THE App SHALL mostrar un mensaje de error al usuario indicando que los datos no se pudieron guardar y SHALL mantener los datos previos sin corrupción
6. WHEN la aplicación se inicia, THE Base_Datos_Local SHALL estar lista para operaciones de lectura y escritura en un máximo de 3 segundos

### Requisito 7: Sincronización con Base de Datos en la Nube

**Historia de Usuario:** Como usuario, quiero que mis datos se respalden en la nube de forma gratuita, para tener acceso permanente a mi información desde cualquier dispositivo.

#### Criterios de Aceptación

1. WHEN el dispositivo recupera conexión a internet, THE App SHALL sincronizar de forma incremental los cambios pendientes de Productos, Tiendas, Listas_Mensuales, Precios_Registro y metadatos de fotos desde la Base_Datos_Local hacia la Base_Datos_Nube en un máximo de 30 segundos tras detectar conectividad
2. THE Base_Datos_Nube SHALL utilizar un servicio gratuito (Firebase Firestore free tier o similar) y THE App SHALL sincronizar únicamente datos estructurales (no archivos de imagen) para mantenerse dentro de los límites de almacenamiento del tier gratuito
3. IF ocurre un conflicto durante la sincronización donde el mismo registro fue modificado en dos dispositivos distintos entre sincronizaciones, THEN THE App SHALL conservar la versión con la marca de tiempo de modificación más reciente y descartar la versión anterior
4. WHILE el dispositivo no tiene conexión a internet, THE App SHALL permitir todas las operaciones CRUD sobre la Base_Datos_Local sin restricciones y SHALL registrar los cambios pendientes para sincronización posterior
5. WHEN la sincronización se completa exitosamente, THE App SHALL mostrar la fecha y hora de la última sincronización exitosa en formato "dd/MM/yyyy HH:mm" visible en la pantalla principal
6. IF la sincronización falla por error de red o error del servicio, THEN THE App SHALL mostrar un indicador de error de sincronización al usuario, conservar los datos en la Base_Datos_Local sin pérdida, y reintentar la sincronización automáticamente en el siguiente intervalo de conectividad
7. IF la Base_Datos_Nube alcanza el límite de almacenamiento del tier gratuito, THEN THE App SHALL notificar al usuario que la sincronización está pausada por límite de almacenamiento y SHALL continuar operando con la Base_Datos_Local sin pérdida de funcionalidad

### Requisito 8: Exportar Lista a Excel

**Historia de Usuario:** Como usuario, quiero exportar mi lista de compras a un archivo Excel, para poder compartirla o revisarla fuera de la aplicación.

#### Criterios de Aceptación

1. WHEN el usuario solicita exportar una Lista_Mensual, THE Exportador_Excel SHALL generar un archivo .xlsx nombrado con el formato "Lista_[Mes]_[Año].xlsx" con todos los Productos, Tiendas y Precios_Registro de esa lista, sin requerir conexión a internet, en un tiempo máximo de 10 segundos
2. THE Exportador_Excel SHALL organizar el archivo con columnas: Producto, Categoría, Tienda, Precio, Fecha_Registro (fecha en que se registró el precio), generando una fila por cada combinación Producto-Tienda que tenga un Precio_Registro, ordenadas alfabéticamente por nombre de Producto
3. WHEN el archivo Excel se genera exitosamente, THE App SHALL presentar al usuario la opción de compartir el archivo mediante el selector de aplicaciones del sistema operativo (Android Sharesheet) o guardarlo en el almacenamiento del dispositivo
4. IF ocurre un error durante la exportación, THEN THE App SHALL mostrar al usuario un mensaje indicando la causa del fallo (permisos insuficientes, almacenamiento lleno o error de escritura) y mantener los datos de la Lista_Mensual sin modificaciones
5. IF la Lista_Mensual no contiene Productos con Precios_Registro, THEN THE App SHALL informar al usuario que no hay datos para exportar y no generar el archivo

### Requisito 9: Búsqueda de Productos

**Historia de Usuario:** Como usuario, quiero buscar productos en mi lista, para encontrar rápidamente lo que necesito.

#### Criterios de Aceptación

1. WHEN el usuario ingresa un término de búsqueda de al menos 1 carácter, THE App SHALL filtrar los Productos de la Lista_Mensual activa cuyo nombre o categoría coincida parcial o totalmente con el término, sin distinguir entre mayúsculas y minúsculas
2. WHEN el usuario busca por código de barras, THE App SHALL mostrar el Producto asociado a ese código en la Base_Datos_Local
3. IF el código de barras buscado no está asociado a ningún Producto en la Base_Datos_Local, THEN THE App SHALL mostrar un mensaje indicando que no se encontró el Producto y ofrecer la opción de registrar un nuevo Producto con ese código
4. THE App SHALL mostrar los resultados de búsqueda en menos de 1 segundo para listas de hasta 500 Productos
5. WHEN no se encuentran resultados para una búsqueda por texto, THE App SHALL mostrar un mensaje indicando que no hay coincidencias y sugerir agregar un nuevo Producto
6. WHEN el usuario limpia el campo de búsqueda, THE App SHALL restaurar la vista completa de Productos de la Lista_Mensual activa

### Requisito 10: Repositorio Git

**Historia de Usuario:** Como desarrollador, quiero tener el código fuente en un repositorio Git, para tener control de versiones y poder vincularlo a GitHub posteriormente.

#### Criterios de Aceptación

1. THE App SHALL tener su código fuente en un repositorio Git local inicializado con al menos un commit inicial que contenga la estructura base del proyecto
2. THE App SHALL incluir un archivo .gitignore que excluya como mínimo: directorio build/, archivos .gradle/, archivos de configuración local de IDE (.idea/, *.iml), archivos local.properties, y cualquier archivo que contenga credenciales o claves de API
3. THE App SHALL tener una estructura de proyecto con separación de capas en paquetes diferenciados: capa de UI (presentación y componentes de interfaz), capa de datos (repositorios y fuentes de datos locales/remotos), y capa de dominio (modelos y casos de uso)
4. WHEN el desarrollador solicite vincular a un repositorio remoto, THE App SHALL no contener archivos de más de 100 MB, no contener credenciales o claves de API en el historial de commits, y tener un archivo README.md en la raíz con el nombre del proyecto y las instrucciones básicas de compilación
5. THE App SHALL no tener archivos generados (builds, caches) ni datos sensibles (claves, tokens, contraseñas) registrados en el historial de commits de Git
