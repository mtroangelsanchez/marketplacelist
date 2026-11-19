# Documentación - Mi Tienda de Super

App Android para armar listas de compras mensuales, comparar precios por tienda, escanear códigos de barras, leer tickets de compra y exportar a Excel. Kotlin, Jetpack Compose, Hilt, Room (v2), CameraX, ML Kit, WorkManager, Firebase (respaldo opcional) y poi-android.

| Documento | Contenido | Diagrama editable (draw.io) |
|---|---|---|
| [base-de-datos-er.md](base-de-datos-er.md) | Modelo entidad-relación de Room v2: tablas, campos, claves, índices y migración 1 → 2 | [base-de-datos-er.drawio](base-de-datos-er.drawio) |
| [arquitectura-software.md](arquitectura-software.md) | Capas, componentes y flujos principales (datos, escáner, ticket, Excel, sincronización) | [arquitectura-software.drawio](arquitectura-software.drawio) |

Los archivos `.drawio` se abren en https://app.diagrams.net (Archivo > Abrir desde > Dispositivo) o con la extensión draw.io de VS Code.

Fuentes de verdad: `app/src/main/java/com/app/marketlist/` y `app/schemas/com.app.marketlist.data.local.db.AppDatabase/2.json`.
