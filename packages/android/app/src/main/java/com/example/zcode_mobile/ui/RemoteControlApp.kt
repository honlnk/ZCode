package com.example.zcode_mobile.ui

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.view.Window
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateTopPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.zcode_mobile.BuildConfig
import com.example.zcode_mobile.R
import com.example.zcode_mobile.remote.RemoteControlRecentLink
import com.example.zcode_mobile.remote.RemoteControlUrlPolicy
import com.example.zcode_mobile.remote.displaySubtitle
import com.example.zcode_mobile.remote.displayTitle
import com.example.zcode_mobile.ui.theme.BrandBlue
import com.example.zcode_mobile.ui.theme.BrandBlueDark
import com.example.zcode_mobile.ui.theme.NeutralDarkBackground
import com.example.zcode_mobile.ui.theme.NeutralDarkSubtle
import com.example.zcode_mobile.ui.theme.NeutralDarkSurface
import com.example.zcode_mobile.ui.theme.NeutralDarkText
import com.example.zcode_mobile.ui.theme.NeutralLightBackground
import com.example.zcode_mobile.ui.theme.NeutralLightSubtle
import com.example.zcode_mobile.ui.theme.NeutralLightSurface
import com.example.zcode_mobile.ui.theme.NeutralLightText
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun RemoteControlApp(
    remoteUrl: String?,
    recentLinks: List<RemoteControlRecentLink>,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Boolean,
    onCloseRemote: () -> Unit,
    onClearRecentLinks: () -> Unit,
) {
    val context = LocalContext.current
    var scannerOpen by rememberSaveable { mutableStateOf(false) }
    var launcherError by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraPermissionDenied = stringResource(R.string.remote_scan_permission_denied)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launcherError = null
            scannerOpen = true
        } else {
            launcherError = cameraPermissionDenied
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        if (scannerOpen) {
            RemoteControlQrScanner(
                onClose = {
                    scannerOpen = false
                },
                onScanned = { raw ->
                    val opened = onOpenUrl(raw)
                    if (opened) {
                        scannerOpen = false
                        launcherError = null
                    } else {
                        launcherError = context.getString(R.string.remote_scan_invalid)
                    }
                    opened
                },
            )
        } else if (remoteUrl == null) {
            RemoteControlLauncher(
                recentLinks = recentLinks,
                errorMessage = launcherError,
                onOpenUrl = { raw ->
                    val opened = onOpenUrl(raw)
                    if (opened) {
                        launcherError = null
                    }
                    opened
                },
                onScanQrCode = {
                    if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        launcherError = null
                        scannerOpen = true
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onClearRecentLinks = onClearRecentLinks,
            )
        } else {
            RemoteControlWebViewScreen(
                remoteUrl = remoteUrl,
                onCloseRemote = onCloseRemote,
            )
        }
    }
}

@Composable
private fun RemoteControlQrScanner(
    onClose: () -> Unit,
    onScanned: (String) -> Boolean,
) {
    var scanError by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(onBack = onClose)

    Box(modifier = Modifier.fillMaxSize()) {
        RemoteQrCameraPreview(
            onScanned = onScanned,
            onCameraError = {
                scanError = it
            },
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.remote_scan_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.remote_scan_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.remote_close_button))
                }
            }
        }
        scanError?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 2.dp,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun RemoteQrCameraPreview(
    onScanned: (String) -> Boolean,
    onCameraError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val consumed = remember { AtomicBoolean(false) }
    val disposed = remember { AtomicBoolean(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val invalidQrMessage = stringResource(R.string.remote_scan_invalid)

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            cameraProvider?.unbindAll()
            scanner.close()
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                bindRemoteQrCamera(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    previewView = this,
                    scanner = scanner,
                    cameraExecutor = cameraExecutor,
                    consumed = consumed,
                    disposed = disposed,
                    onProviderReady = { provider ->
                        cameraProvider = provider
                    },
                    onScanned = { raw ->
                        val accepted = onScanned(raw)
                        if (!accepted) {
                            onCameraError(invalidQrMessage)
                            consumed.set(false)
                        }
                    },
                    onCameraError = onCameraError,
                )
            }
        },
    )
}

private fun bindRemoteQrCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    scanner: BarcodeScanner,
    cameraExecutor: ExecutorService,
    consumed: AtomicBoolean,
    disposed: AtomicBoolean,
    onProviderReady: (ProcessCameraProvider) -> Unit,
    onScanned: (String) -> Unit,
    onCameraError: (String) -> Unit,
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener(
        {
            val provider = runCatching { providerFuture.get() }
                .getOrElse { error ->
                    previewView.post {
                        onCameraError(error.message ?: context.getString(R.string.remote_scan_camera_failed))
                    }
                    return@addListener
                }
            if (disposed.get()) {
                provider.unbindAll()
                return@addListener
            }

            onProviderReady(provider)
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        analyzeRemoteQrImage(
                            scanner = scanner,
                            imageProxy = imageProxy,
                            consumed = consumed,
                            previewView = previewView,
                            onScanned = onScanned,
                        )
                    }
                }

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }.onFailure { error ->
                previewView.post {
                    onCameraError(error.message ?: context.getString(R.string.remote_scan_camera_failed))
                }
            }
        },
        ContextCompat.getMainExecutor(context),
    )
}

private fun analyzeRemoteQrImage(
    scanner: BarcodeScanner,
    imageProxy: ImageProxy,
    consumed: AtomicBoolean,
    previewView: PreviewView,
    onScanned: (String) -> Unit,
) {
    if (consumed.get()) {
        imageProxy.close()
        return
    }

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val rawValue = barcodes.firstNotNullOfOrNull { barcode ->
                barcode.rawValue?.takeIf { it.isNotBlank() }
            }
            if (rawValue != null && consumed.compareAndSet(false, true)) {
                previewView.post {
                    onScanned(rawValue)
                }
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

@Composable
private fun RemoteControlLauncher(
    recentLinks: List<RemoteControlRecentLink>,
    errorMessage: String?,
    onOpenUrl: (String) -> Boolean,
    onScanQrCode: () -> Unit,
    onClearRecentLinks: () -> Unit,
) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    val invalidUrlMessage = stringResource(R.string.remote_url_invalid)
    val visibleError = localError ?: errorMessage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.zcode_logo),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.remote_launcher_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.remote_launcher_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                localError = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 2,
            label = { Text(stringResource(R.string.remote_url_label)) },
            placeholder = { Text(stringResource(R.string.remote_url_placeholder)) },
            isError = visibleError != null,
            supportingText = {
                visibleError?.let {
                    Text(it)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!onOpenUrl(input)) {
                    localError = invalidUrlMessage
                }
            },
        ) {
            Text(stringResource(R.string.remote_open_button))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onScanQrCode,
        ) {
            Text(stringResource(R.string.remote_scan_button))
        }
        if (recentLinks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.remote_recent_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                TextButton(onClick = onClearRecentLinks) {
                    Text(stringResource(R.string.remote_recent_clear))
                }
            }
            recentLinks.forEach { link ->
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // Bugfix: 二维码链接很长，用户重启 App 后手输容易出错。
                        // 这里复用本地已校验过的历史记录，但打开前仍走 onOpenUrl 的白名单校验。
                        if (!onOpenUrl(link.url)) {
                            localError = invalidUrlMessage
                        }
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = link.displayTitle(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = link.displaySubtitle(context),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteControlWebViewScreen(
    remoteUrl: String,
    onCloseRemote: () -> Unit,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    // OAuth 登录弹窗（window.open）持有的对话框；页面销毁时统一关闭。
    val popupDialogs = remember { mutableListOf<Dialog>() }
    var progress by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    // Header 悬浮覆盖在网页之上、不占内容高度；可隐藏，隐藏后只留状态栏右侧的小箭头。
    var headerVisible by rememberSaveable { mutableStateOf(true) }
    // 页面实际主题由 WebView 内注入的 JS 桥上报：顶部留白带与状态栏图标跟着页面走，
    // 避免“页面深色、App 控件浅色”的割裂。
    var pageBackground by remember { mutableStateOf<Color?>(null) }
    var pageDark by remember { mutableStateOf<Boolean?>(null) }
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = pageBackground?.let { it.luminance() < 0.5f } ?: pageDark ?: systemDark
    val view = LocalView.current

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) {
            view.goBack()
        } else {
            onCloseRemote()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            popupDialogs.forEach { runCatching { it.dismiss() } }
            popupDialogs.clear()
            webView?.destroy()
            webView = null
        }
    }

    // 状态栏/导航栏图标明暗与顶部留白带对立：浅色带用深色图标，深色带用浅色图标。
    LaunchedEffect(effectiveDark) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !effectiveDark
            isAppearanceLightNavigationBars = !effectiveDark
        }
    }
    // 离开远控页时恢复成 App 主题（跟随系统）的状态栏样式。
    DisposableEffect(Unit) {
        onDispose {
            val window = (context as? Activity)?.window ?: return@onDispose
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !systemDark
                isAppearanceLightNavigationBars = !systemDark
            }
        }
    }

    val statusBarInset = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    // 状态栏高度之外再留一行，放展开/收起箭头，避免和系统状态栏图标重叠。
    val headerStripHeight = statusBarInset + 24.dp
    val stripColor = pageBackground
        ?: if (effectiveDark) NeutralDarkBackground else NeutralLightBackground

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 状态栏留白：纯色带跟随页面主题，底部右侧是唯一的展开/收起箭头。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerStripHeight)
                    .background(stripColor),
            ) {
                HeaderToggleButton(
                    expanded = headerVisible,
                    dark = effectiveDark,
                    onClick = { headerVisible = !headerVisible },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 2.dp),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        createRemoteControlWebView(
                            context = context,
                            popupDialogs = popupDialogs,
                            onPageThemeChanged = { background, dark ->
                                pageBackground = background
                                pageDark = dark
                            },
                            onLoadingChanged = { loading ->
                                isLoading = loading
                            },
                            onProgressChanged = { nextProgress ->
                                progress = nextProgress
                            },
                            onMainFrameError = { message ->
                                pageError = message
                            },
                        ).also { view ->
                            webView = view
                            view.loadUrl(remoteUrl)
                        }
                    },
                    update = { view ->
                        if (view.url != remoteUrl) {
                            pageError = null
                            view.loadUrl(remoteUrl)
                        }
                    },
                )
                if (pageError != null) {
                    RemoteControlErrorOverlay(
                        message = pageError.orEmpty(),
                        onRetry = {
                            pageError = null
                            webView?.reload()
                        },
                    )
                }
                if (headerVisible) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        color = (if (effectiveDark) NeutralDarkSurface else NeutralLightSurface)
                            .copy(alpha = 0.96f),
                        contentColor = if (effectiveDark) NeutralDarkText else NeutralLightText,
                        tonalElevation = 3.dp,
                        shadowElevation = 2.dp,
                    ) {
                        Column {
                            RemoteControlTopBar(
                                remoteUrl = remoteUrl,
                                isLoading = isLoading,
                                dark = effectiveDark,
                                onReload = {
                                    pageError = null
                                    webView?.reload()
                                },
                                onClose = onCloseRemote,
                            )
                            if (isLoading) {
                                LinearProgressIndicator(
                                    progress = { (progress.coerceIn(0, 100) / 100f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteControlTopBar(
    remoteUrl: String,
    isLoading: Boolean,
    dark: Boolean,
    onReload: () -> Unit,
    onClose: () -> Unit,
) {
    val textColor = if (dark) NeutralDarkText else NeutralLightText
    val subtleColor = if (dark) NeutralDarkSubtle else NeutralLightSubtle
    val accentColor = if (dark) BrandBlueDark else BrandBlue
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.remote_screen_title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = textColor,
            )
            Text(
                text = remoteUrl,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = subtleColor,
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = accentColor,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        TextButton(
            onClick = onReload,
            colors = ButtonDefaults.textButtonColors(contentColor = accentColor),
        ) {
            Text(stringResource(R.string.remote_reload_button))
        }
        TextButton(
            onClick = onClose,
            colors = ButtonDefaults.textButtonColors(contentColor = textColor),
        ) {
            Text(stringResource(R.string.remote_close_button))
        }
    }
}

/** 状态栏留白带里的小型半透明箭头：向下展开 Header，向上收起。 */
@Composable
private fun HeaderToggleButton(
    expanded: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chevronColor = if (dark) {
        Color.White.copy(alpha = 0.78f)
    } else {
        Color.Black.copy(alpha = 0.55f)
    }
    val circleColor = if (dark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(circleColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(9.dp)) {
            val path = Path().apply {
                if (expanded) {
                    moveTo(0f, size.height * 0.68f)
                    lineTo(size.width / 2f, size.height * 0.32f)
                    lineTo(size.width, size.height * 0.68f)
                } else {
                    moveTo(0f, size.height * 0.32f)
                    lineTo(size.width / 2f, size.height * 0.68f)
                    lineTo(size.width, size.height * 0.32f)
                }
            }
            drawPath(
                path = path,
                color = chevronColor,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

@Composable
private fun RemoteControlErrorOverlay(
    message: String,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(R.string.remote_load_failed_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.remote_retry_button))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
private fun createRemoteControlWebView(
    context: android.content.Context,
    popupDialogs: MutableList<Dialog>,
    onPageThemeChanged: (Color?, Boolean) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onMainFrameError: (String) -> Unit,
): WebView {
    CookieManager.getInstance().setAcceptCookie(true)

    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        overScrollMode = WebView.OVER_SCROLL_NEVER
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(
            RemoteControlThemeBridge(
                mainHandler = Handler(Looper.getMainLooper()),
                publishTheme = onPageThemeChanged,
            ),
            THEME_BRIDGE_NAME,
        )
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            userAgentString = "$userAgentString ZCodeAndroid/${BuildConfig.VERSION_NAME}"
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgressChanged(newProgress)
            }

            // v4 登录用 window.open 弹窗完成 OAuth。必须承接下来，否则页面收到 null
            // 并报 "Authorization popup was blocked"，登录无法完成。
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val created = createRemoteControlPopup(view, popupDialogs) ?: return false
                transport.webView = created.second
                resultMsg.sendToTarget()
                return true
            }
        }
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                onLoadingChanged(true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                onLoadingChanged(false)
                view?.evaluateJavascript(THEME_BRIDGE_SCRIPT, null)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                if (!request.isForMainFrame) {
                    return false
                }

                if (request.url.scheme == "zcode") {
                    RemoteControlUrlPolicy.resolve(request.url)?.let { view.loadUrl(it) }
                    return true
                }

                // 远控入口与 OAuth 域族留在 App 内，登录态才不会丢在系统浏览器里。
                if (RemoteControlUrlPolicy.isInAppNavigationAllowed(request.url)) {
                    return false
                }

                openExternalUrl(view.context, request.url)
                return true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame != true) {
                    return
                }
                onLoadingChanged(false)
                onMainFrameError(error?.description?.toString().orEmpty())
            }
        }
    }
}

/**
 * 为 window.open 创建承载 OAuth 登录的临时弹窗 WebView。
 * 弹窗与主 WebView 共享 Cookie 空间；授权完成回跳远控入口时关闭弹窗并把回调交给主页面。
 * 返回 null 表示创建失败（调用方应让页面感知弹窗被拒绝）。
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createRemoteControlPopup(
    parent: WebView,
    popupDialogs: MutableList<Dialog>,
): Pair<Dialog, WebView>? {
    val context = parent.context
    val dialog = Dialog(context)
    runCatching { dialog.requestWindowFeature(Window.FEATURE_NO_TITLE) }
    val popup = WebView(context)
    CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true)
    popup.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        userAgentString = parent.settings.userAgentString
    }
    popup.webChromeClient = object : WebChromeClient() {
        override fun onCloseWindow(window: WebView) {
            dialog.dismiss()
        }
    }
    popup.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            if (!request.isForMainFrame) {
                return false
            }
            val url = request.url
            if (RemoteControlUrlPolicy.isRemoteEntryUrl(url)) {
                // 授权完成回跳 /remote：把回调交给主 WebView 走 callback 登录，关闭弹窗。
                parent.loadUrl(url.toString())
                dialog.dismiss()
                return true
            }
            if (RemoteControlUrlPolicy.isInAppNavigationAllowed(url, allowZcodeNonRemote = true)) {
                return false
            }
            openExternalUrl(view.context, url)
            return true
        }
    }
    dialog.setContentView(
        popup,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ),
    )
    dialog.window?.setLayout(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    dialog.setOnDismissListener {
        popupDialogs.remove(dialog)
        runCatching { popup.destroy() }
    }
    popupDialogs.add(dialog)
    return runCatching { dialog.show() }.fold(
        onSuccess = { dialog to popup },
        onFailure = {
            popupDialogs.remove(dialog)
            runCatching { popup.destroy() }
            null
        },
    )
}

private fun openExternalUrl(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    runCatching {
        context.startActivity(intent)
    }
}

private const val THEME_BRIDGE_NAME = "ZCodeThemeBridge"
private const val THEME_LOG_TAG = "ZCodeTheme"

/**
 * 页面主题桥：把 WebView 里页面实际使用的背景色报给 App。
 * 顶部留白带与状态栏图标据此取反/同色，不再假设页面主题和系统主题一致。
 */
private class RemoteControlThemeBridge(
    private val mainHandler: Handler,
    private val publishTheme: (Color?, Boolean) -> Unit,
) {
    @JavascriptInterface
    fun onThemeChanged(backgroundColor: String?, darkClass: Boolean) {
        val parsed = parseCssColor(backgroundColor)
        Log.i(THEME_LOG_TAG, "page theme: bg=$backgroundColor darkClass=$darkClass")
        mainHandler.post { publishTheme(parsed, darkClass) }
    }

    /** 一次性诊断：确认 WebView 的 prefers-color-scheme 与页面存储的主题偏好。 */
    @JavascriptInterface
    fun onDiagnostics(info: String) {
        Log.i(THEME_LOG_TAG, "theme diagnostics: $info")
    }
}

private val CSS_RGB_PATTERN =
    Regex("""rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})(?:\s*,\s*([0-9.]+))?\s*\)""")

/** 解析 WebView getComputedStyle 返回的 rgb()/rgba() 颜色。 */
private fun parseCssColor(raw: String?): Color? {
    if (raw.isNullOrBlank()) {
        return null
    }
    val match = CSS_RGB_PATTERN.find(raw.trim().lowercase()) ?: return null
    val (red, green, blue, alphaText) = match.destructured
    val alpha = alphaText.toFloatOrNull()?.let { (it * 255f).toInt().coerceIn(0, 255) } ?: 255
    if (alpha < 32) {
        return null
    }
    return Color(
        red = red.toInt().coerceIn(0, 255),
        green = green.toInt().coerceIn(0, 255),
        blue = blue.toInt().coerceIn(0, 255),
        alpha = alpha,
    )
}

/**
 * 页面加载完成后注入：监听 <html class> 变化（页面主题切换）与系统配色变化，实时上报。
 * 页面自身是主题的唯一权威，App 只读不写，避免和 Web 端主题逻辑打架。
 */
private const val THEME_BRIDGE_SCRIPT = """
(function () {
  try {
    var root = document.documentElement;
    var readBackground = function () {
      var bg = '';
      try { bg = getComputedStyle(root).backgroundColor || ''; } catch (e) {}
      if (!bg || bg === 'transparent' || bg === 'rgba(0, 0, 0, 0)') {
        try { bg = getComputedStyle(document.body).backgroundColor || ''; } catch (e) {}
      }
      return bg;
    };
    var notify = function () {
      try {
        if (window.ZCodeThemeBridge && window.ZCodeThemeBridge.onThemeChanged) {
          window.ZCodeThemeBridge.onThemeChanged(readBackground(), root.classList.contains('dark'));
        }
      } catch (e) {}
    };
    var scheduleNotify = function () {
      notify();
      setTimeout(notify, 200);
    };
    if (!window.__zcodeThemeBridgeInstalled) {
      window.__zcodeThemeBridgeInstalled = true;
      try {
        new MutationObserver(scheduleNotify).observe(root, { attributes: true, attributeFilter: ['class'] });
      } catch (e) {}
      try {
        var mq = window.matchMedia('(prefers-color-scheme: dark)');
        if (mq.addEventListener) { mq.addEventListener('change', scheduleNotify); }
        else if (mq.addListener) { mq.addListener(scheduleNotify); }
      } catch (e) {}
      try {
        if (window.ZCodeThemeBridge && window.ZCodeThemeBridge.onDiagnostics) {
          window.ZCodeThemeBridge.onDiagnostics(JSON.stringify({
            stored: (function () { try { return localStorage.getItem('zcode-theme'); } catch (e) { return null; } })(),
            mediaDark: window.matchMedia('(prefers-color-scheme: dark)').matches,
            darkClass: root.classList.contains('dark'),
            bg: readBackground()
          }));
        }
      } catch (e) {}
    }
    notify();
  } catch (e) {}
})();
"""
