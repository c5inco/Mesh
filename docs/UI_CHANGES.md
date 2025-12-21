# UI Changes - Document Management

## New Document Section in Side Panel

The side panel now includes a new "Document" section at the top with the following elements:

```
┌─────────────────────────────────────┐
│ Document                            │
│                                     │
│ Untitled •                          │ <- Document name with unsaved indicator
│                                     │
│ [New] [Open] [Save] [Save As...]    │ <- Action links
└─────────────────────────────────────┘
```

### Features

1. **Document Name Display**
   - Shows current document name
   - Displays bullet (•) when there are unsaved changes
   - Bold text when modified

2. **Action Links**
   - **New**: Create a fresh document with default settings
   - **Open**: Browse and open existing .mesh files
   - **Save**: Save to current file (or prompt for location if new)
   - **Save As...**: Always prompt for save location

### Visual Feedback

- Notifications appear for all document operations:
  - 📄 New document created
  - 📂 Opened [filename]
  - 💾 Saved [filename]
  - ❌ Failed to open/save document

### Integration

The document section is cleanly separated from other controls with a divider:

```
┌─────────────────────────────────────┐
│         Document Section            │
├─────────────────────────────────────┤ <- Divider
│         Colors Section              │
│                                     │
│         Canvas Section              │
│                                     │
│         Points Section              │
└─────────────────────────────────────┘
```

### User Experience

1. **Automatic Change Tracking**: Any modification to the mesh automatically marks the document as unsaved
2. **Clear Status**: Always know if your work is saved
3. **Easy Access**: All document operations in one convenient location
4. **Native File Dialogs**: Uses system file browser for familiarity
5. **Smart Defaults**: Appropriate default names and locations

## Workflow Examples

### Creating a New Mesh
1. Click "New" -> Fresh canvas appears
2. Make your mesh gradient
3. Click "Save As..." -> Choose name and location
4. Continue working with auto-tracked changes

### Opening Existing Work
1. Click "Open" -> Browse to .mesh file
2. File loads with all settings preserved
3. Make changes (unsaved indicator appears)
4. Click "Save" to update the file

### Managing Multiple Documents
1. Work on one mesh
2. Click "Save As..." to save with a name
3. Click "New" to start another
4. Use "Open" to switch between saved documents
