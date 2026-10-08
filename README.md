# BIM AR VR Viewer (Android)

A Kotlin / Jetpack Compose Android app with three ways to look at a building model:

| Mode | What it does |
|------|--------------|
| **3D View** | Model-viewer style orbit camera: drag to orbit, pinch to zoom, two fingers to pan. The camera frames the whole model automatically. |
| **AR View** | Markerless AR on ARCore with **no floor or plane detection**. ARCore only tracks the phone's own movement; press **Place model in front of me** and the model is anchored upright at a fixed spot ahead of you, facing you (1.5 m away in Tabletop 1 m mode, 10 m away in Real size 1:1 mode). It stays put in the room while you walk around it. Use **Lower / Raise** to correct the height, pinch, twist or drag to adjust, and **Clear** to remove. |
| **VR View** | Side-by-side stereo for a Cardboard-style phone headset. Two cameras 64 mm apart, head-tracked with the phone's rotation sensor. The model stands 7 m in front of you, scaled to 6 m. Tap the screen to recenter. Plain stereo split, no lens distortion correction. |

The home screen also has **Open .glb from phone**, so you can load any GLB without rebuilding.

Built on [SceneView 4.52](https://github.com/sceneview/sceneview) (Filament renderer + ARCore).

## About the model (swap-in point)

The Autodesk share link (https://autode.sk/4ehmacU) could not be downloaded from the build environment, so
`app/src/main/assets/models/model.glb` is a **placeholder building** (slab, walls with door and windows,
porch columns, roof; 9 x 3.6 x 8.2 m, in metres, glTF-validator clean).

To use your real model, export it to **glTF binary (.glb)** and either:

1. **Bundle it**: overwrite `app/src/main/assets/models/model.glb` (keep the name, or change
   `ModelSource.BUNDLED_MODEL` in `ModelSource.kt`), then rebuild; or
2. **Load it at runtime**: copy the .glb to the phone and use **Open .glb from phone** on the home screen.

### Getting a .glb out of Autodesk

- **Revit**: install a glTF exporter add-in (several free ones exist on the Autodesk App Store) and
  export straight to .glb; or export FBX, then open it in Blender and use *File > Export > glTF 2.0 (.glb)*.
- **Fusion**: *File > Export* to FBX or OBJ, then convert to .glb in Blender.
- **Autodesk Viewer / Docs share link**: if downloading is allowed on the share, download the source file
  and convert it as above. Otherwise ask the owner for an export.
- **AutoCAD / Navisworks**: export FBX, then convert in Blender.

Tips: use metres with Y up (Blender's glTF exporter does this by default), keep the file under roughly
50 MB, and use *Real size 1:1* in AR to see the building at full scale outdoors.

## Build

Requirements: a current Android Studio that supports Android Gradle Plugin 9.4 (JDK 21 bundled),
Android SDK platform 37, and an Android phone with Android 9 (API 28) or newer.
AR mode needs a phone on the [ARCore supported devices list](https://developers.google.com/ar/devices);
3D and VR modes work on any phone with OpenGL ES 3.

1. Unzip and open the `ArVrViewer` folder in Android Studio (*File > Open*).
2. Let Gradle sync (it downloads Gradle 9.8, AGP 9.4.1, Kotlin 2.4.20 and SceneView).
3. Plug in the phone with USB debugging on and press **Run**.

Command line:

```bash
./gradlew assembleDebug                        # APK: app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

On GitHub, `.github/workflows/build.yml` builds the APK on every push to `main` and publishes it on the
repository's **Releases** page as `BIM-AR-VR-Viewer.apk`. Open that page on the phone, download the APK and
allow installing from your browser when Android asks.

## Project layout

```
app/src/main/
  AndroidManifest.xml          camera permission, ARCore "optional", VR activity locked to landscape
  assets/models/model.glb      the model (swap-in point)
  java/com/builtinbim/arvrviewer/
    MainActivity.kt            home screen: pick a mode, load a .glb from the phone
    ModelSource.kt             which model is shown (bundled asset, imported file or https URL)
    ViewerActivity.kt          3D orbit viewer
    ArActivity.kt              markerless AR, no plane detection: camera-relative placement
    VrActivity.kt              stereo VR split screen
    HeadTracker.kt             rotation sensor -> head pose for the VR cameras
```
