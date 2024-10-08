package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.preferences.ThemeSwitcher.readThemeValue
import ac.mdiq.podcini.preferences.UserPreferences.ThemePreference
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private const val TAG = "AppTheme"

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    )
    // Add other text styles as needed
)

val Shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(0.dp)
)

fun getColorFromAttr(context: Context, @AttrRes attrColor: Int): Int {
    val typedValue = TypedValue()
    val theme = context.theme
    theme.resolveAttribute(attrColor, typedValue, true)
    Logd(TAG, "getColorFromAttr: ${typedValue.resourceId} ${typedValue.data}")
    return if (typedValue.resourceId != 0) {
        ContextCompat.getColor(context, typedValue.resourceId)
    } else {
        typedValue.data
    }
}

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()
//private val LightColors = dynamicLightColorScheme()
//private val DarkColors = dynamicDarkColorScheme()

@Composable
fun CustomTheme(context: Context, content: @Composable () -> Unit) {
    val colors = when (readThemeValue(context)) {
        ThemePreference.LIGHT -> {
            Logd(TAG, "Light theme")
            LightColors
        }
        ThemePreference.DARK -> {
            Logd(TAG, "Dark theme")
            DarkColors
        }
        ThemePreference.BLACK -> {
            Logd(TAG, "Dark theme")
            DarkColors.copy(surface = Color(0xFF000000))
        }
        ThemePreference.SYSTEM -> {
            if (isSystemInDarkTheme()) {
                Logd(TAG, "System Dark theme")
                DarkColors
            } else {
                Logd(TAG, "System Light theme")
                LightColors
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
