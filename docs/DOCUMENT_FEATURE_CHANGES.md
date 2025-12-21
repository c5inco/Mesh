# Document Management Feature Implementation

## Overview
Implemented the ability to treat mesh gradients as documents/drawings that can be created, opened, saved, and managed independently.

## Changes Made

### 1. New Files Created

#### `MeshDocument.kt` (commonMain)
- Data class representing a mesh document
- Contains: name, mesh state, mesh points, and canvas background color
- Serializable for saving/loading

#### `DocumentManager.kt` (desktopMain)
- Singleton object managing document operations
- Features:
  - Create new document
  - Save document to file (.mesh extension)
  - Load document from file
  - File chooser dialogs for Open/Save As
  - Document directory management (~/.mesh/documents)

### 2. Modified Files

#### `AppConfiguration.kt`
- Extended `AppUiState` with document tracking:
  - `currentDocumentName`: Name of the current document
  - `currentDocumentPath`: File path of current document
  - `hasUnsavedChanges`: Track if document has unsaved changes
  
- Added `markAsModified()` method called on all state changes
- New document operations:
  - `newDocument()`: Create a fresh document
  - `openDocument()`: Open a document via file dialog
  - `saveDocument()`: Save to current path or show save dialog
  - `saveDocumentAs()`: Always show save dialog
  - `loadDocumentState()`: Load document into app state
  - `getCurrentDocument()`: Get current state as document

#### `SidePanel.kt`
- Added new parameters for document state and operations
- Created `DocumentSection` composable:
  - Shows current document name with unsaved indicator (•)
  - Provides New, Open, Save, and Save As links
  - Clean, compact UI integrated at top of panel

#### `App.kt`
- Passed document operations from configuration to SidePanel
- Connected document state to UI

#### `main.kt`
- Updated shutdown hook to maintain auto-save behavior
- Comments added for clarity

#### `build.gradle.kts`
- Updated JVM toolchain from Java 17 to Java 21
- Removed JetBrains vendor requirement for broader compatibility

## Features Implemented

### Document Operations
1. **New Document**: Create a fresh canvas with default settings
2. **Open Document**: Browse and open .mesh files
3. **Save Document**: Save current work to existing file or show save dialog
4. **Save As**: Save document to a new location/name

### State Management
- Automatic tracking of unsaved changes
- Visual indicator when document has unsaved changes (• after name)
- All user actions trigger the modified flag
- Document state includes all mesh configuration

### File Format
- Custom `.mesh` extension
- JSON serialization for human-readable format
- Stores complete document state:
  - Mesh configuration (rows, cols, dimensions, blur)
  - All mesh points with positions and colors
  - Canvas background color
  - Document name

### User Experience
- Clean document controls at top of side panel
- Clear visual feedback for document state
- Notifications for all document operations
- File browser for easy document management
- Auto-save still works for recovery

## Technical Details

### File Storage
- Documents saved to: `~/.mesh/documents/`
- File extension: `.mesh`
- Format: JSON (human-readable, version-controllable)

### Backward Compatibility
- Existing auto-save mechanism maintained
- Legacy state files continue to work
- Smooth migration path for existing users

## Testing
- Code compiles successfully
- No linter errors
- All document operations properly integrated
- State management correctly tracks changes

## Future Enhancements
Could add in the future:
- Recent documents list
- Auto-save for documents
- Document templates/presets
- Document metadata (creation date, tags)
- Export document as different formats
