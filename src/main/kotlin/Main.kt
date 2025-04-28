import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlinx.coroutines.*

@Composable
@Preview
fun App() {
    var text by remember { mutableStateOf("Hello, World!") }

    MaterialTheme {
        Button(onClick = {
            text = "Hello, Desktop!"
        }) {
            Text(text)
        }
    }
}

/**
 * Gets the CPU temperature using the powermetrics command
 * @return The CPU temperature in Celsius, or null if an error occurred
 */
fun getCpuTemperature(): Double? {
    return try {
        // Run powermetrics command with sudo to get CPU temperature
        // Note: This requires sudo privileges and may prompt for password
        val process = ProcessBuilder("sudo", "powermetrics", "-n", "1", "-i", "1000", "--samplers", "thermal").start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        var temperature: Double? = null

        // Read the output and look for CPU die temperature
        while (reader.readLine().also { line = it } != null) {
            if (line?.contains("CPU die temperature") == true) {
                // Extract the temperature value
                val tempString = line?.substringAfter("CPU die temperature: ")?.substringBefore(" C")
                temperature = tempString?.toDoubleOrNull()
                break
            }
        }

        // Wait for the process to complete
        process.waitFor(5, TimeUnit.SECONDS)
        process.destroy()

        temperature
    } catch (e: Exception) {
        println("Error getting CPU temperature: ${e.message}")
        null
    }
}

/**
 * Creates an icon with the temperature text
 * @param temperature The temperature to display
 * @return A BufferedImage with the temperature text
 */
fun createTemperatureIcon(temperature: String): BufferedImage {
    val width = 64
    val height = 22
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2d = image.createGraphics()

    // Set up the graphics context for high quality text rendering
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    // Draw the temperature text
    g2d.font = Font("SansSerif", Font.PLAIN, 12)
    g2d.color = Color.BLACK

    val metrics = g2d.fontMetrics
    val textWidth = metrics.stringWidth(temperature)
    val textHeight = metrics.height

    g2d.drawString(temperature, (width - textWidth) / 2, height - (height - textHeight) / 2)

    g2d.dispose()
    return image
}

/**
 * Updates the tray icon and menu item with the current temperature
 * @param temperature The current CPU temperature
 * @param trayIcon The tray icon to update
 * @param temperatureItem The menu item to update
 * @param threshold The temperature threshold for notifications
 * @param lastNotificationTime The time of the last notification
 * @return The updated time of the last notification
 */
fun updateTemperatureDisplay(
    temperature: Double?, 
    trayIcon: TrayIcon, 
    temperatureItem: MenuItem,
    threshold: Double = 80.0,
    lastNotificationTime: Long = 0
): Long {
    val temperatureText = if (temperature != null) {
        String.format("%.1f°C", temperature)
    } else {
        "--°C"
    }

    // Update the tray icon
    trayIcon.image = createTemperatureIcon(temperatureText)

    // Update the menu item
    temperatureItem.label = "CPU Temperature: $temperatureText"

    // Check if temperature exceeds threshold and send notification if needed
    var updatedLastNotificationTime = lastNotificationTime
    if (temperature != null && temperature > threshold) {
        val currentTime = System.currentTimeMillis()
        // Only send notification if it's been at least 5 minutes since the last one
        if (currentTime - lastNotificationTime > 5 * 60 * 1000) {
            trayIcon.displayMessage(
                "High CPU Temperature", 
                "CPU temperature is $temperatureText, which exceeds the threshold of ${threshold}°C", 
                TrayIcon.MessageType.WARNING
            )
            updatedLastNotificationTime = currentTime
        }
    }

    return updatedLastNotificationTime
}

fun main() = application {
    // Check if SystemTray is supported
    if (!SystemTray.isSupported()) {
        println("SystemTray is not supported")
        return@application
    }

    // Create a popup menu
    val popup = PopupMenu()

    // Create menu items
    val temperatureItem = MenuItem("CPU Temperature: --°C")
    val exitItem = MenuItem("Exit")

    // Add action listeners
    exitItem.addActionListener {
        exitApplication()
    }

    // Add menu items to popup menu
    popup.add(temperatureItem)
    popup.addSeparator()
    popup.add(exitItem)

    // Create an icon with temperature text
    val initialTemperature = "--°C"
    val trayIconImage = createTemperatureIcon(initialTemperature)
    val trayIcon = TrayIcon(trayIconImage, "CPU Temperature Monitor")
    trayIcon.isImageAutoSize = true
    trayIcon.popupMenu = popup

    // Add the tray icon to the system tray
    try {
        SystemTray.getSystemTray().add(trayIcon)
    } catch (e: Exception) {
        println("Error adding tray icon: ${e.message}")
        return@application
    }

    // Create a scheduled executor to update the temperature periodically
    val scheduler = Executors.newScheduledThreadPool(1)

    // Track the last notification time to prevent too many notifications
    var lastNotificationTime = 0L

    // Define temperature threshold for notifications (default 80°C)
    val temperatureThreshold = 80.0

    // Schedule the temperature update task to run every 5 seconds
    scheduler.scheduleAtFixedRate({
        try {
            val temperature = getCpuTemperature()
            lastNotificationTime = updateTemperatureDisplay(
                temperature, 
                trayIcon, 
                temperatureItem,
                temperatureThreshold,
                lastNotificationTime
            )
        } catch (e: Exception) {
            println("Error updating temperature: ${e.message}")
        }
    }, 0, 5, TimeUnit.SECONDS)

    // Cleanup when application exits
    DisposableEffect(Unit) {
        onDispose {
            scheduler.shutdown()
            SystemTray.getSystemTray().remove(trayIcon)
        }
    }
}
