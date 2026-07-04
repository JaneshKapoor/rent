package com.rent.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rent.app.data.ContributionRepository
import com.rent.app.data.RentDataStore
import com.rent.app.widget.HeatmapPalette
import com.rent.app.widget.RentWidget
import com.rent.app.widget.RentWidgetReceiver
import com.rent.app.work.RefreshScheduler
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ScreenBg = Color(0xFF0D1117)
private val CardBg = Color(0xFF161B22)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8B949E)
private val Accent = Color(0xFF3FB950)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = RentDataStore(applicationContext)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) {
                Surface(modifier = Modifier.fillMaxSize(), color = ScreenBg) {
                    RentSettingsApp(store)
                }
            }
        }
    }
}

@Composable
private fun RentSettingsApp(store: RentDataStore) {
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Rent",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
        )
        TabRow(selectedTabIndex = tab, containerColor = ScreenBg, contentColor = Accent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("SETTINGS") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("ABOUT APP") })
        }
        when (tab) {
            0 -> SettingsTab(store)
            else -> AboutTab()
        }
    }
}

@Composable
private fun SettingsTab(store: RentDataStore) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appContext = context.applicationContext

    var loaded by remember { mutableStateOf(false) }

    var username by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var threshold by remember { mutableStateOf(RentDataStore.DEFAULT_THRESHOLD.toString()) }
    var palette by remember { mutableStateOf(HeatmapPalette.GREEN) }
    var darkMode by remember { mutableStateOf(RentDataStore.DEFAULT_DARK_MODE) }
    var opacity by remember { mutableIntStateOf(RentDataStore.DEFAULT_OPACITY) }
    var margin by remember { mutableIntStateOf(RentDataStore.DEFAULT_MARGIN) }
    var weeks by remember { mutableIntStateOf(RentDataStore.DEFAULT_WEEKS) }
    var autoUpdate by remember { mutableStateOf(RentDataStore.DEFAULT_AUTO_UPDATE) }
    var status by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Track the data-affecting fields so we only re-fetch when they change.
    var initialUsername by remember { mutableStateOf("") }
    var initialToken by remember { mutableStateOf("") }
    var initialThreshold by remember { mutableIntStateOf(RentDataStore.DEFAULT_THRESHOLD) }

    if (!loaded) {
        loaded = true
        scope.launch {
            val s = store.getSettings()
            username = s.username
            token = s.token
            threshold = s.threshold.toString()
            palette = s.palette
            darkMode = s.darkMode
            opacity = s.backgroundOpacity
            margin = s.marginDp
            weeks = s.weeksToShow
            autoUpdate = s.autoUpdate
            initialUsername = s.username
            initialToken = s.token
            initialThreshold = s.threshold
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // --- GitHub account (data-related) ---
        SectionLabel("GitHub username")
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        SectionLabel("Personal Access Token (optional)")
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        HelperText(
            "Blank = public contributions page. Provide a token to use the more " +
                "reliable GraphQL API."
        )

        SectionLabel("Daily contribution threshold")
        OutlinedTextField(
            value = threshold,
            onValueChange = { new -> threshold = new.filter { it.isDigit() }.take(4) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Divider()

        // --- Appearance ---
        SectionLabel("Contributions color")
        PalettePicker(selected = palette, onSelect = { palette = it })

        SliderRow(
            title = "Weeks to show",
            valueLabel = if (weeks == 1) "1 week" else "$weeks weeks",
            value = weeks.toFloat(),
            range = RentDataStore.MIN_WEEKS.toFloat()..RentDataStore.MAX_WEEKS.toFloat(),
            onChange = { weeks = it.roundToInt() }
        )
        HelperText("How many weeks of the contribution graph the widget displays.")

        DarkModeRow(checked = darkMode, onChange = { darkMode = it })

        SliderRow(
            title = "Background opacity",
            valueLabel = "$opacity%",
            value = opacity.toFloat(),
            range = 0f..100f,
            onChange = { opacity = it.roundToInt() }
        )
        HelperText("Lower opacity lets the widget blend with your wallpaper.")

        SliderRow(
            title = "Top & bottom margin",
            valueLabel = "${margin}px",
            value = margin.toFloat(),
            range = 0f..RentDataStore.MAX_MARGIN.toFloat(),
            onChange = { margin = it.roundToInt() }
        )

        Divider()

        // --- Behavior ---
        CheckRow(
            checked = autoUpdate,
            onChange = { autoUpdate = it },
            title = "Auto update",
            subtitle = "Automatically acquires data twice a day"
        )
        HelperText("If auto update stops, please turn off battery optimization.")
        OutlinedButton(
            onClick = { requestIgnoreBatteryOptimizations(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Turn off battery optimization")
        }

        Divider()

        Button(
            onClick = {
                val user = username.trim()
                val th = threshold.toIntOrNull() ?: RentDataStore.DEFAULT_THRESHOLD
                val trimmedToken = token.trim()
                saving = true
                status = "Saving…"
                scope.launch {
                    store.saveSettings(
                        username = user,
                        token = trimmedToken,
                        threshold = th,
                        palette = palette,
                        darkMode = darkMode,
                        backgroundOpacity = opacity,
                        marginDp = margin,
                        autoUpdate = autoUpdate,
                        weeksToShow = weeks
                    )
                    RefreshScheduler.applyAutoUpdate(appContext, autoUpdate)

                    if (user.isNotBlank()) {
                        // Always fetch a full year of fresh data inline so the
                        // heatmap has enough history for the selected week count.
                        status = "Fetching latest from GitHub…"
                        ContributionRepository.get(appContext).refresh()
                    }
                    // Re-render every widget instance with the new state/appearance.
                    RentWidget.updateAll(appContext)

                    initialUsername = user
                    initialToken = trimmedToken
                    initialThreshold = th
                    saving = false
                    status = when {
                        user.isBlank() -> "Enter a username to start tracking."
                        else -> "Saved & widget refreshed."
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (saving) "Working…" else "Save & Refresh Widget")
        }

        status?.let { Text(text = it, color = Accent) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PalettePicker(selected: HeatmapPalette, onSelect: (HeatmapPalette) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeatmapPalette.entries.forEach { p ->
                val isSelected = p == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardBg)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Accent else Color(0xFF30363D),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelect(p) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        p.swatches.forEach { c -> Swatch(c, 12.dp) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = p.displayName,
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        // "Less -> More" preview of the currently selected palette.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Less", color = TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                selected.swatches.forEach { c -> Swatch(c, 16.dp) }
            }
            Spacer(Modifier.width(8.dp))
            Text("More", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Swatch(color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
    )
}

@Composable
private fun DarkModeRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    CheckRow(
        checked = checked,
        onChange = onChange,
        title = "Dark mode",
        subtitle = "Set background color to BLACK"
    )
}

@Composable
private fun CheckRow(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(valueLabel, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun HelperText(text: String) {
    Text(text = text, color = TextSecondary, fontSize = 12.sp)
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFF21262D))
    )
}

@Composable
private fun AboutTab() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Rent", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Version 1.0", color = TextSecondary, fontSize = 13.sp)
        OutlinedButton(
            onClick = { requestPinWidget(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Rent widget to home screen")
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Pay your GitHub rent. Rent shows a home-screen widget with your current " +
                "contribution streak and a heatmap of the last several weeks.",
            color = TextPrimary,
            fontSize = 14.sp
        )
        Text(
            "Data comes from your public GitHub contributions page, or from the GitHub " +
                "GraphQL API when you provide a Personal Access Token. Nothing leaves your " +
                "device except requests to GitHub.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Text(
            "Rent Paid ✅ when today's contributions meet your threshold; Rent Due ⚠️ " +
                "otherwise. Your streak stays intact until a day fully ends unpaid.",
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}

/**
 * Asks the launcher to pin the Rent widget to the home screen (API 26+).
 * Falls back to a hint if the launcher doesn't support pinning.
 */
private fun requestPinWidget(context: Context) {
    val manager = context.getSystemService(AppWidgetManager::class.java)
    val provider = ComponentName(context, RentWidgetReceiver::class.java)
    if (manager != null && manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(provider, null, null)
    } else {
        Toast.makeText(
            context,
            "Add it from your launcher's widget tray (long-press home → Widgets → Rent).",
            Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * Requests exemption from battery optimization so the periodic worker keeps
 * running. Checks first to avoid re-prompting when already exempt.
 */
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
        Toast.makeText(context, "Already exempt from battery optimization", Toast.LENGTH_SHORT)
            .show()
        return
    }
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    runCatching { context.startActivity(intent) }.onFailure {
        // Fallback: open the general battery optimization settings list.
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
