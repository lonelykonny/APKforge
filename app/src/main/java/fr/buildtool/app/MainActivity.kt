package fr.buildtool.app

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Restaure la langue choisie avant d'attacher le contexte de base.
        LocaleManager.load(newBase)
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // On lit LocaleManager.current : tout changement recompose ce bloc et
            // re-fournit un Context localise, SANS recreer l'activite (pas de flash).
            val lang = LocaleManager.current
            val baseCtx = LocalContext.current
            val localizedCtx = remember(lang) { LocaleManager.wrap(baseCtx.applicationContext) }
            CompositionLocalProvider(
                LocalContext provides localizedCtx,
                LocalConfiguration provides localizedCtx.resources.configuration,
            ) {
                ForgeTheme {
                    BuildScreen()
                }
            }
        }
    }
}

/**
 * Theme Material 3 avec couleurs dynamiques (Material You) sur Android 12+.
 * En dessous, repli sur une palette neutre et lisible.
 */
@Composable
fun ForgeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
