package dev.lukat.briefing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens with a real edition to PNG, so the design can be checked
 * without a device:  gradle testDebugUnitTest --tests "*Screenshots*"
 * Output: app/build/screenshots/<name>.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class Screenshots {
    @get:Rule val compose = createComposeRule()

    private val feed: Feed by lazy {
        parseFeed(javaClass.classLoader!!.getResource("edition.json")!!.readText())
    }

    private fun shoot(name: String, light: Boolean, content: @Composable () -> Unit) {
        Palette.light = light
        compose.setContent {
            MaterialTheme(colorScheme = if (light) lightColorScheme(background = Bg, surface = Panel) else darkColorScheme(background = Bg, surface = Panel)) {
                Box(Modifier.fillMaxSize().background(Bg)) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Composable
    private fun home(wide: Boolean) = HomeScreen(
        feed = feed, wide = wide, offline = false, dates = listOf(feed.date, "2026-11-16", "2026-11-15", "2026-11-14"),
        selected = feed.date, read = emptySet(), savedCount = 2, updateLabel = null, updateError = null,
        onUpdate = {}, onSelectDate = {}, onLatest = {}, onCategory = {}, onSearch = {}, onSaved = {},
        refreshing = false, onRefresh = {}
    )

    @Composable
    private fun category(name: String, wide: Boolean) = CategoryScreen(
        feed = feed, name = feed.sections.first { it.name.contains(name) }.name, wide = wide,
        read = emptySet(), onSwitch = {}, onBack = {}, onOpen = { _, _ -> }
    )

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun homeDarkTall() = shoot("home-dark-tall", false) { home(false) }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun homeLight() = shoot("home-light", true) { home(false) }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun worldDark() = shoot("cat-world-dark", false) { category("World", false) }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun moneyLight() = shoot("cat-money-light", true) { category("Money", false) }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun footballDark() = shoot("cat-football-dark", false) { category("Football", false) }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun tonightDark() = shoot("cat-tonight-dark", false) { category("Tonight", false) }

    @Test @Config(qualifiers = "w841dp-h900dp-xhdpi")
    fun homeFold() = shoot("home-fold", false) { home(true) }

    @Test @Config(qualifiers = "w841dp-h900dp-xhdpi")
    fun worldFold() = shoot("cat-world-fold", false) { category("World", true) }
}
