package com.lazycatlabs.media_scanner

import androidx.annotation.NonNull
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.Nullable
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/** MediaScannerPlugin */
class MediaScannerPlugin : FlutterPlugin, MethodCallHandler {
    /// Used for logging purposes
    private val tag = "MediaScannerPlugin"

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel
    private lateinit var context: Context

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "media_scanner")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "saveFile" -> {
                val path = call.argument<String>("file") ?: return
                val name = call.argument<String>("name")
                result.success(saveFileToGallery(path, name))
            }
            "saveImage" -> {
                val image = call.argument<ByteArray>("imageBytes") ?: return
                val quality = call.argument<Int>("quality") ?: return
                val name = call.argument<String>("name")

                result.success(saveImageToGallery(BitmapFactory.decodeByteArray(image, 0, image.size), quality, name))
            }
            else -> result.notImplemented()
        }
    }

    /// Store a new file and add it to the indexes of medias of the O.S. database
    private fun saveFileToGallery(filePath: String, @Nullable name: String? = null): HashMap<String, Any?> {

        return try {
            log("[SCANNING] $filePath")
            val originalFile = File(filePath)
            val fileUri = generateUri(originalFile.extension, name)

            val outputStream = context.contentResolver?.openOutputStream(fileUri)!!
            val fileInputStream = FileInputStream(originalFile)

            val buffer = ByteArray(10240)
            var count: Int
            while (fileInputStream.read(buffer).also { count = it } > 0) {
                outputStream.write(buffer, 0, count)
            }

            outputStream.flush()
            outputStream.close()
            fileInputStream.close()

            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, fileUri))

            val result = SaveResultModel(fileUri.toString().isNotEmpty(), fileUri.toString(), null).toHashMap()
            log("[COMPLETED] $result")

            result
        } catch (e: IOException) {
            log("[ERROR] $e", true)
            SaveResultModel(false, null, e.toString()).toHashMap()
        }
    }

    private fun generateUri(extension: String = "", name: String? = null): Uri {
        log("[Generating URI]")
        var fileName = name ?: System.currentTimeMillis().toString()
        val root = File.separator + "sidestory" + File.separator
        val videoPath = root + "videos"
        val picturePath = root + "pictures"

        val mimeType = getMIMEType(extension)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, picturePath)

            if (!TextUtils.isEmpty(mimeType)) {
                values.put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (mimeType!!.startsWith("video")) {
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, videoPath)
                }
            }
            val result = context.contentResolver?.insert(uri, values)!!
            log("[URI PATH] ${result.path}")
            return result
        } else {
            var storePath = Environment.getExternalStorageDirectory().absolutePath + picturePath

            if (!TextUtils.isEmpty(mimeType)) {
                if (mimeType!!.startsWith("video")) {
                     storePath = Environment.getExternalStorageDirectory().absolutePath + videoPath
                }
            }

            val appDir = File(storePath)

            if (!appDir.exists()){
                appDir.mkdir()
            }

            if (extension.isNotEmpty()) {
                fileName += (".$extension")
            }
            val result = Uri.fromFile(File(appDir, fileName))
            log("[URI PATH] ${result.path}")
            return result
        }
    }

    private fun getMIMEType(extension: String): String? {
        var type: String? = null
        if (!TextUtils.isEmpty(extension)) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        }
        return type
    }

    private fun log(@NonNull message: String, isError: Boolean = false){
        if(isError){
            Log.e(tag, message)
        }else{
            Log.i(tag, message)
        }
    }

    private fun saveImageToGallery(bmp: Bitmap, quality: Int, name: String?): HashMap<String, Any?> {
        val context = context
        val fileUri = generateUri("jpg", name = name)
        return try {
            val fos = context.contentResolver?.openOutputStream(fileUri)!!
            log("Image $quality")
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            fos.flush()
            fos.close()
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, fileUri))
            bmp.recycle()

            SaveResultModel(fileUri.toString().isNotEmpty(), fileUri.toString(), null).toHashMap()
        } catch (e: IOException) {
            SaveResultModel(false, null, e.toString()).toHashMap()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}

class SaveResultModel(
    private var isSuccess: Boolean,
    private var filePath: String? = null,
    private var errorMessage: String? = null) {
    fun toHashMap(): HashMap<String, Any?> {
        val hashMap = HashMap<String, Any?>()
        hashMap["isSuccess"] = isSuccess
        hashMap["filePath"] = filePath
        hashMap["errorMessage"] = errorMessage
        return hashMap
    }
}