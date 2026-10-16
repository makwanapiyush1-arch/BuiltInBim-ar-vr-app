package com.builtinbim.uedc_ss_villa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * Where the app gets its model from.
 *
 * SWAP-IN POINT: replace `assets/models/model.glb` in the Flutter project (see README.md).
 * Flutter packs its assets into the APK under `flutter_assets/`, which is where SceneView reads it.
 * A direct https:// link to a .glb file also works as a location.
 */
object ModelSource {
    const val BUNDLED_MODEL = "flutter_assets/assets/models/model.glb"
    const val EXTRA_MODEL = "model_location"

    private const val PREFS = "model_source"
    private const val KEY_LOCATION = "location"

    /** Last model the user chose, or the bundled one. */
    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LOCATION, null) ?: BUNDLED_MODEL

    fun save(context: Context, location: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOCATION, location).apply()
    }

    /** Copies a .glb picked from the phone into app storage and returns its file:// location. */
    fun importFromDevice(context: Context, uri: Uri): String {
        val target = File(context.filesDir, "models/imported.glb").apply { parentFile?.mkdirs() }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open $uri" }
            target.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(target).toString()
    }

    fun fromIntent(activity: Activity): String =
        activity.intent.getStringExtra(EXTRA_MODEL) ?: current(activity)

    fun launch(context: Context, screen: Class<out Activity>, location: String) {
        context.startActivity(Intent(context, screen).putExtra(EXTRA_MODEL, location))
    }
}
