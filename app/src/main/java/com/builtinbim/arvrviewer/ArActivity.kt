package com.builtinbim.arvrviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** How far in front of the phone, and how far below it, a model is dropped. Metres. */
private data class Drop(val distance: Float, val below: Float)

private val TABLETOP_DROP = Drop(distance = 1.5f, below = 0.5f)
private val REAL_SIZE_DROP = Drop(distance = 10f, below = 1.4f)

/**
 * AR mode. No markers and no floor (plane) detection: ARCore only tracks the phone's own motion,
 * and the model is anchored at a fixed spot in front of the camera, upright and facing the user.
 * Once placed it stays put in the room while you walk around it.
 */
class ArActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val location = ModelSource.fromIntent(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val engine = rememberEngine()
                val modelLoader = rememberModelLoader(engine)
                // Latest ARCore session and frame. Plain holder so per-frame updates don't recompose.
                val latest = remember { LatestFrame() }
                var isTracking by remember { mutableStateOf(false) }
                // true = place at the model's real size (1 unit = 1 m), false = 1 m tabletop scale.
                var realScale by remember { mutableStateOf(false) }
                // Manual height correction, since there is no floor detection to snap to.
                var heightOffset by remember { mutableFloatStateOf(0f) }
                val anchors = remember { mutableStateListOf<Pair<Anchor, Boolean>>() }

                Box(Modifier.fillMaxSize()) {
                    ARSceneView(
                        modifier = Modifier.fillMaxSize(),
                        engine = engine,
                        modelLoader = modelLoader,
                        planeFindingMode = Config.PlaneFindingMode.DISABLED,
                        instantPlacementMode = Config.InstantPlacementMode.DISABLED,
                        planeRenderer = false,
                        onSessionUpdated = { session, frame ->
                            latest.session = session
                            latest.frame = frame
                            val tracking = frame.camera.trackingState == TrackingState.TRACKING
                            if (tracking != isTracking) isTracking = tracking
                        }
                    ) {
                        anchors.forEach { placed ->
                            val anchor = placed.first
                            val placedRealScale = placed.second
                            key(anchor) {
                                AnchorNode(anchor = anchor) {
                                    rememberModelInstance(modelLoader, location)?.let { instance ->
                                        ModelNode(
                                            modelInstance = instance,
                                            scaleToUnits = if (placedRealScale) null else 1f,
                                            // Bottom-centre of the model sits on the anchor point.
                                            centerOrigin = Position(0f, -1f, 0f),
                                            position = Position(0f, heightOffset, 0f),
                                            // Pinch to scale, twist to rotate, drag to move.
                                            isEditable = true
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !realScale,
                                onClick = { realScale = false },
                                label = { Text("Tabletop 1 m") }
                            )
                            FilterChip(
                                selected = realScale,
                                onClick = { realScale = true },
                                label = { Text("Real size 1:1") }
                            )
                        }
                        if (!isTracking) {
                            Text("Move the phone slowly to start tracking", color = Color.White)
                        }
                    }

                    Column(
                        Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { heightOffset -= 0.1f }) { Text("Lower") }
                            OutlinedButton(onClick = { heightOffset += 0.1f }) { Text("Raise") }
                            OutlinedButton(onClick = {
                                anchors.forEach { runCatching { it.first.detach() } }
                                anchors.clear()
                                heightOffset = 0f
                            }) { Text("Clear") }
                        }
                        Button(
                            enabled = isTracking,
                            onClick = {
                                val drop = if (realScale) REAL_SIZE_DROP else TABLETOP_DROP
                                latest.placeInFront(drop)?.let { anchors.add(it to realScale) }
                            }
                        ) { Text("Place model in front of me") }
                    }
                }
            }
        }
    }
}

private class LatestFrame {
    var session: Session? = null
    var frame: Frame? = null

    /** Anchors a pose [Drop.distance] ahead of the camera (level, ignoring tilt), facing the camera. */
    fun placeInFront(drop: Drop): Anchor? {
        val session = session ?: return null
        val camera = frame?.camera?.takeIf { it.trackingState == TrackingState.TRACKING } ?: return null
        val cameraPose = camera.displayOrientedPose

        // Camera looks down its -Z axis; flatten that to the horizontal plane (ARCore world is Y up).
        val z = cameraPose.zAxis
        var forwardX = -z[0]
        var forwardZ = -z[2]
        val length = sqrt(forwardX * forwardX + forwardZ * forwardZ)
        if (length < 1e-3f) {
            // Pointing straight down or up: use the phone's up direction instead.
            val y = cameraPose.yAxis
            forwardX = y[0]; forwardZ = y[2]
        }
        val norm = sqrt(forwardX * forwardX + forwardZ * forwardZ).coerceAtLeast(1e-3f)
        forwardX /= norm; forwardZ /= norm

        val translation = floatArrayOf(
            cameraPose.tx() + forwardX * drop.distance,
            cameraPose.ty() - drop.below,
            cameraPose.tz() + forwardZ * drop.distance
        )
        // Turn the model about Y so its front (+Z) faces back towards the user.
        val yaw = atan2(-forwardX, -forwardZ)
        val rotation = floatArrayOf(0f, sin(yaw / 2), 0f, cos(yaw / 2))
        return runCatching { session.createAnchor(Pose(translation, rotation)) }.getOrNull()
    }
}
