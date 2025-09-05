# OpenInSplittedTab - Kotlin Edition

A modern Kotlin-based IntelliJ IDEA plugin that provides Xcode-style split tab functionality.

## Overview

This plugin was converted from Java to Kotlin with a modern Gradle build system. It allows developers to open declarations/implementations in a vertically split tab, mimicking the assistant view behavior in Xcode.

## Features

- **Open in splitted tab**: Opens the selected symbol in a vertically split tab (replaces current tab)
- **Open in splitted (new) tab**: Opens the selected symbol in a new vertically split tab (preserves existing tabs)
- Automatic split pane creation when none exists
- Support for ambiguous symbol resolution with popup selection
- Fallback to standard "Go to Declaration" when no symbol is found

## Build Requirements

- **Java**: JDK 17 or higher
- **Gradle**: 8.4 or higher
- **IntelliJ IDEA**: 2023.2+ for development

## Building the Plugin

### Using Gradle Wrapper (Recommended)

```bash
# Build the plugin
./gradlew build

# Run the plugin in a sandbox IDE
./gradlew runIde

# Verify plugin compatibility
./gradlew verifyPlugin

# Build distribution
./gradlew buildPlugin
```

### Using System Gradle

```bash
gradle build
gradle runIde
gradle verifyPlugin
gradle buildPlugin
```

## Project Structure

```
OpenInSplittedTab/
├── src/main/kotlin/org/para/plugin/
│   ├── OpenInSplittedTabBaseAction.kt      # Core functionality
│   ├── OpenInSplittedTabAction.kt          # Replace tab action
│   └── OpenInSplittedNewTabAction.kt       # New tab action
├── src/main/resources/META-INF/
│   └── plugin.xml                          # Plugin configuration
├── build.gradle.kts                        # Build configuration
├── settings.gradle.kts                     # Project settings
├── gradle.properties                       # Build properties
└── old-java-files/                         # Original Java source
```

## Development

### Key Classes

- **OpenInSplittedTabBaseAction**: Abstract base class containing the core logic for symbol resolution and split tab management
- **OpenInSplittedTabAction**: Concrete implementation that replaces the current tab when opening symbols
- **OpenInSplittedNewTabAction**: Concrete implementation that preserves existing tabs when opening symbols

### Plugin Configuration

The plugin registers two actions in `plugin.xml`:

1. `opennInSplittedTab` - Opens in existing split tab
2. `opennInNewSplittedTab` - Opens in new split tab

Both actions are available in the "GoTo" menu context.

## Installation

### From Source

1. Clone the repository
2. Build the plugin: `./gradlew buildPlugin`
3. Install the generated plugin file from `build/distributions/`

### IDE Compatibility

- **Since Build**: 232 (IntelliJ IDEA 2023.2)
- **Until Build**: 242.* (IntelliJ IDEA 2024.2)

## Migration from Java

This plugin was migrated from Java to Kotlin while maintaining 100% functional compatibility. Key improvements:

- **Modern Kotlin**: Idiomatic Kotlin code with null safety
- **Gradle Build**: Modern Gradle with Kotlin DSL
- **Updated APIs**: Removed deprecated IntelliJ platform components
- **Better Structure**: Standard Gradle project layout

## License

This project maintains the same license as the original Java version.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes with tests
4. Submit a pull request

## Changelog

### 0.2.1 (Kotlin Edition)
- Converted entire codebase from Java to Kotlin
- Modernized build system with Gradle and Kotlin DSL
- Updated plugin.xml for modern IntelliJ platform
- Removed deprecated API usage
- Improved code quality with Kotlin idioms