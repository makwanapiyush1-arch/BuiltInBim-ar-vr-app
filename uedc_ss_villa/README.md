# UEDC_SS Villa (Android + iOS)

A Flutter app with three ways to look at the UEDC_SS Villa model. The home screen is shared Flutter
code; each mode is a native viewer so AR gets full phone tracking on both platforms.

| Mode | Android (SceneView + ARCore) | iOS (SceneKit + ARKit) |
|------|------------------------------|------------------------|
| **3D View** | Orbit camera: drag to orbit, pinch to zoom, two fingers to pan. | Same gestures. |
| **AR View** | **No marker and no floor/plane detection.** Press **Place model in front of me** and the villa is anchored upright ahead of you, facing you (1.5 m away in *Tabletop 1 m*, 10 m away in *Real size 1:1*). It stays put while you walk around it. **Lower / Raise** fix the height, pinch to scale, twist to rotate, **Clear** removes it. | Same behaviour (ARKit world tracking with plane detection off). |
| **VR View** | Side-by-side stereo for a Cardboard-style headset, head-tracked with the rotation sensor. The villa stands 7 m ahead, scaled to 6 m. Tap to recenter. | Same, using CoreMotion. |

**Open model from phone** loads another model at runtime (.glb on Android; .usdz, .usd, .scn, .dae or .obj on iOS).

## The model (swap-in point)

The Autodesk Viewer link for the villa is a protected share of a Navisworks `.nwd`, and it could not be
downloaded from the build environment. The app therefore ships a **placeholder building** in
`assets/models/`:

| File | Used by |
|------|---------|
| `assets/models/model.glb` | Android |
| `assets/models/model.usdz` | iOS |

To put the real villa in the app, replace both files (keep the names) and push; the GitHub Actions build
picks them up. Both should be in metres with Y up.

### Getting the villa out of Navisworks

Navisworks cannot export glTF or USDZ directly, so go through FBX:

1. In Navisworks Manage/Simulate open the `.nwd` and use **Output > Export Scene > FBX** (or ask the
   owner of the Autodesk share for an FBX/NWC/RVT copy if you only have viewer access).
2. Open the FBX in Blender (*File > Import > FBX*). Check the scale (one unit = one metre) and delete
   anything you don't want on the phone.
3. *File > Export > glTF 2.0*, format **glTF Binary (.glb)** → save as `model.glb`.
4. *File > Export > Universal Scene Description*, file name `model.usdz` → save as `model.usdz`
   (or drop the .glb into Apple's free **Reality Converter** on a Mac and export .usdz).

Keep each file under roughly 50 MB for smooth phones. `tools/glb_to_usdz.py` converts a simple,
untextured .glb to .usdz (it made the placeholder); use Blender for textured models.

## Download and install

GitHub Actions (`.github/workflows/uedc-ss-villa.yml`) builds both apps on every push that touches this
folder and publishes them on the repository's **Releases** page:

- **UEDC_SS-Villa.apk**: open the release on the Android phone, download, and allow installing from the
  browser when asked. AR needs a phone on Google's ARCore supported list; 3D and VR work on any phone
  with Android 9 or newer.
- **UEDC_SS-Villa-unsigned.ipa**: iOS will not install an unsigned app. See below.

## iOS: what's needed for an installable build

Apple only installs apps signed with an Apple certificate, so CI produces an unsigned build that proves
the iOS code compiles. To get it on an iPhone, pick one:

1. **Your own iPhone, free Apple ID**: on a Mac with Xcode, open `ios/Runner.xcworkspace`, select the
   *Runner* target > *Signing & Capabilities*, choose your Apple ID team, change the bundle identifier to
   something unique, plug in the iPhone and press Run. Free signing expires after 7 days.
2. **Team distribution (recommended)**: an Apple Developer Program membership (USD 99/year). Then either
   build and upload from Xcode (*Product > Archive > Distribute App > TestFlight*), or add these GitHub
   secrets and extend the workflow to sign on CI: the distribution certificate (.p12) and its password,
   a provisioning profile, and an App Store Connect API key. Testers install through TestFlight.
3. **Sideloading the unsigned .ipa** with a tool such as Sideloadly or AltStore, which re-signs it with
   your Apple ID.

AR on iOS needs an iPhone/iPad with an A9 chip or newer (iPhone 6s onwards).

## Build locally

Requires Flutter 3.47 (Dart 3.13). Android needs the Android SDK (platform 37) and JDK 21; iOS needs a
Mac with Xcode.

```bash
flutter pub get
flutter build apk --release          # build/app/outputs/flutter-apk/app-release.apk
flutter build ios --release          # needs signing set up in Xcode
```

## Layout

```
lib/main.dart                       Flutter home screen; talks to native code on "uedc_ss_villa/viewer"
assets/models/                      model.glb (Android), model.usdz (iOS)
android/app/src/main/kotlin/...     MainActivity (channel), ViewerActivity, ArActivity, VrActivity, HeadTracker
ios/Runner/ViewerChannel.swift      channel + model file picker
ios/Runner/ModelViewers.swift       3D (SceneKit), AR (ARKit, no plane detection), VR (stereo + CoreMotion)
tools/glb_to_usdz.py                simple .glb -> .usdz converter
```
