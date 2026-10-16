package com.builtinbim.uedc_ss_villa

import android.content.Intent
import android.net.Uri
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * Hosts the Flutter home screen. The 3D, AR and VR modes are native SceneView screens,
 * opened from Flutter over the "uedc_ss_villa/viewer" channel.
 */
class MainActivity : FlutterActivity() {
    private var pendingPick: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "open3d" -> launch(ViewerActivity::class.java, result)
                "openAr" -> launch(ArActivity::class.java, result)
                "openVr" -> launch(VrActivity::class.java, result)
                "currentModel" -> result.success(describe(ModelSource.current(this)))
                "useBundledModel" -> {
                    ModelSource.save(this, null)
                    result.success(describe(ModelSource.current(this)))
                }
                "pickModel" -> {
                    pendingPick?.success(null)
                    pendingPick = result
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*")
                        .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("model/gltf-binary", "application/octet-stream"))
                    startActivityForResult(intent, PICK_MODEL)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun launch(screen: Class<out android.app.Activity>, result: MethodChannel.Result) {
        ModelSource.launch(this, screen, ModelSource.current(this))
        result.success(null)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_MODEL) return
        val result = pendingPick ?: return
        pendingPick = null
        val uri: Uri = data?.data ?: return result.success(null)
        runCatching { ModelSource.importFromDevice(this, uri) }
            .onSuccess {
                ModelSource.save(this, it)
                result.success(describe(it))
            }
            .onFailure { result.error("import_failed", it.message, null) }
    }

    private fun describe(location: String) =
        if (location == ModelSource.BUNDLED_MODEL) "UEDC_SS Villa (bundled)" else "Imported model"

    private companion object {
        const val CHANNEL = "uedc_ss_villa/viewer"
        const val PICK_MODEL = 41
    }
}
