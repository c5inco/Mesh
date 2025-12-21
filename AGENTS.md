# Mesh Codebase Guide for AI Agents

This document provides context and guidelines for AI agents working on the **Mesh** project.

## Project Overview

**Mesh** is a desktop application for creating and editing smooth mesh gradients. It allows users to manipulate control points, manage color palettes, and export gradients as code (Kotlin) or images.

## Tech Stack

- **Language:** Kotlin (Targeting JVM/Desktop)
- **UI Framework:** [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform)
- **UI Components:** [Jewel UI (Standalone)](https://github.com/JetBrains/jewel) - JetBrains' IntelliJ Platform UI for Compose.
- **Persistence:** [Room](https://developer.android.com/training/data-storage/room) with SQLite.
- **Code Generation:** [KotlinPoet](https://github.com/square/kotlinpoet) (for exporting gradient code).
- **Asynchronicity:** Kotlin Coroutines (`kotlinx.coroutines`).

## Project Structure

The project follows a standard Kotlin Multiplatform structure:

- **`composeApp/`**: Main module containing the application source code.
    - **`src/commonMain/kotlin/`**: Shared code (platform-agnostic).
        - **`data/`**: Database definitions (Room), Repositories, DAOs.
        - **`model/`**: Data models (`SavedColor`, `MeshPoint`).
    - **`src/desktopMain/kotlin/`**: Desktop-specific implementations.
        - **`des/c5inco/mesh/`**:
            - **`Main.kt`**: Entry point (`main()` function).
            - **`App.kt`**: Root Composable.
            - **`ui/`**: UI Components (Canvas, SidePanel, etc.).
            - **`data/`**: `AppConfiguration` (State management), `AppDataRepository` (Data access).
    - **`src/desktopMain/resources/`**: Assets (Icons, SVGs).

## Key Files & Classes

- **`AppConfiguration`**: Central class for managing global app state (`AppUiState`, `MeshState`) and repository access.
- **`AppRoomDatabase`**: The Room database definition.
- **`AppDataRepository`**: Handles data operations for colors and mesh points.
- **`Main.kt`**: Sets up the window, theme (Jewel), and launches the app.

## Development & Build Commands

- **Run App:**
  ```bash
  ./gradlew :composeApp:run
  ```

- **Build DMG (macOS):**
  ```bash
  ./gradlew :composeApp:packageReleaseDmg
  ```

- **Run Linter/Check:**
  (Standard Gradle checks apply)

## Coding Conventions

1.  **UI Components:** Use **Jewel** components (`org.jetbrains.jewel.ui.component.*`) over standard Material components where possible to maintain the IDE-like aesthetic (Dark theme is default).
2.  **State Management:** Use `AppConfiguration` to hold state that needs to persist or be shared across the app. Use `collectAsState()` in Composables to react to flow updates.
3.  **Coroutines:** Use the provided scopes (e.g., in `AppConfiguration`) or `LaunchedEffect` in Composables. Avoid `GlobalScope`.
4.  **Database:** Interact with the database through `AppDataRepository`. Do not call DAOs directly from UI.

## Notes for AI

- **Dependency Injection:** The project currently uses manual dependency injection (passing `AppConfiguration` or repositories down).
- **Theme:** The app uses `IntUiTheme` from Jewel with a dark theme definition.
- **File System:** The app interacts with the file system for saving images (defaulting to Desktop).

