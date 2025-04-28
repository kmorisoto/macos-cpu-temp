# macOS CPU Temperature Monitor

A menu bar application for macOS that displays the CPU temperature directly in the menu bar.

## Features

- Displays CPU temperature in the macOS menu bar
- Shows detailed temperature information in a popup menu
- Provides notifications when temperature exceeds a threshold
- Runs in the background with minimal resource usage

## Dependencies

The application uses the following dependencies:

- **Kotlin/Compose Desktop**: For the UI framework
- **JNA (Java Native Access)**: For accessing native macOS APIs
- **Java AWT**: For system tray and menu bar integration

## Implementation Details

### Menu Bar Integration

The application integrates with the macOS menu bar using Java's AWT SystemTray API. This allows the app to:

1. Display an icon with the current CPU temperature in the menu bar
2. Show a popup menu with more detailed information when clicked
3. Run without a visible window, saving screen space

### CPU Temperature Monitoring

The application uses the macOS `powermetrics` command to retrieve CPU temperature data:

```bash
sudo powermetrics -n 1 -i 1000 --samplers thermal
```

Key features of the temperature monitoring:

- Retrieves CPU die temperature every 5 seconds
- Updates the menu bar icon with the current temperature
- Displays detailed temperature information in the popup menu
- Sends notifications when temperature exceeds 80°C (configurable)
- Limits notifications to prevent alert fatigue (max 1 notification per 5 minutes)

**Note:** The `powermetrics` command requires sudo privileges. When running the application for the first time, you may be prompted to enter your password.

### Next Steps

- Add settings UI for customizing update frequency and temperature thresholds
- Add auto-start on login functionality
- Implement historical temperature tracking and graphing
- Add support for monitoring additional system metrics

## Building and Running

```bash
./gradlew run
```

## Requirements

- macOS 10.14 or later
- Java 11 or later
