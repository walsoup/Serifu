package app.walsoup.serifu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.serifu.bergamot.BergamotNative
import app.serifu.core.model.ModelVariant
import app.serifu.core.translate.ModelRoute
import app.walsoup.serifu.R
import app.walsoup.serifu.SerifuApp
import app.walsoup.serifu.ml.BergamotModelManager
import app.walsoup.serifu.ml.DownloadError
import app.walsoup.serifu.ml.DownloadErrorKind
import app.walsoup.serifu.ml.DownloadStage
import app.walsoup.serifu.ml.LanguageModelManager
import app.walsoup.serifu.settings.ActiveEngine
import app.walsoup.serifu.settings.Settings
import app.walsoup.serifu.ui.design.ActionButton
import app.walsoup.serifu.ui.design.ActionStyle
import app.walsoup.serifu.ui.design.Serifu
import app.walsoup.serifu.ui.design.SerifuProgressBar
import app.walsoup.serifu.ui.design.StatusBadge
import app.walsoup.serifu.ui.design.targetName
import kotlinx.coroutines.CancellationException

data class ResolvedBergamotRoute(
    val route: ModelRoute? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@Composable
fun rememberBergamotRoute(
    bergamotModels: BergamotModelManager,
    sourceTag: String,
    targetTag: String,
    variant: ModelVariant,
): ResolvedBergamotRoute {
    var state by remember(sourceTag, targetTag, variant) {
        mutableStateOf(ResolvedBergamotRoute(loading = true))
    }
    LaunchedEffect(sourceTag, targetTag, variant) {
        state = ResolvedBergamotRoute(loading = true)
        state = try {
            val resolved = bergamotModels.resolveRoute(sourceTag, targetTag, variant)
            ResolvedBergamotRoute(route = resolved, loading = false, error = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            android.util.Log.w("BergamotRoute", "Failed to resolve Bergamot route", t)
            ResolvedBergamotRoute(route = null, loading = false, error = "")
        }
    }
    return state
}

@Composable
fun ActiveModelStatusCard(
    settings: Settings,
    onOpenEngine: () -> Unit,
    onOpenLanguages: () -> Unit,
    modifier: Modifier = Modifier,
    showReadyState: Boolean = true,
    resolvedRoute: ResolvedBergamotRoute? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as SerifuApp
    val downloadedCodes by LanguageModelManager.downloadedCodes.collectAsStateWithLifecycle()
    val inProgressCodes by LanguageModelManager.inProgressCodes.collectAsStateWithLifecycle()
    val mlKitErrors by LanguageModelManager.errors.collectAsStateWithLifecycle()

    val bergamotReadyKeys by app.bergamotModels.readyKeys.collectAsStateWithLifecycle()
    val bergamotInProgress by app.bergamotModels.inProgressKeys.collectAsStateWithLifecycle()
    val bergamotProgress by app.bergamotModels.downloadProgress.collectAsStateWithLifecycle()
    val bergamotErrors by app.bergamotModels.downloadErrors.collectAsStateWithLifecycle()

    val sourceName = settings.sourceLanguage.englishName
    val targetLabel = targetName(settings.targetLanguage)
    val neededCodes = remember(settings.sourceLanguage, settings.targetLanguage) {
        listOf(settings.sourceLanguage.tag, settings.targetLanguage).distinct()
    }
    val missingMlKitCodes = neededCodes.filter { it !in downloadedCodes }
    val mlKitDownloading = neededCodes.any { it in inProgressCodes }
    val mlKitError = neededCodes.firstNotNullOfOrNull { mlKitErrors[it] }

    val active = settings.activeEngine
    val usingApiOnly = active.apiOnly
    val usingApiProgressive = active.kind == ActiveEngine.Kind.API_PROGRESSIVE
    val usingBergamot = active.usingBergamot
    val apiConfigured = active.apiConfigured

    if (usingApiOnly) {
        if (!apiConfigured) {
            StatusCardContainer(attention = true, modifier = modifier) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.model_api_unconfigured),
                        style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = Serifu.colors.primaryText,
                    )
                    StatusBadge(text = stringResource(R.string.engine_api), warning = true)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.model_api_unconfigured_detail),
                    style = Serifu.type.caption,
                    color = Serifu.colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                ActionButton(
                    text = stringResource(R.string.home_configure_api_cta),
                    onClick = onOpenEngine,
                )
            }
        } else if (showReadyState) {
            val label = settings.aiModel.trim().ifEmpty { "API" }
            StatusCardContainer(attention = false, modifier = modifier) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.model_api_ready, label),
                        style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = Serifu.colors.text,
                    )
                    StatusBadge(text = stringResource(R.string.active_value))
                }
            }
        }
        return
    }

    if (usingBergamot) {
        val routeState = resolvedRoute ?: rememberBergamotRoute(
            bergamotModels = app.bergamotModels,
            sourceTag = settings.sourceLanguage.tag,
            targetTag = settings.targetLanguage,
            variant = settings.bergamotVariant,
        )
        val route = routeState.route
        val routeKeys = remember(route, bergamotReadyKeys) {
            route?.let { app.bergamotModels.routeStorageKeys(it) }.orEmpty()
        }
        val activeProgress = route?.let {
            app.bergamotModels.routeProgress(it, bergamotProgress, bergamotReadyKeys)
        }
        val activeDownloading = routeKeys.any { it in bergamotInProgress } || activeProgress != null
        val activeError = routeKeys.firstNotNullOfOrNull { bergamotErrors[it] }
        val routeReady = remember(route, bergamotReadyKeys) {
            route != null && app.bergamotModels.isRouteReady(route)
        }

        if (activeDownloading && route != null) {
            StatusCardContainer(attention = false, modifier = modifier) {
                val stageText = when (activeProgress?.stage) {
                    DownloadStage.VERIFYING -> stringResource(R.string.model_status_verifying)
                    DownloadStage.CATALOG -> stringResource(R.string.model_status_catalog)
                    else -> stringResource(R.string.model_status_downloading, "$sourceName → $targetLabel")
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stageText,
                        style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = Serifu.colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    if (activeProgress != null && activeProgress.totalFiles > 0) {
                        Text(
                            text = stringResource(
                                R.string.model_progress_file,
                                activeProgress.currentFileIndex,
                                activeProgress.totalFiles,
                            ),
                            style = Serifu.type.monoSmall,
                            color = Serifu.colors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val fraction = activeProgress?.takeIf { it.totalBytes > 0L }?.let {
                    (it.downloadedBytes.toFloat() / it.totalBytes.toFloat()).coerceIn(0f, 1f)
                }
                SerifuProgressBar(progress = fraction)
                if (activeProgress != null && activeProgress.totalBytes > 0L) {
                    Spacer(Modifier.height(6.dp))
                    val downMb = (activeProgress.downloadedBytes / (1024L * 1024L)).toInt()
                    val totalMb = ((activeProgress.totalBytes + 512L * 1024L) / (1024L * 1024L)).toInt().coerceAtLeast(1)
                    val pct = ((activeProgress.downloadedBytes * 100L) / activeProgress.totalBytes).toInt().coerceIn(0, 100)
                    Text(
                        text = stringResource(R.string.model_progress_bytes, downMb, totalMb, pct),
                        style = Serifu.type.monoSmall,
                        color = Serifu.colors.textSecondary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    text = stringResource(R.string.download_cancel),
                    onClick = { app.bergamotModels.cancelRouteDownload(route) },
                    style = ActionStyle.Ghost,
                )
            }
            return
        }

        if (activeError != null && route != null) {
            ErrorDownloadCard(
                error = activeError,
                onRetry = {
                    app.bergamotModels.startDownloadRoute(app.appScope, route, settings.wifiOnlyDownloads)
                },
                onUseMobileData = {
                    app.bergamotModels.startDownloadRoute(app.appScope, route, wifiOnly = false)
                },
                modifier = modifier,
            )
            return
        }

        if (routeReady && route != null) {
            if (showReadyState) {
                StatusCardContainer(attention = false, modifier = modifier) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.model_status_ready_pair, sourceName, targetLabel),
                                style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold),
                                color = Serifu.colors.text,
                            )
                            Text(
                                text = stringResource(R.string.engine_bergamot_ready, sourceName, targetLabel),
                                style = Serifu.type.caption,
                                color = Serifu.colors.textSecondary,
                            )
                        }
                        StatusBadge(text = "${route.sizeMb} MB")
                    }
                }
            }
            return
        }

        if (routeState.loading) {
            StatusCardContainer(attention = false, modifier = modifier) {
                Text(
                    text = stringResource(R.string.model_bergamot_resolving),
                    style = Serifu.type.body,
                    color = Serifu.colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                SerifuProgressBar(progress = null)
            }
            return
        }

        if (!BergamotNative.available || route == null) {
            StatusCardContainer(attention = true, modifier = modifier) {
                Text(
                    text = stringResource(R.string.model_bergamot_missing_short),
                    style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = Serifu.colors.primaryText,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        !BergamotNative.available -> stringResource(R.string.engine_bergamot_native_missing)
                        else -> stringResource(R.string.engine_bergamot_unsupported, sourceName, targetLabel)
                    },
                    style = Serifu.type.caption,
                    color = Serifu.colors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    text = stringResource(R.string.engine_title),
                    onClick = onOpenEngine,
                    style = ActionStyle.Ghost,
                )
            }
            return
        }

        // Bergamot route resolved but not yet downloaded.
        val variantLabel = if (settings.bergamotVariant == ModelVariant.LITE) {
            stringResource(R.string.engine_bergamot_lite)
        } else {
            stringResource(R.string.engine_bergamot_full)
        }
        val wifiBlocked = settings.wifiOnlyDownloads && !app.bergamotModels.isOnWifi()
        StatusCardContainer(attention = true, modifier = modifier) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.model_bergamot_missing_short),
                    style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = Serifu.colors.primaryText,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(text = "${route.sizeMb} MB", warning = true)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.model_bergamot_missing_detail,
                    sourceName,
                    targetLabel,
                    variantLabel,
                    route.sizeMb,
                ),
                style = Serifu.type.caption,
                color = Serifu.colors.textSecondary,
            )
            if (wifiBlocked) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.wifi_required_error),
                    style = Serifu.type.caption,
                    color = Serifu.colors.primaryText,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (wifiBlocked) {
                ActionButton(
                    text = stringResource(R.string.download_on_mobile_data),
                    onClick = {
                        app.bergamotModels.startDownloadRoute(app.appScope, route, wifiOnly = false)
                    },
                )
            } else {
                ActionButton(
                    text = stringResource(R.string.model_download_now, route.sizeMb),
                    onClick = {
                        app.bergamotModels.startDownloadRoute(app.appScope, route, settings.wifiOnlyDownloads)
                    },
                )
            }
        }
        return
    }

    // ML Kit (FAST + MLKIT) or BEST_PROGRESSIVE fallback pack status.
    if (mlKitDownloading) {
        StatusCardContainer(attention = false, modifier = modifier) {
            Text(
                text = stringResource(R.string.model_status_downloading, "$sourceName → $targetLabel"),
                style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = Serifu.colors.text,
            )
            Spacer(Modifier.height(10.dp))
            SerifuProgressBar(progress = null)
            Spacer(Modifier.height(10.dp))
            ActionButton(
                text = stringResource(R.string.download_cancel),
                onClick = { LanguageModelManager.cancelDownload(neededCodes.toSet()) },
                style = ActionStyle.Ghost,
            )
        }
        return
    }

    if (mlKitError != null) {
        ErrorDownloadCard(
            error = mlKitError,
            onRetry = {
                val codes = missingMlKitCodes.ifEmpty { neededCodes }.toSet()
                LanguageModelManager.startDownload(app.appScope, codes, settings.wifiOnlyDownloads, context)
            },
            onUseMobileData = {
                val codes = missingMlKitCodes.ifEmpty { neededCodes }.toSet()
                LanguageModelManager.startDownload(app.appScope, codes, wifiOnly = false, context)
            },
            modifier = modifier,
        )
        return
    }

    if (missingMlKitCodes.isNotEmpty()) {
        val totalMb = missingMlKitCodes.sumOf { code ->
            LanguageModelManager.ALL_PACKS.firstOrNull { it.code == code }?.sizeMb ?: 30
        }
        val wifiBlocked = settings.wifiOnlyDownloads && !LanguageModelManager.isOnWifi(context)
        StatusCardContainer(attention = true, modifier = modifier) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.model_status_needed),
                    style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = Serifu.colors.primaryText,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(text = "~$totalMb MB", warning = true)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.model_status_needed_detail, sourceName, targetLabel, totalMb),
                style = Serifu.type.caption,
                color = Serifu.colors.textSecondary,
            )
            if (wifiBlocked) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.wifi_required_error),
                    style = Serifu.type.caption,
                    color = Serifu.colors.primaryText,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (wifiBlocked) {
                ActionButton(
                    text = stringResource(R.string.download_on_mobile_data),
                    onClick = {
                        LanguageModelManager.startDownload(
                            app.appScope,
                            missingMlKitCodes.toSet(),
                            wifiOnly = false,
                            context = context,
                        )
                    },
                )
            } else {
                ActionButton(
                    text = stringResource(R.string.model_download_now, totalMb),
                    onClick = {
                        LanguageModelManager.startDownload(
                            app.appScope,
                            missingMlKitCodes.toSet(),
                            wifiOnly = settings.wifiOnlyDownloads,
                            context = context,
                        )
                    },
                )
            }
        }
        return
    }

    if (usingApiProgressive && !apiConfigured) {
        StatusCardContainer(attention = true, modifier = modifier) {
            Text(
                text = stringResource(R.string.model_api_unconfigured),
                style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = Serifu.colors.primaryText,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.model_api_unconfigured_detail),
                style = Serifu.type.caption,
                color = Serifu.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            ActionButton(
                text = stringResource(R.string.home_configure_api_cta),
                onClick = onOpenEngine,
            )
        }
        return
    }

    if (showReadyState) {
        StatusCardContainer(attention = false, modifier = modifier) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.model_status_ready_pair, sourceName, targetLabel),
                    style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = Serifu.colors.text,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(text = stringResource(R.string.model_status_ready))
            }
        }
    }
}

@Composable
fun DownloadError.label(): String = when (kind) {
    DownloadErrorKind.WIFI_REQUIRED -> stringResource(R.string.pack_error_wifi)
    DownloadErrorKind.TIMEOUT -> stringResource(R.string.pack_error_timeout)
    DownloadErrorKind.VERIFICATION_FAILED -> stringResource(R.string.pack_error_verification)
    DownloadErrorKind.NETWORK -> stringResource(R.string.pack_error_generic)
}

@Composable
private fun ErrorDownloadCard(
    error: DownloadError,
    onRetry: () -> Unit,
    onUseMobileData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Serifu.colors
    val detail = error.label()
    StatusCardContainer(attention = true, modifier = modifier) {
        Text(
            text = stringResource(R.string.model_error_title),
            style = Serifu.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = c.primaryText,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail,
            style = Serifu.type.caption,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        if (error.kind == DownloadErrorKind.WIFI_REQUIRED) {
            ActionButton(
                text = stringResource(R.string.download_on_mobile_data),
                onClick = onUseMobileData,
            )
            Spacer(Modifier.height(8.dp))
            ActionButton(
                text = stringResource(R.string.download_retry),
                onClick = onRetry,
                style = ActionStyle.Ghost,
            )
        } else {
            ActionButton(
                text = stringResource(R.string.download_retry),
                onClick = onRetry,
            )
        }
    }
}

@Composable
private fun StatusCardContainer(
    attention: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = Serifu.colors
    val shape = RoundedCornerShape(Serifu.radiusSurface)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (attention) c.surfaceAlt else c.surface)
            .border(1.5.dp, if (attention) c.primary else c.hairline, shape)
            .padding(16.dp),
    ) {
        content()
    }
}
