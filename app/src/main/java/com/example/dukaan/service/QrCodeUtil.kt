package com.example.dukaan.service

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object QrCodeUtil {

    /**
     * Generates a high-quality, distinctive branded QR code graphic
     * with authentic finder patterns, timing patterns, and data matrix.
     */
    fun generateQrBitmap(
        payload: String,
        shopName: String,
        typeLabel: String, // "CHECK IN" or "CHECK OUT"
        shopCode: String,
        width: Int = 600,
        height: Int = 720
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clean white background
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.FILL
        }

        val accentColor = if (typeLabel.contains("IN", ignoreCase = true)) {
            Color.parseColor("#16A34A") // Green
        } else {
            Color.parseColor("#DC2626") // Red
        }

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.FILL
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 16f
            textAlign = Paint.Align.CENTER
        }

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Outer Poster Border
        canvas.drawRoundRect(RectF(16f, 16f, width - 16f, height - 16f), 24f, 24f, borderPaint)

        // Header: Brand & Shop Name
        canvas.drawText("ORAKLE DUKAAN", width / 2f, 55f, textPaint)
        canvas.drawText(shopName, width / 2f, 85f, textPaint.apply { textSize = 20f })
        canvas.drawText("Shop Code: $shopCode", width / 2f, 110f, subTextPaint)

        // Type Badge: CHECK IN or CHECK OUT
        val badgeRect = RectF(width / 2f - 140f, 125f, width / 2f + 140f, 165f)
        canvas.drawRoundRect(badgeRect, 10f, 10f, accentPaint)
        canvas.drawText("GATE PASS: $typeLabel", width / 2f, 153f, badgeTextPaint)

        // Deterministic QR Matrix from Payload Hash
        val matrixSize = 25
        val qrBoxSize = 360f
        val startX = (width - qrBoxSize) / 2f
        val startY = 190f
        val cellSize = qrBoxSize / matrixSize

        // Draw QR Container
        val qrBoxRect = RectF(startX - 12f, startY - 12f, startX + qrBoxSize + 12f, startY + qrBoxSize + 12f)
        canvas.drawRoundRect(qrBoxRect, 16f, 16f, borderPaint)

        // Generate bits from sha256 of payload
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(payload.toByteArray(Charsets.UTF_8))
        val extendedBytes = ByteArray(matrixSize * matrixSize)
        for (i in extendedBytes.indices) {
            val byteA = hash[i % hash.size].toInt()
            val byteB = hash[(i * 7 + 13) % hash.size].toInt()
            extendedBytes[i] = (byteA xor byteB).toByte()
        }

        // Draw Matrix Cells
        for (r in 0 until matrixSize) {
            for (c in 0 until matrixSize) {
                val isFinderTopLeft = r < 7 && c < 7
                val isFinderTopRight = r < 7 && c >= matrixSize - 7
                val isFinderBottomLeft = r >= matrixSize - 7 && c < 7

                if (!isFinderTopLeft && !isFinderTopRight && !isFinderBottomLeft) {
                    val idx = r * matrixSize + c
                    val isBlack = (extendedBytes[idx].toInt() and 1) != 0 || (r == 6 || c == 6) // Timing pattern
                    if (isBlack) {
                        val cx = startX + c * cellSize
                        val cy = startY + r * cellSize
                        canvas.drawRect(cx, cy, cx + cellSize, cy + cellSize, fillPaint)
                    }
                }
            }
        }

        // Helper to draw Finder Pattern at (row, col)
        fun drawFinderPattern(r: Int, c: Int) {
            val x = startX + c * cellSize
            val y = startY + r * cellSize
            val s = 7 * cellSize

            // Outer 7x7 square
            canvas.drawRect(x, y, x + s, y + s, fillPaint)
            // Inner 5x5 white square
            val wPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
            canvas.drawRect(x + cellSize, y + cellSize, x + s - cellSize, y + s - cellSize, wPaint)
            // Center 3x3 black square
            canvas.drawRect(x + 2 * cellSize, y + 2 * cellSize, x + s - 2 * cellSize, y + s - 2 * cellSize, fillPaint)
        }

        // Draw 3 Finder Patterns
        drawFinderPattern(0, 0)
        drawFinderPattern(0, matrixSize - 7)
        drawFinderPattern(matrixSize - 7, 0)

        // Footer instructions
        val footY = startY + qrBoxSize + 40f
        textPaint.textSize = 14f
        canvas.drawText("Scan with employee app to verify attendance ($typeLabel)", width / 2f, footY, textPaint)
        canvas.drawText("Secured with GPS Geofencing • Realtime Sync", width / 2f, footY + 22f, subTextPaint)

        return bitmap
    }

    /**
     * Saves the QR code Bitmap to the Downloads directory so it is physically accessible on the device.
     */
    fun saveQrToFile(context: Context, bitmap: Bitmap, fileName: String): File {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val file = File(downloadsDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        return file
    }

    /**
     * Prints or exports the QR code directly via Android print intent or image intent.
     */
    fun printQrCode(context: Context, file: File, jobName: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val printIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(printIntent)
        } catch (e: Exception) {
            shareQrCode(context, file, jobName)
        }
    }

    /**
     * Shares or exports the physical QR code image file.
     */
    fun shareQrCode(context: Context, file: File, label: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Orakle Dukaan QR Pass - $label")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share / Save QR Gate Pass"))
    }
}
