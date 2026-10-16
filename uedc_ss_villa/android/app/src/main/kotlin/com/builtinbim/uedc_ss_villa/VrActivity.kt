package com.builtinbim.uedc_ss_villa

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.Engine
import io.github.sceneview.FrameRatePolicy
import io.github.sceneview.SceneView
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.node.CameraNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/** Distance between the eyes, meters. */
private const val IPD = 0.064f

/** Where the viewer stands: eye height above the model's ground. */
private val HEAD = Position(0f, 1.6f, 0f)

/** The model is scaled to this size and set down this far in front of the viewer. */
private const val MODEL_SIZE = 6f
private val MODEL_POSITION = Position(0f, 0f, -7f)

/**
 * VR mode for a Cardboard-style phone headset: the screen is split into a left-eye and a
 * right-eye view rendered from two cameras [IPD] apart, both driven by the phone's rotation
 * sensor. Tap anywhere to recenter. (No lens distortion correction: plain stereo split.)
 */
class VrActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val location = ModelSource.fromIntent(this)
        setContent { VrScreen(location) }
    }
}

@Composable
private fun VrScreen(location: String) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    // ~90° vertical field of view per eye (12 mm on a 24 mm-high full-frame sensor).
    val leftEye = rememberCameraNode(engine) { focalLength = 12.0; position = HEAD + Position(-IPD / 2, 0f, 0f) }
    val rightEye = rememberCameraNode(engine) { focalLength = 12.0; position = HEAD + Position(IPD / 2, 0f, 0f) }

    val tracker = remember {
        HeadTracker(context) { orientation, right ->
            val half = right * (IPD / 2)
            leftEye.worldQuaternion = orientation
            leftEye.worldPosition = HEAD - half
            rightEye.worldQuaternion = orientation
            rightEye.worldPosition = HEAD + half
        }
    }

    // Track only while the screen is visible.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> tracker.start()
                Lifecycle.Event.ON_PAUSE -> tracker.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) tracker.start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tracker.stop()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxSize()) {
            EyeView(engine, modelLoader, location, leftEye, Modifier.weight(1f).fillMaxHeight())
            Box(Modifier.width(2.dp).fillMaxHeight().background(Color.Black))
            EyeView(engine, modelLoader, location, rightEye, Modifier.weight(1f).fillMaxHeight())
        }
        // Transparent layer above both eye views: tap anywhere to recenter.
        Box(
            Modifier
                .matchParentSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    tracker.recenter()
                }
        )
        if (!tracker.isAvailable) {
            Text(
                "No rotation sensor on this phone: head tracking is off",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
            )
        }
    }
}

@Composable
private fun EyeView(
    engine: Engine,
    modelLoader: ModelLoader,
    location: String,
    eye: CameraNode,
    modifier: Modifier
) {
    // Each eye has its own Filament scene, so each gets its own instance of the model.
    val modelInstance = rememberModelInstance(modelLoader, location)
    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        cameraNode = eye,
        // The head tracker owns the camera: no touch orbit, and render every frame.
        cameraManipulator = null,
        onGestureListener = null,
        frameRatePolicy = FrameRatePolicy.Continuous(),
        autoCenterContent = false
    ) {
        modelInstance?.let {
            ModelNode(
                modelInstance = it,
                scaleToUnits = MODEL_SIZE,
                centerOrigin = Position(0f, -1f, 0f),
                position = MODEL_POSITION
            )
        }
    }
}
