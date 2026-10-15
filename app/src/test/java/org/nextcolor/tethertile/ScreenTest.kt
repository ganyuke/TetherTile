package org.nextcolor.tethertile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nextcolor.tethertile.ui.SetupScreen
import org.nextcolor.tethertile.ui.SetupState
import org.nextcolor.tethertile.ui.TetherTileTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the setup screen to app/build/screenshots so it can be looked at without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class ScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String, state: SetupState, dark: Boolean = false) {
        rule.setContent {
            TetherTileTheme(dark = dark) { SetupScreen(state, {}, {}, {}, {}, {}) }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun notInstalled() = shot("1-not-installed", SetupState(Shell.Status.NotInstalled, false, -1, false, null))

    @Test fun noPermission() = shot("2-no-permission", SetupState(Shell.Status.NoPermission, false, -1, false, null))

    @Test fun ready() = shot("3-ready", SetupState(Shell.Status.Ready, true, 1, false, null))

    @Test fun readyDark() = shot("5-ready-dark", SetupState(Shell.Status.Ready, true, 1, false, null), dark = true)

    @Test fun failed() = shot("4-failed-dark", SetupState(Shell.Status.Ready, true, 0, false, "Couldn't turn on Bluetooth"), dark = true)
}
