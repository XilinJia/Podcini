package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.ThemeSwitcher.readThemeValue
import ac.mdiq.podcini.preferences.UserPreferences.ThemePreference
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val TAG = "AppTheme"

val Typography = Typography(
    h1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp
    ),
    body1 = TextStyle(
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

fun getPrimaryColor(context: Context): Color {
    return Color(getColorFromAttr(context, R.attr.colorPrimary))
}

fun getSecondaryColor(context: Context): Color {
    return Color(getColorFromAttr(context, R.attr.colorSecondary))
}

val LightColors = lightColors(
    primary = Color(0xFF6200EE),
    primaryVariant = Color(0xFF3700B3),
    secondary = Color(0xFF03DAC6),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFB00020),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    onError = Color(0xFFFFFFFF)
)

val DarkColors = darkColors(
    primary = Color(0xFFBB86FC),
    primaryVariant = Color(0xFF3700B3),
    secondary = Color(0xFF03DAC6),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    error = Color(0xFFCF6679),
    onPrimary = Color(0xFF000000),
    onSecondary = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onError = Color(0xFF000000)
)

@Composable
fun CustomTheme(context: Context, content: @Composable () -> Unit) {
//    val primaryColor = getPrimaryColor(context)
//    val secondaryColor = getSecondaryColor(context)

    val colors = when (readThemeValue(context)) {
        ThemePreference.LIGHT -> {
            Logd(TAG, "Light theme")
            LightColors
        }
        ThemePreference.DARK, ThemePreference.BLACK -> {
            Logd(TAG, "Dark theme")
            DarkColors
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
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
