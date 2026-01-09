# 📚 PDF Escolar Kiki

Un lector de PDF moderno, minimalista y altamente optimizado para Android. Diseñado para ofrecer una experiencia de lectura fluida incluso con archivos grandes, utilizando **100% APIs nativas de Android** para minimizar el tamaño de la APK.

## 🚀 Características Clave

*   **Renderizado Nativo**: Utiliza `PdfRenderer` y `ParcelFileDescriptor` para una carga instantánea sin librerías de terceros pesadas.
*   **Gestión de Archivos Eficiente**: Sistema de caché inteligente (`FileUtils`) y manejo de URIs persistentes (`ContentResolver`).
*   **Arquitectura Limpia**: Código modularizado siguiendo el patrón **Repository** para la persistencia de datos y separación de responsabilidades (UI vs Lógica).
*   **Gestor de Recientes**: Historial de archivos con persistencia en JSON/SharedPreferences y favoritos.
*   **Modo Nocturno Real**: Inversión de colores nativa para lectura cómoda.
*   **Thumbnails Asíncronos**: Generación de miniaturas en segundo plano utilizando **Kotlin Coroutines** para no bloquear el hilo principal (UI Thread).

## 🛠️ Stack Tecnológico

*   **Lenguaje**: [Kotlin](https://kotlinlang.org/) (100%)
*   **Componentes**: Android View System (XML), RecyclerView, CardView.
*   **Concurrencia**: Kotlin Coroutines & Dispatchers.
*   **Almacenamiento**: SharedPreferences, File IO.
*   **Patrones de Diseño**: Repository Pattern, Adapter Pattern.

## 📂 Estructura del Proyecto

El código ha sido refactorizado para seguir principios de **Clean Code**:

```text
com.example.KikiPdf
├── MainActivity.kt          # Controlador de UI (View Layer)
├── RecentFilesRepository.kt # Lógica de Datos y Persistencia (Data Layer)
├── PdfAdapter.kt            # Adaptador eficiente para RecyclerView
├── FileUtils.kt             # Utilidades de manejo de Archivos E/S
└── RecentFile.kt            # Modelo de Datos (Domain)
```

## 🔧 Instalación

Clona este repositorio y ábrelo en Android Studio:

```bash
git clone https://github.com/tu-usuario/PDFEscolarkiki.git
```

## 📄 Licencia

Este proyecto está bajo la Licencia MIT - [LICENSE.md](LICENSE.md)
