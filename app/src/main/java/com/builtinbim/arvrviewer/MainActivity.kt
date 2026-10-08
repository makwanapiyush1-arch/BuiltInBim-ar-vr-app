package com.builtinbim.arvrviewer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ar.core.ArCoreApk

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { HomeScreen() }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    var location by remember { mutableStateOf(ModelSource.current(context)) }

    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ModelSource.importFromDevice(context, uri) }
                .onSuccess { location = it; ModelSource.save(context, it) }
                .onFailure { Toast.makeText(context, "Could not open model: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("BIM AR VR Viewer", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (location == ModelSource.BUNDLED_MODEL) "Model: bundled (assets/models/model.glb)"
            else "Model: imported from this phone",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(6.dp))

        ModeCard("3D View", "Orbit, pan and pinch-zoom around the model.") {
            ModelSource.launch(context, ViewerActivity::class.java, location)
        }
        ModeCard("AR View", "No markers, no floor scanning: the model is placed in front of you.") {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            if (availability.isUnsupported) {
                Toast.makeText(context, "This phone does not support ARCore.", Toast.LENGTH_LONG).show()
            } else {
                ModelSource.launch(context, ArActivity::class.java, location)
            }
        }
        ModeCard("VR View", "Side-by-side stereo for a Cardboard-style headset, head tracked.") {
            ModelSource.launch(context, VrActivity::class.java, location)
        }

        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = { pickModel.launch(arrayOf("model/gltf-binary", "application/octet-stream", "*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open .glb from phone") }
        if (location != ModelSource.BUNDLED_MODEL) {
            TextButton(
                onClick = { location = ModelSource.BUNDLED_MODEL; ModelSource.save(context, location) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Use bundled model") }
        }
    }
}

@Composable
private fun ModeCard(title: String, description: String, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpen) { Text("Open $title") }
        }
    }
}
