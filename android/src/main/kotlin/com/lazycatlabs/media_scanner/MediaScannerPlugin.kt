package com.lazycatlabs.media_scanner

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.delay
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch



/** MediaScannerPlugin */
class MediaScannerPlugin : FlutterPlugin, MethodCallHandler, MediaScannerConnection.OnScanCompletedListener {
    private val tag = "MediaScannerPlugin"
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel
    private lateinit var context: Context

    private var uri : Uri?

    get() {
        return this.uri
    }

    set(newUri: Uri?) {
        uri = newUri
    }

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
            result.success(refreshMedia(path))

        } else {
            result.notImplemented()
        }

    }


    /// function to refresh media on Android Device
    private fun refreshMedia(path: String?): String {
        return try {
            if (path == null)
                throw NullPointerException()
            val file = File(path)

            Log.d(tag, "Starting the scan")
            uri = null

            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.toString()),
                null,
                this,
            )

           GlobalScope.launch(Dispatchers.IO) {
               wait()
           }

            Log.d(tag, "intermediate...")
            if(uri==null)
                ""
            else
                uri.toString()
        } catch (e: Exception) {
            Log.e(tag, "ERROR\n$e")
            e.toString()
        }

    }

    private suspend fun wait() {
        delay(40)
        Log.d(tag, "Waited 40ms")
    }

    override fun onScanCompleted(path: String?, uri: Uri?) {
        this.uri = uri
        Log.d(tag, "Completed: path: $path\nuri: $uri")
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}

