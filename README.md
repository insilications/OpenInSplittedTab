# Open In Splitted Tab

This IntelliJ IDEA plugin opens the declaration/usage/implementation of the currently selected symbol in the next available splitted tab. If a splitted tab is already open, it uses this. If not, a new splitted tab is opened.

## Features

There are two actions available in the "Go To" menu:

- **Declaration Or Usages (Splitted Tab)**: Opens the declaration/usage of the currently selected symbol in the next available splitted tab. If a splitted tab is already open, it uses this. If not, a new splitted tab is opened. If there are multiple declarations/usages, a popup will appear, allowing you to select the relevant one.
- **Implementation(s) (Splitted Tab)**: Opens the implementation of the currently selected symbol in the next available splitted tab. If a splitted tab is already open, it uses this. If there are multiple implementations, a popup will appear, allowing you to select the relevant one.

The actions have no shortcuts assigned by default and are accessible in the "Go To".

## IDE Compatibility

- **Since Build**: 251.x (Version 2025.1)

## Building

### Build Requirements

- **Java**: JDK 21 or higher
- **Kotlin**: 2.2 or higher
- **Gradle**: 9.0 or higher

### Using Gradle Wrapper (Recommended)

```bash
# Build the plugin
./gradlew build

# Verify plugin compatibility
./gradlew verifyPlugin

# Reports available dependency upgrades
./gradlew dependencyUpdates

# Build distribution in `build/distributions/`
./gradlew buildPlugin
```

## Installation

### From Source

1. Clone the repository
2. Build the plugin: `./gradlew buildPlugin`
3. Install the generated plugin file from `build/distributions/`
