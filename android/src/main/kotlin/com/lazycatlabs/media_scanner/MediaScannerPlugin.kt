package com.lazycatlabs.media_scanner

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import android.content.ContentValues
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import android.text.TextUtils
import android.webkit.MimeTypeMap
import org.jetbrains.annotations.Nullable

/** MediaScannerPlugin */
class MediaScannerPlugin : FlutterPlugin, MethodCallHandler {
    /// Used for logging
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

    override fun onMethodCall(
        @NonNull call: MethodCall,
        @NonNull result: Result
    ) {
        if (call.method == "refreshGallery") {
            val path: String? = call.argument("path")
            refreshMedia(path, result)
        } else {
            result.notImplemented()
        }
    }

    /// function to refresh media on Android Device
    private fun refreshMedia(filePath: String?, result: Result) {
        try {
            if (filePath == null)
                throw NullPointerException()
            val file = File(filePath)

            log("Scanning $filePath")

            // if (android.os.Build.VERSION.SDK_INT < 29) {
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))

//            MediaScannerConnection.scanFile(
//                context,
//                arrayOf(filePath),
//                null
//                ) {
//                    path, uri ->
//                    log("[Completed] path: $path\nuri: $uri")
//                    if(uri!=null) {
//                        result.success("$uri")
//                    }
//                    else {
//                        result.error("$uri", null, null)
//                    }
//            }
            saveFileToGallery(filePath);

        } catch (e: Exception) {
            log("[ERROR] $e", true)
            result.error("-1", e.toString(), null)
        }

    }

    private fun saveFileToGallery(filePath: String, @Nullable name: String? = null): HashMap<String, Any?> {
        val context = context
        return try {
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
            SaveResultModel(fileUri.toString().isNotEmpty(), fileUri.toString(), null).toHashMap()
        } catch (e: IOException) {
            SaveResultModel(false, null, e.toString()).toHashMap()
        }
    }

    private fun generateUri(extension: String = "", name: String? = null): Uri {
        var fileName = name ?: System.currentTimeMillis().toString()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            val mimeType = getMIMEType(extension)
            if (!TextUtils.isEmpty(mimeType)) {
                values.put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (mimeType!!.startsWith("video")) {
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                }
            }
            return context.contentResolver?.insert(uri, values)!!
        } else {
            val storePath = Environment.getExternalStorageDirectory().absolutePath + File.separator + "sidestory" + File.separator + "videos"
            log(storePath)
            val appDir = File(storePath)
            if (!appDir.exists()) {
                appDir.mkdir()
            }
            if (extension.isNotEmpty()) {
                fileName += (".$extension")
            }
            return Uri.fromFile(File(appDir, fileName))
        }
    }

    private fun getMIMEType(extension: String): String? {
        var type: String? = null
        if (!TextUtils.isEmpty(extension)) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        }
        return type
    }


    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun log(@NonNull message: String, isError: Boolean = false){
        if(isError){
            Log.e(tag, message)
        }else{
            Log.i(tag, message)
        }

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