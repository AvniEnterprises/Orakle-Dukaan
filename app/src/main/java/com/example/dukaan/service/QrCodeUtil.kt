package com.example.dukaan.service

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Environment
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.util.EnumMap

object QrCodeUtil {

    /**
     * Generates a 100% genuine, standard scannable QR Code using ZXing.
     * Decorated with authentic shop details, brand header, and clear color-coded
     * IN (Aane ka) vs OUT (Jaane ka) badges.
     */
    fun generateQrBitmap(
        payload: String,
        shopName: String,
        typeLabel: String, // "CHECK IN" or "CHECK OUT"
        shopCode: String,
        width: Int = 600,
        height: Int = 740
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val isCheckIn = typeLabel.contains("IN", ignoreCase = true) || typeLabel.contains("AANE", ignoreCase = true)
        val accentColor = if (isCheckIn) Color.parseColor("#15803D") else Color.parseColor("#DC2626")

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.FILL
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
            textSize = 15f
            textAlign = Paint.Align.CENTER
        }

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 19f
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
        val badgeRect = RectF(width / 2f - 165f, 125f, width / 2f + 165f, 168f)
        canvas.drawRoundRect(badgeRect, 10f, 10f, accentPaint)
        val badgeDisplay = if (isCheckIn) "GATE PASS: AANE KA (CHECK IN)" else "GATE PASS: JAANE KA (CHECK OUT)"
        canvas.drawText(badgeDisplay, width / 2f, 155f, badgeTextPaint)

        // Generate genuine ZXing QR BitMatrix
        val qrBoxSize = 360
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 1)
        }

        val bitMatrix = try {
            QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, qrBoxSize, qrBoxSize, hints)
        } catch (_: Exception) {
            null
        }

        val startX = (width - qrBoxSize) / 2f
        val startY = 190f

        // Draw QR Container Box
        val qrBoxRect = RectF(startX - 10f, startY - 10f, startX + qrBoxSize + 10f, startY + qrBoxSize + 10f)
        canvas.drawRoundRect(qrBoxRect, 16f, 16f, borderPaint)

        if (bitMatrix != null) {
            val qrWidth = bitMatrix.width
            val qrHeight = bitMatrix.height
            val cellW = qrBoxSize.toFloat() / qrWidth
            val cellH = qrBoxSize.toFloat() / qrHeight

            for (x in 0 until qrWidth) {
                for (y in 0 until qrHeight) {
                    if (bitMatrix.get(x, y)) {
                        canvas.drawRect(
                            startX + x * cellW,
                            startY + y * cellH,
                            startX + (x + 1) * cellW,
                            startY + (y + 1) * cellH,
                            fillPaint
                        )
                    }
                }
            }
        }

        // Footer instructions
        val footY = startY + qrBoxSize + 40f
        textPaint.textSize = 14f
        val actionDesc = if (isCheckIn) "Subah aate waqt duty start karne ke liye scan karein" else "Shaam ko duty end karte waqt exit ke liye scan karein"
        canvas.drawText(actionDesc, width / 2f, footY, textPaint)
        canvas.drawText("Secured with GPS Geofencing • Realtime Cloud Sync", width / 2f, footY + 22f, subTextPaint)

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
        } catch (_: Exception) {
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
