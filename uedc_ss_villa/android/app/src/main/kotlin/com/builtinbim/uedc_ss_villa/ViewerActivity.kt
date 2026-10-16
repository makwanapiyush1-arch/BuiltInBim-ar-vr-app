package com.builtinbim.uedc_ss_villa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/** 3D mode: a model-viewer style orbit camera (one finger orbit, two fingers pan / pinch zoom). */
class ViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val location = ModelSource.fromIntent(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val engine = rememberEngine()
                val modelLoader = rememberModelLoader(engine)
                val modelInstance = rememberModelInstance(modelLoader, location)

                Box(Modifier.fillMaxSize()) {
                    SceneView(
                        modifier = Modifier.fillMaxSize(),
                        engine = engine,
                        modelLoader = modelLoader,
                        // Frame the whole model whatever its real-world size.
                        autoFitContent = true
                    ) {
                        modelInstance?.let { ModelNode(modelInstance = it) }
                    }
                    if (modelInstance == null) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    Text(
                        "Drag to orbit, pinch to zoom, two fingers to pan",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp)
                    )
                }
            }
        }
    }
}
