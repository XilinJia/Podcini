package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.preferences.ThemeSwitcher.readThemeValue
import ac.mdiq.podcini.preferences.UserPreferences
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

val CustomTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    // Add other text styles as needed
)

object CustomTextStyles {
    val titleCustom = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)
}

val Shapes = Shapes(small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(4.dp), large = RoundedCornerShape(0.dp))

fun getColorFromAttr(context: Context, @AttrRes attrColor: Int): Int {
    val typedValue = TypedValue()
    val theme = context.theme
    theme.resolveAttribute(attrColor, typedValue, true)
    Logd(TAG, "getColorFromAttr: ${typedValue.resourceId} ${typedValue.data}")
    return if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else { typedValue.data }
}

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun CustomTheme(context: Context, content: @Composable () -> Unit) {
    val colors = when (readThemeValue(context)) {
        ThemePreference.LIGHT -> LightColors
        ThemePreference.DARK -> DarkColors
        ThemePreference.BLACK -> DarkColors.copy(surface = Color(0xFF000000))
        ThemePreference.SYSTEM -> if (isSystemInDarkTheme()) DarkColors else LightColors
    }
    MaterialTheme(colorScheme = colors, typography = CustomTypography, shapes = Shapes, content = content)
}

fun isLightTheme(context: Context): Boolean {
    return readThemeValue(context) == UserPreferences.ThemePreference.LIGHT
}