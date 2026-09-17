package com.gesturehome

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.gesturehome.devices.Device
import com.gesturehome.devices.DeviceStore
import com.gesturehome.devices.PhoneDeviceBackend
import com.gesturehome.gesture.GestureAnalyzer
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private var analyzer: GestureAnalyzer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val backend = PhoneDeviceBackend(this) { brightness ->
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
        val store = DeviceStore(backend)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var hasCamera by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                    )
                }
                var lastEvent by remember { mutableStateOf("Waiting for a gesture…") }

                val launcher = rememberLauncherForCameraPermission { hasCamera = it }
                LaunchedEffect(Unit) { if (!hasCamera) launcher() }

                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text("GestureHome", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Touchless control. All recognition runs on this phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        if (hasCamera) {
                            CameraPane(
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                onAnalyzerReady = { analyzer = it },
                                onGestureText = { lastEvent = it },
                                store = store,
                            )
                        } else {
                            Card(Modifier.fillMaxWidth()) {
                                Text("Camera access is needed to read gestures.", Modifier.padding(16.dp))
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Text(lastEvent, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                        }

                        Spacer(Modifier.height(16.dp))
                        Text("This device", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(store.devices, key = { it.id }) { device -> DeviceRow(device) }
                            item {
                                Spacer(Modifier.height(16.dp))
                                Text("Recent actions", fontWeight = FontWeight.SemiBold)
                            }
                            items(store.log) { line ->
                                Text("• $line", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        analyzer?.close()
        analyzerExecutor.shutdown()
        super.onDestroy()
    }

    @Composable
    private fun rememberLauncherForCameraPermission(onResult: (Boolean) -> Unit): () -> Unit {
        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> onResult(granted) }
        return { launcher.launch(Manifest.permission.CAMERA) }
    }

    @Composable
    private fun CameraPane(
        modifier: Modifier,
        store: DeviceStore,
        onAnalyzerReady: (GestureAnalyzer) -> Unit,
        onGestureText: (String) -> Unit,
    ) {
        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()

                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val gestureAnalyzer = GestureAnalyzer(
                        context = ctx,
                        onGesture = { event ->
                            val result = store.handle(event)
                            previewView.post { onGestureText(result) }
                        },
                        onStatus = { status -> previewView.post { onGestureText(status) } },
                    )
                    onAnalyzerReady(gestureAnalyzer)

                    val analysis = ImageAnalysis.Builder()
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(analyzerExecutor, gestureAnalyzer) }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        analysis
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )
    }
}

@Composable
private fun DeviceRow(device: Device) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.Medium)
                Text(
                    if (device.dimmable) "${device.room} · ${device.level}%" else device.room,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Badge(
                containerColor = if (device.isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (device.isOn) MaterialTheme.colorScheme.onPrimary else Color.Gray
            ) { Text(if (device.isOn) "ON" else "OFF") }
        }
    }
}
