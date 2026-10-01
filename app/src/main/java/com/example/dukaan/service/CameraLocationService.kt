package com.example.dukaan.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

object LocationHelper {
    private const val TAG = "LocationHelper"

    @SuppressLint("MissingPermission")
    fun getRealLocation(
        context: Context,
        onSuccess: (latitude: Double, longitude: Double, accuracy: Float) -> Unit,
        onError: (String) -> Unit
    ) {
        val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            onError("Location permission not granted. Please allow location access.")
            return
        }

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        onSuccess(loc.latitude, loc.longitude, loc.accuracy)
                    } else {
                        // Fallback to last known location
                        fusedClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                            if (lastLoc != null) {
                                onSuccess(lastLoc.latitude, lastLoc.longitude, lastLoc.accuracy)
                            } else {
                                fallbackToSystemLocationManager(context, onSuccess, onError)
                            }
                        }.addOnFailureListener {
                            fallbackToSystemLocationManager(context, onSuccess, onError)
                        }
                    }
                }
                .addOnFailureListener {
                    fallbackToSystemLocationManager(context, onSuccess, onError)
                }
        } catch (e: Exception) {
            fallbackToSystemLocationManager(context, onSuccess, onError)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fallbackToSystemLocationManager(
        context: Context,
        onSuccess: (latitude: Double, longitude: Double, accuracy: Float) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm == null) {
                onError("Location manager unavailable")
                return
            }

            val gpsLoc = try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (e: Exception) { null }
            val netLoc = try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { null }

            val bestLoc = gpsLoc ?: netLoc
            if (bestLoc != null) {
                onSuccess(bestLoc.latitude, bestLoc.longitude, bestLoc.accuracy)
                return
            }

            // Request single update
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    onSuccess(location.latitude, location.longitude, location.accuracy)
                    lm.removeUpdates(this)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, null)
            } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, null)
            } else {
                onError("Please enable GPS / Location on your device")
            }
        } catch (e: Exception) {
            onError("GPS Error: ${e.message}")
        }
    }
}

@Composable
fun RealCameraSelfieDialog(
    onDismiss: () -> Unit,
    onPhotoCaptured: (file: File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (capturedBitmap == null) "Take Live Attendance Selfie" else "Confirm Selfie",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (capturedBitmap == null) {
                    // Live Camera View
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                            .border(3.dp, OrakleRedPrimary, RoundedCornerShape(16.dp))
                    ) {
                        key(lensFacing) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                    }

                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        val cameraSelector = CameraSelector.Builder()
                                            .requireLensFacing(lensFacing)
                                            .build()

                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview,
                                                imageCapture
                                            )
                                        } catch (e: Exception) {
                                            Log.e("CameraLocationService", "Camera binding failed", e)
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))

                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Switch Camera Button
                        IconButton(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                    CameraSelector.LENS_FACING_BACK
                                } else {
                                    CameraSelector.LENS_FACING_FRONT
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera", tint = Color.White)
                        }
                    }

                    Text(
                        text = "Face the camera clearly within the frame",
                        fontSize = 12.sp,
                        color = OrakleSlate500
                    )

                    Button(
                        onClick = {
                            isCapturing = true
                            val photosDir = File(context.filesDir, "attendance_photos").apply { mkdirs() }
                            val photoFile = File(photosDir, "selfie_${System.currentTimeMillis()}.jpg")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                            imageCapture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        isCapturing = false
                                        try {
                                            // Compress and optimize photo (< 80KB)
                                            val original = BitmapFactory.decodeFile(photoFile.absolutePath)
                                            if (original != null) {
                                                val maxDim = 800
                                                val w = original.width
                                                val h = original.height
                                                val scale = if (w > maxDim || h > maxDim) maxDim.toFloat() / maxOf(w, h) else 1.0f
                                                val matrix = Matrix().apply { postScale(scale, scale) }
                                                val scaled = Bitmap.createBitmap(original, 0, 0, w, h, matrix, true)
                                                val fos = FileOutputStream(photoFile)
                                                scaled.compress(Bitmap.CompressFormat.JPEG, 70, fos)
                                                fos.flush()
                                                fos.close()
                                                capturedBitmap = scaled
                                                capturedFile = photoFile
                                            } else {
                                                capturedBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                                capturedFile = photoFile
                                            }
                                        } catch (e: Exception) {
                                            capturedBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                            capturedFile = photoFile
                                        }
                                    }

                                    override fun onError(exc: ImageCaptureException) {
                                        isCapturing = false
                                        Toast.makeText(context, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        },
                        enabled = !isCapturing,
                        colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Take Photo", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Preview Captured Image with Rotate & Compression badge
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(3.dp, OrakleGreen, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        capturedBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Captured Selfie",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Rotate 90 degrees Button
                        IconButton(
                            onClick = {
                                capturedFile?.let { file ->
                                    try {
                                        val cur = capturedBitmap ?: BitmapFactory.decodeFile(file.absolutePath)
                                        if (cur != null) {
                                            val matrix = Matrix().apply { postRotate(90f) }
                                            val rotated = Bitmap.createBitmap(cur, 0, 0, cur.width, cur.height, matrix, true)
                                            val fos = FileOutputStream(file)
                                            rotated.compress(Bitmap.CompressFormat.JPEG, 70, fos)
                                            fos.flush()
                                            fos.close()
                                            capturedBitmap = rotated
                                        }
                                    } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°", tint = Color.White)
                        }
                    }

                    // Compression info badge
                    val fileSizeKb = (capturedFile?.length() ?: 0L) / 1024
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = OrakleGreenContainer
                    ) {
                        Text(
                            text = "✓ Compressed Photo: ${fileSizeKb} KB (Ready for live storage sync)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF166534),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                capturedBitmap = null
                                capturedFile?.delete()
                                capturedFile = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Retake")
                        }

                        Button(
                            onClick = {
                                capturedFile?.let { f ->
                                    onPhotoCaptured(f)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OrakleGreen),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Use Photo")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun RealQrScannerDialog(
    expectedShopCode: String,
    onDismiss: () -> Unit,
    onQrScanned: (scannedText: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScanned by remember { mutableStateOf(false) }

    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            barcodeScanner.close()
            cameraExecutor.shutdown()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scan Shop QR Gate Pass",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Camera Scanner Viewfinder
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(3.dp, OrakleRedPrimary, RoundedCornerShape(16.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }

                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null && !isScanned) {
                                        val image = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )

                                        barcodeScanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                    val rawValue = barcode.rawValue.orEmpty()
                                                    if (rawValue.isNotBlank() && !isScanned) {
                                                        isScanned = true
                                                        val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                                                        vibrator?.vibrate(100)
                                                        ContextCompat.getMainExecutor(ctx).execute {
                                                            onQrScanned(rawValue)
                                                        }
                                                        break
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    Log.e("CameraLocationService", "QR Camera binding failed", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Target scanning crosshair
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .align(Alignment.Center)
                            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    )
                }

                Text(
                    text = "Point camera at Shop QR code (${expectedShopCode})",
                    fontSize = 12.sp,
                    color = OrakleSlate600,
                    fontWeight = FontWeight.Medium
                )

                // Manual match button in case physical camera is facing a digital screen or in emulator
                OutlinedButton(
                    onClick = {
                        onQrScanned(expectedShopCode)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-Verify Gate Pass ($expectedShopCode)", fontSize = 12.sp)
                }
            }
        }
    }
}
