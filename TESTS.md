# Mesh Project Tests

This document describes the comprehensive test suite for the Mesh gradient editor project.

## Test Coverage

### Unit Tests (80 tests total)

#### 1. Model Classes Tests (22 tests)

**MeshPointTest.kt** (9 tests)
- MeshPoint creation with default and custom uid
- Pair to MeshPoint conversion
- Empty list to offset grid conversion
- Single point and multi-dimensional grid conversions (2x2, 3x4)
- List to saved mesh points conversion
- Round-trip conversion validation

**SavedColorTest.kt** (13 tests)
- SavedColor creation with default and custom values
- SavedColor to Color conversion (including black, white, common colors)
- Color to SavedColor conversion
- Round-trip conversion validation
- findColor utility function tests (matching uid, non-matching uid, empty list)
- Transparent and semi-transparent color handling

#### 2. Data Classes Tests (16 tests)

**MeshStateTest.kt** (16 tests)
- Default MeshState creation
- Custom MeshState creation with all parameters
- Copy operations for each property (canvasWidthMode, canvasWidth, canvasHeight, resolution, blurLevel, rows, cols)
- Multiple property changes at once
- Equality and inequality checks
- DimensionMode enum validation
- Min/max/large value handling
- Serialization compatibility

#### 3. Utility Functions Tests (32 tests)

**UtilsTest.kt** (32 tests)
- Color to hex string conversion (with/without alpha, various colors)
- Hex string to Color parsing (with/without hash, with/without alpha, with whitespace)
- Invalid hex string handling (invalid length, invalid characters, empty string)
- Round-trip hex conversion tests
- Case insensitive hex parsing
- formatFloat utility tests (integer, decimal, small, trailing zeros, negative, precision)

#### 4. State Management Tests (10 tests)

**MeshStateManagerTest.kt** (10 tests)
- Save state to JSON file
- Load state from JSON file
- Default state when file doesn't exist
- Round-trip save/load validation
- Min/max/large value persistence
- Directory creation
- Invalid JSON handling
- File overwrite behavior
- Different dimension mode combinations

## Test Infrastructure

### Build Configuration
- **Test Framework**: JUnit 4 with Kotlin Test
- **Build Tool**: Gradle with Kotlin Multiplatform
- **JVM Version**: Java 21 (configurable)
- **Test Source Sets**:
  - `commonTest`: Platform-independent tests
  - `desktopTest`: Desktop-specific tests

### Dependencies
```kotlin
commonTest.dependencies {
    implementation(libs.kotlin.test)
    implementation(libs.kotlin.test.junit)
}

desktopTest.dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.desktop.uiTestJUnit4)
    implementation(libs.junit)
}
```

## Running Tests

### Run All Tests
```bash
./gradlew allTests
```

### Run Desktop Tests Only
```bash
./gradlew desktopTest
```

### Run with Clean Build
```bash
./gradlew clean allTests
```

### Run Specific Test Class
```bash
./gradlew desktopTest --tests "model.MeshPointTest"
```

### Generate Test Report
Test reports are automatically generated at:
`composeApp/build/reports/tests/desktopTest/index.html`

## Test Organization

```
composeApp/src/
├── commonTest/kotlin/
│   ├── data/
│   │   └── MeshStateTest.kt (16 tests)
│   ├── des/c5inco/mesh/common/
│   │   └── UtilsTest.kt (32 tests)
│   └── model/
│       ├── MeshPointTest.kt (9 tests)
│       └── SavedColorTest.kt (13 tests)
└── desktopTest/kotlin/
    └── des/c5inco/mesh/data/
        └── MeshStateManagerTest.kt (10 tests)
```

## Notes

### Compose UI Tests
Compose UI tests were initially created but removed because they require an X11 display server, which is not available in headless CI/CD environments. The UI component tests that were created included:
- ColorSwatchTest
- DimensionInputFieldTest
- ParameterSwatchTest

These tests can be re-enabled in environments with graphical display capabilities.

### Floating Point Precision
Several tests account for floating point precision differences in Compose Color:
- Color values like 0.5 may be stored as slightly different values (e.g., 128/255 instead of 127/255)
- Tests use tolerance values (e.g., `assertEquals(expected, actual, 0.01f)`) where appropriate
- Alpha channel conversions account for rounding differences

### Test Isolation
MeshStateManager tests use a shared configuration file location. Tests are designed to handle pre-existing state files gracefully, though running with `clean` ensures a fresh start.

## Future Improvements

1. **Screenshot Tests**: Add visual regression tests for UI components
2. **Integration Tests**: Test complete workflows end-to-end
3. **Performance Tests**: Benchmark gradient rendering and mesh point calculations
4. **Database Tests**: Add tests for Room database operations
5. **UI Tests**: Re-enable Compose UI tests with proper headless display configuration
