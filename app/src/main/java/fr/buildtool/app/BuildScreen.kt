package fr.buildtool.app

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Delete
import java.text.DateFormat
import java.util.Date
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(vm: BuildViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scroll = rememberScrollState()
    val installScope = rememberCoroutineScope()
    var showHistory by remember { mutableStateOf(false) }

    // Enregistre dans l'historique chaque build termine (reussi ou echoue).
    LaunchedEffect(state.phase, state.buildStatus) {
        if (state.phase == UiState.Phase.DONE &&
            (state.buildStatus == "success" || state.buildStatus == "failed")
        ) {
            HistoryStore.record(ctx, state.url, state.buildStatus!!)
        }
    }

    // auto-scroll des logs vers le bas quand ils grandissent
    LaunchedEffect(state.logLines.size) {
        if (state.logLines.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("APKforge", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Filled.History,
                            contentDescription = stringResource(R.string.history_cd))
                    }
                    LanguageMenu()
                },
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CrashCard()

            ServerBanner(state, onSetup = vm::runSetup, onRetry = vm::checkServer)

            // --- Saisie URL ---
            OutlinedTextField(
                value = state.url,
                onValueChange = vm::onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                label = { Text(stringResource(R.string.url_label)) },
                placeholder = { Text(stringResource(R.string.url_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                enabled = state.phase != UiState.Phase.BUILDING &&
                          state.phase != UiState.Phase.SETUP,
            )

            // --- Bouton principal ---
            val busy = state.phase == UiState.Phase.BUILDING ||
                       state.phase == UiState.Phase.SETUP
            Button(
                onClick = { if (busy) Unit else vm.startBuild() },
                enabled = !busy && state.serverReachable == true &&
                          state.chainReady && state.url.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                if (busy) {
                    if (state.phase == UiState.Phase.BUILDING) {
                        // Pendant la compilation : marteau qui frappe l'Android.
                        // Même teinte d'accent que l'éclair du titre et le checkmark
                        // (colorScheme.primary). La couleur étant passée explicitement
                        // au Canvas, elle n'est pas atténuée par le bouton désactivé.
                        ForgingHammer(
                            size = 30.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.state_compiling))
                    } else {
                        // Pendant l'installation/téléchargement : spinner neutre.
                        CircularProgressIndicator(
                            Modifier.size(20.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.state_installing))
                    }
                } else {
                    Icon(Icons.Filled.Bolt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_compile), style = MaterialTheme.typography.titleMedium)
                }
            }

            // --- Bandeau de resultat ---
            AnimatedVisibility(
                visible = state.phase == UiState.Phase.DONE,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ResultCard(
                    status = state.buildStatus,
                    apkUrl = state.apkReadyUrl,
                    apkFileName = apkFileNameFrom(state.url),
                    onInstall = { url ->
                        installScope.launch {
                            val apk = ApkInstaller.download(ctx, url)
                            if (apk != null) {
                                runCatching { ApkInstaller.install(ctx, apk) }
                                    .onFailure {
                                        Toast.makeText(ctx,
                                            ctx.getString(R.string.install_failed),
                                            Toast.LENGTH_LONG).show()
                                    }
                            } else {
                                Toast.makeText(ctx,
                                    ctx.getString(R.string.install_download_failed),
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onReset = vm::reset,
                )
            }

            // --- Console de logs ---
            LogConsole(
                lines = state.logLines,
                scroll = scroll,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }

        if (showHistory) {
            HistorySheet(
                onDismiss = { showHistory = false },
                onPick = { url ->
                    vm.onUrlChange(url)
                    showHistory = false
                },
            )
        }
    }
}

@Composable
private fun ServerBanner(
    state: UiState,
    onSetup: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.serverReachable == null -> {
            AssistChipRow(stringResource(R.string.server_connecting), null)
        }
        state.serverReachable == false -> {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.server_not_found_title),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.server_not_found_body),
                        style = MaterialTheme.typography.bodyMedium)
                    SetupButtons()
                    FilledTonalButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.btn_retry))
                    }
                }
            }
        }
        !state.chainReady -> {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.first_setup_title),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.first_setup_body),
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onSetup) {
                        Icon(Icons.Filled.Bolt, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.btn_install_chain))
                    }
                }
            }
        }
        else -> {
            // Pret : on indique aussi quelle chaine sera utilisee.
            val chainLabel = when {
                state.nativeReady -> stringResource(R.string.chain_native)
                state.prootReady -> stringResource(R.string.chain_proot)
                else -> null
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChipRow(stringResource(R.string.ready_to_compile),
                    Icons.Filled.CheckCircle)
                if (chainLabel != null) {
                    AssistChip(
                        onClick = {},
                        label = { Text(chainLabel) },
                        leadingIcon = {
                            Icon(
                                if (state.nativeReady) Icons.Filled.Bolt
                                else Icons.Filled.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistChipRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultCard(
    status: String?,
    apkUrl: String?,
    apkFileName: String,
    onInstall: (String) -> Unit,
    onReset: () -> Unit,
) {
    val success = status == "success"
    val container = if (success)
        MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.errorContainer
    val onContainer = if (success)
        MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onErrorContainer

    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        val ctx = LocalContext.current
        val exportScope = rememberCoroutineScope()
        // Selecteur systeme "Enregistrer sous" : l'utilisateur choisit ou sauver
        // l'APK. Au retour, on telecharge et on ecrit dans l'URI choisi.
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
        ) { uri ->
            if (uri != null && apkUrl != null) {
                exportScope.launch {
                    val ok = ApkInstaller.exportTo(ctx, apkUrl, uri)
                    Toast.makeText(
                        ctx,
                        ctx.getString(if (ok) R.string.export_ok else R.string.export_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                    contentDescription = null, tint = onContainer)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (success) stringResource(R.string.build_success) else stringResource(R.string.build_failed),
                    style = MaterialTheme.typography.titleMedium, color = onContainer)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (success && apkUrl != null) {
                    Button(
                        onClick = { onInstall(apkUrl) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.btn_install_apk))
                    }
                    FilledTonalIconButton(
                        onClick = { exportLauncher.launch(apkFileName) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = stringResource(R.string.btn_export_apk),
                        )
                    }
                }
                FilledTonalIconButton(
                    onClick = onReset,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.btn_new_build),
                    )
                }
            }
        }
    }
}

@Composable
private fun LogConsole(
    lines: List<String>,
    scroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
    ) {
        if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.logs_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(14.dp),
                ) {
                    lines.forEach { line ->
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = colorForLine(line),
                            softWrap = true,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
                // Bouton copier : posé en haut à droite, par-dessus les logs.
                FilledTonalIconButton(
                    onClick = {
                        val cm = ctx.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(
                            ClipData.newPlainText(ctx.getString(R.string.logs_clip_label), lines.joinToString("\n")))
                        Toast.makeText(ctx, ctx.getString(R.string.logs_copied), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(36.dp),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.logs_copy_cd),
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Deux boutons de configuration : install complete / demarrage rapide.
 *  Chaque bouton copie le script correspondant et tente d'ouvrir Termux. */
@Composable
private fun SetupButtons() {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val script = readAsset(ctx, "forge-install.sh")
                copyToClipboard(ctx, "forge-install", script)
                val msg = openTermux(ctx)
                toast(ctx, ctx.getString(R.string.toast_install_copied, msg))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Bolt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.btn_first_install))
        }
        FilledTonalButton(
            onClick = {
                val script = readAsset(ctx, "forge-start.sh")
                copyToClipboard(ctx, "forge-start", script)
                val msg = openTermux(ctx)
                toast(ctx, ctx.getString(R.string.toast_start_copied, msg))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.btn_start_server))
        }
        Text(
            stringResource(R.string.setup_clipboard_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
@Composable
private fun colorForLine(line: String): Color {
    val l = line.lowercase()
    return when {
        "build successful" in l || "réussi" in l || "success" in l ->
            MaterialTheme.colorScheme.primary
        "error" in l || "failed" in l || "échec" in l || "erreur" in l ->
            MaterialTheme.colorScheme.error
        line.startsWith("$") || line.startsWith("===") ->
            MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
}

/**
 * Animation du logo Forge en action : un marteau qui frappe en rythme une tête
 * d'Android, qui rebondit (se compresse) à chaque impact. Dessinée au Canvas
 * pour rester nette à toute taille et indépendante d'assets externes.
 *
 * Cycle (1 s) : le marteau remonte (armé), redescend, frappe, la tête encaisse,
 * puis tout se relâche. RepeatMode.Restart pour un martèlement régulier.
 */
@Composable
private fun ForgingHammer(
    size: androidx.compose.ui.unit.Dp,
    color: Color,
) {
    val transition = rememberInfiniteTransition(label = "forge")

    // Facteur de frappe. 0 = armé (tête du marteau relevée), 1 = abattu (impact
    // sur le crâne). On l'anime en 0..1 puis on le mappe sur l'angle de rotation.
    val swing by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0f at 0 using LinearEasing            // armé, tête relevée
                0f at 120 using LinearEasing           // petite pause (armé)
                1f at 300 using LinearEasing           // descente rapide -> impact
                1f at 360 using LinearEasing           // maintien à l'impact
                0f at 680 using LinearEasing           // remontée
                0f at 1000 using LinearEasing
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "swing",
    )

    // Compression de la tête Android à l'impact (1 = normal, <1 = écrasée).
    val squash by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 285
                0.80f at 330 using LinearEasing      // encaisse le coup
                1.06f at 470 using LinearEasing       // léger rebond
                1f at 620 using LinearEasing
                1f at 1000
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "squash",
    )

    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val ink = color
        val u = w / 100f   // unité relative (repère 0..100)

        // --- tête d'Android (demi-dôme) en bas, qui se compresse verticalement ---
        val headW = w * 0.52f
        val headH = headW * 0.5f * squash
        val headCx = w * 0.50f
        val headBottom = h * 0.93f
        val headLeft = headCx - headW / 2f
        val headTop = headBottom - headH

        // Tête dessinée sur un calque isolé pour pouvoir "perforer" les yeux
        // (BlendMode.Clear) : ils deviennent transparents au lieu d'être blancs,
        // donc invisibles aussi bien en mode clair qu'en mode sombre.
        val eyeR = w * 0.035f
        val eyeY = headTop + headH * 0.45f
        drawContext.canvas.saveLayer(
            androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
            androidx.compose.ui.graphics.Paint(),
        )
        drawArc(
            color = ink,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(headLeft, headTop),
            size = androidx.compose.ui.geometry.Size(headW, headH * 2f),
        )
        drawCircle(Color.Transparent, eyeR,
            Offset(headCx - headW * 0.18f, eyeY), blendMode = BlendMode.Clear)
        drawCircle(Color.Transparent, eyeR,
            Offset(headCx + headW * 0.18f, eyeY), blendMode = BlendMode.Clear)
        drawContext.canvas.restore()

        drawLine(ink,
            Offset(headCx - headW * 0.20f, headTop - headH * 0.05f),
            Offset(headCx - headW * 0.30f, headTop - headH * 0.5f),
            strokeWidth = w * 0.045f, cap = StrokeCap.Round)
        drawLine(ink,
            Offset(headCx + headW * 0.20f, headTop - headH * 0.05f),
            Offset(headCx + headW * 0.30f, headTop - headH * 0.5f),
            strokeWidth = w * 0.045f, cap = StrokeCap.Round)

        // --- marteau ---
        // Le PIVOT est le bout GAUCHE du manche : il reste quasi fixe. C'est la
        // TÊTE du marteau (au bout droit) qui décrit un arc de cercle vertical et
        // vient frapper le sommet du crâne, bien à plat.
        //   armé   = -64°  (tête relevée en l'air)
        //   abattu =   0°  (manche horizontal -> frappe plate sur le crâne)
        val mhW = w * 0.15f      // épaisseur de la tête (le long du manche)
        val mhH = w * 0.32f      // hauteur de la tête (perpendiculaire)
        val handleLen = w * 0.40f
        val handleThick = w * 0.07f

        // Point d'impact : centre de la tête juste au-dessus du crâne (frappe plate).
        val impactHeadCx = headCx
        val impactHeadCy = headTop - mhH * 0.5f + w * 0.015f
        // Pivot = à gauche de ce point, à la même hauteur -> manche horizontal à l'impact.
        val pivot = Offset(impactHeadCx - handleLen, impactHeadCy)

        val armedDeg = -64f
        val angle = armedDeg + swing * (0f - armedDeg)   // -64° (armé) -> 0° (impact)

        rotate(degrees = angle, pivot = pivot) {
            // Manche : du pivot (gauche) vers la droite.
            val headCenter = Offset(pivot.x + handleLen, pivot.y)
            drawLine(
                ink,
                start = pivot,
                end = headCenter,
                strokeWidth = handleThick,
                cap = StrokeCap.Round,
            )
            // Tête du marteau : bloc perpendiculaire au bout droit du manche.
            drawRoundRect(
                color = ink,
                topLeft = Offset(headCenter.x - mhW / 2f, headCenter.y - mhH / 2f),
                size = androidx.compose.ui.geometry.Size(mhW, mhH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * u, 4f * u),
            )
        }
    }
}

/**
 * Sélecteur de langue dans la barre du haut. Met à jour LocaleManager, qui
 * recompose l'UI avec un Context localisé SANS recréer l'activité (donc sans
 * flash). Le choix (français, anglais, ou suivi du système) est persisté.
 */
@Composable
private fun LanguageMenu() {
    var expanded by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Language,
                contentDescription = stringResource(R.string.language_cd))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_system)) },
                onClick = {
                    expanded = false
                    LocaleManager.set(ctx, null)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_french)) },
                onClick = {
                    expanded = false
                    LocaleManager.set(ctx, "fr")
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_english)) },
                onClick = {
                    expanded = false
                    LocaleManager.set(ctx, "en")
                },
            )
        }
    }
}

/**
 * Affiche le dernier crash enregistre (s'il y en a un), avec boutons Copier et
 * Effacer. Sert a recuperer facilement la stacktrace pour diagnostiquer un bug.
 */
@Composable
private fun CrashCard() {
    val ctx = LocalContext.current
    var crash by remember { mutableStateOf(CrashLogger.lastCrash(ctx)) }
    val text = crash ?: return

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text("Dernier crash détecté",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Text(
                text.take(1500),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    val clip = ctx.getSystemService(ClipboardManager::class.java)
                    clip?.setPrimaryClip(ClipData.newPlainText("APKforge crash", text))
                    Toast.makeText(ctx, "Crash copié", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp)); Text("Copier")
                }
                TextButton(onClick = {
                    CrashLogger.clear(ctx); crash = null
                }) { Text("Effacer") }
            }
        }
    }
}

/**
 * Feuille modale listant l'historique des depots compiles. Entrees epinglees en
 * tete. Tap sur une entree -> remplit le champ URL. Actions : epingler, supprimer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var entries by remember { mutableStateOf(HistoryStore.load(ctx)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleLarge,
            )

            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                entries.forEach { e ->
                    ListItem(
                        headlineContent = {
                            Text(e.url, style = MaterialTheme.typography.bodyMedium)
                        },
                        supportingContent = {
                            val ok = e.status == "success"
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (ok) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    dateFmt.format(Date(e.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        leadingContent = {
                            IconButton(onClick = {
                                HistoryStore.togglePin(ctx, e.url)
                                entries = HistoryStore.load(ctx)
                            }) {
                                Icon(
                                    if (e.pinned) Icons.Filled.PushPin
                                    else Icons.Outlined.PushPin,
                                    contentDescription = stringResource(R.string.history_pin),
                                    tint = if (e.pinned) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                HistoryStore.remove(ctx, e.url)
                                entries = HistoryStore.load(ctx)
                            }) {
                                Icon(Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.history_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.clickable { onPick(e.url) },
                    )
                }
            }
        }
    }
}


/**
 * Derive un nom de fichier APK a partir de l'URL du depot git.
 * Ex: "https://github.com/kys0ff/Backtalk.git" -> "Backtalk.apk"
 * Retombe sur "APKforge.apk" si l'URL est vide ou inexploitable.
 */
private fun apkFileNameFrom(repoUrl: String?): String {
    val cleaned = repoUrl
        ?.trim()
        ?.removeSuffix("/")
        ?.removeSuffix(".git")
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        // Garde uniquement les caracteres surs pour un nom de fichier.
        ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return if (cleaned.isNullOrBlank()) "APKforge.apk" else "$cleaned.apk"
}
