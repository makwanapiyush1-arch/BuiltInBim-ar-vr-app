import ARKit
import CoreMotion
import Flutter
import SceneKit
import UIKit

// MARK: - Model source

/// Where the iOS app gets its model from.
///
/// SWAP-IN POINT: replace `assets/models/model.usdz` in the Flutter project (see README.md).
/// SceneKit reads .usdz / .usd / .scn / .dae / .obj; "Open model from phone" imports one at runtime.
enum ModelSource {
  static let bundledAsset = "assets/models/model.usdz"
  private static let key = "imported_model_path"

  static var bundledURL: URL? {
    let path = FlutterDartProject.lookupKey(forAsset: bundledAsset)
    return Bundle.main.url(forResource: path, withExtension: nil)
  }

  static var currentURL: URL? {
    if let path = UserDefaults.standard.string(forKey: key), FileManager.default.fileExists(atPath: path) {
      return URL(fileURLWithPath: path)
    }
    return bundledURL
  }

  static func describe() -> String {
    UserDefaults.standard.string(forKey: key) == nil ? "UEDC_SS Villa (bundled)" : "Imported model"
  }

  static func useBundled() { UserDefaults.standard.removeObject(forKey: key) }

  static func importModel(from url: URL) throws {
    let dir = try FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
      .appendingPathComponent("models", isDirectory: true)
    try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    let target = dir.appendingPathComponent("imported." + url.pathExtension.lowercased())
    try? FileManager.default.removeItem(at: target)
    let scoped = url.startAccessingSecurityScopedResource()
    defer { if scoped { url.stopAccessingSecurityScopedResource() } }
    try FileManager.default.copyItem(at: url, to: target)
    UserDefaults.standard.set(target.path, forKey: key)
  }

  /// Loads the model and wraps it in a pivot node whose origin is the model's bottom centre.
  /// Units are metres, Y up.
  static func loadModel() -> SCNNode? {
    guard let url = currentURL, let scene = try? SCNScene(url: url, options: [.checkConsistency: true]) else { return nil }
    let content = SCNNode()
    for child in scene.rootNode.childNodes { content.addChildNode(child) }
    let (minB, maxB) = content.boundingBox
    content.position = SCNVector3(-(minB.x + maxB.x) / 2, -minB.y, -(minB.z + maxB.z) / 2)
    let pivot = SCNNode()
    pivot.addChildNode(content)
    return pivot
  }

  static func size(of node: SCNNode) -> SCNVector3 {
    let (minB, maxB) = node.boundingBox
    return SCNVector3(maxB.x - minB.x, maxB.y - minB.y, maxB.z - minB.z)
  }

  /// Scale that makes the model's largest dimension `units` metres.
  static func scale(_ node: SCNNode, toFit units: Float) -> Float {
    let s = size(of: node)
    let largest = max(s.x, s.y, s.z)
    return largest > 0 ? units / largest : 1
  }
}

// MARK: - Shared UI helpers

private func makeButton(_ title: String, filled: Bool = false, action: @escaping () -> Void) -> UIButton {
  var config = filled ? UIButton.Configuration.filled() : UIButton.Configuration.tinted()
  config.title = title
  config.cornerStyle = .capsule
  let button = UIButton(configuration: config, primaryAction: UIAction { _ in action() })
  return button
}

private func makeLabel(_ text: String) -> UILabel {
  let label = UILabel()
  label.text = text
  label.textColor = .white
  label.font = .preferredFont(forTextStyle: .footnote)
  label.numberOfLines = 0
  label.textAlignment = .center
  label.shadowColor = .black
  label.shadowOffset = CGSize(width: 0, height: 1)
  return label
}

private func pin(_ view: UIView, in parent: UIView) {
  view.translatesAutoresizingMaskIntoConstraints = false
  parent.addSubview(view)
  NSLayoutConstraint.activate([
    view.leadingAnchor.constraint(equalTo: parent.leadingAnchor),
    view.trailingAnchor.constraint(equalTo: parent.trailingAnchor),
    view.topAnchor.constraint(equalTo: parent.topAnchor),
    view.bottomAnchor.constraint(equalTo: parent.bottomAnchor),
  ])
}

private func addCloseButton(to controller: UIViewController) {
  let close = makeButton("Close") { [weak controller] in controller?.dismiss(animated: true) }
  close.translatesAutoresizingMaskIntoConstraints = false
  controller.view.addSubview(close)
  NSLayoutConstraint.activate([
    close.topAnchor.constraint(equalTo: controller.view.safeAreaLayoutGuide.topAnchor, constant: 8),
    close.leadingAnchor.constraint(equalTo: controller.view.safeAreaLayoutGuide.leadingAnchor, constant: 12),
  ])
}

private func showMissingModel(in controller: UIViewController) {
  let label = makeLabel("Could not load the model. Use \"Use bundled villa model\" on the home screen.")
  label.translatesAutoresizingMaskIntoConstraints = false
  controller.view.addSubview(label)
  NSLayoutConstraint.activate([
    label.centerYAnchor.constraint(equalTo: controller.view.centerYAnchor),
    label.leadingAnchor.constraint(equalTo: controller.view.leadingAnchor, constant: 24),
    label.trailingAnchor.constraint(equalTo: controller.view.trailingAnchor, constant: -24),
  ])
}

// MARK: - 3D view

/// Orbit viewer: one finger orbits, pinch zooms, two fingers pan.
final class Viewer3DController: UIViewController {
  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = .black
    let scnView = SCNView()
    pin(scnView, in: view)
    scnView.backgroundColor = UIColor(white: 0.12, alpha: 1)
    scnView.autoenablesDefaultLighting = true
    scnView.allowsCameraControl = true
    scnView.antialiasingMode = .multisampling4X
    scnView.defaultCameraController.interactionMode = .orbitTurntable
    scnView.defaultCameraController.inertiaEnabled = true

    let scene = SCNScene()
    scnView.scene = scene
    if let model = ModelSource.loadModel() {
      scene.rootNode.addChildNode(model)
      let s = ModelSource.size(of: model)
      let radius = max(s.x, s.y, s.z)
      let camera = SCNNode()
      camera.camera = SCNCamera()
      camera.camera?.zNear = Double(radius) / 100
      camera.camera?.zFar = Double(radius) * 20
      camera.position = SCNVector3(radius * 0.9, radius * 0.7, radius * 1.3)
      camera.look(at: SCNVector3(0, s.y / 2, 0))
      scene.rootNode.addChildNode(camera)
      scnView.pointOfView = camera
      scnView.defaultCameraController.target = SCNVector3(0, s.y / 2, 0)
    } else {
      showMissingModel(in: self)
    }

    let hint = makeLabel("Drag to orbit, pinch to zoom, two fingers to pan")
    hint.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(hint)
    NSLayoutConstraint.activate([
      hint.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -12),
      hint.centerXAnchor.constraint(equalTo: view.centerXAnchor),
    ])
    addCloseButton(to: self)
  }
}

// MARK: - AR view

/// Markerless AR without plane detection: ARKit only tracks the phone's own motion, and the model is
/// anchored at a fixed spot in front of the camera, upright and facing the user. It stays put in the
/// room while you walk around it.
final class ARViewerController: UIViewController, ARSCNViewDelegate, ARSessionDelegate {
  private struct Drop { let distance: Float; let below: Float }
  private static let tabletop = Drop(distance: 1.5, below: 0.5)
  private static let realSize = Drop(distance: 10, below: 1.4)

  private let arView = ARSCNView()
  private let status = makeLabel("Move the phone slowly to start tracking")
  private let scaleSwitch = UISegmentedControl(items: ["Tabletop 1 m", "Real size 1:1"])
  private var model: SCNNode?
  /// Placed anchors and whether each was placed at real size.
  private var placed: [UUID: Bool] = [:]
  private var heightOffset: Float = 0
  private var userScale: Float = 1
  private var userYaw: Float = 0

  override var supportedInterfaceOrientations: UIInterfaceOrientationMask { .portrait }

  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = .black
    pin(arView, in: view)
    arView.delegate = self
    arView.session.delegate = self
    arView.autoenablesDefaultLighting = true
    arView.automaticallyUpdatesLighting = true
    model = ModelSource.loadModel()
    if model == nil { showMissingModel(in: self) }

    arView.addGestureRecognizer(UIPinchGestureRecognizer(target: self, action: #selector(pinched(_:))))
    arView.addGestureRecognizer(UIRotationGestureRecognizer(target: self, action: #selector(twisted(_:))))

    scaleSwitch.selectedSegmentIndex = 0
    scaleSwitch.backgroundColor = UIColor(white: 0, alpha: 0.4)
    let top = UIStackView(arrangedSubviews: [scaleSwitch, status])
    top.axis = .vertical
    top.spacing = 8
    top.alignment = .center
    top.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(top)

    let lower = makeButton("Lower") { [weak self] in self?.nudge(-0.1) }
    let raise = makeButton("Raise") { [weak self] in self?.nudge(0.1) }
    let clear = makeButton("Clear") { [weak self] in self?.clear() }
    let row = UIStackView(arrangedSubviews: [lower, raise, clear])
    row.spacing = 8
    let place = makeButton("Place model in front of me", filled: true) { [weak self] in self?.placeInFront() }
    let bottom = UIStackView(arrangedSubviews: [row, place])
    bottom.axis = .vertical
    bottom.spacing = 10
    bottom.alignment = .center
    bottom.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(bottom)

    NSLayoutConstraint.activate([
      top.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 52),
      top.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
      top.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
      bottom.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16),
      bottom.centerXAnchor.constraint(equalTo: view.centerXAnchor),
    ])
    addCloseButton(to: self)
  }

  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    guard ARWorldTrackingConfiguration.isSupported else {
      status.text = "AR needs an iPhone or iPad with ARKit (A9 chip or newer)."
      return
    }
    let config = ARWorldTrackingConfiguration()
    config.planeDetection = []  // no floor or wall detection
    config.worldAlignment = .gravity
    config.environmentTexturing = .automatic
    arView.session.run(config, options: [.resetTracking, .removeExistingAnchors])
  }

  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    arView.session.pause()
  }

  func session(_ session: ARSession, cameraDidChangeTrackingState camera: ARCamera) {
    switch camera.trackingState {
    case .normal: status.text = ""
    case .limited: status.text = "Move the phone slowly to improve tracking"
    case .notAvailable: status.text = "Tracking not available"
    }
  }

  /// Anchors a pose ahead of the camera (level, ignoring tilt), turned so the model's front faces the user.
  private func placeInFront() {
    guard let frame = arView.session.currentFrame, model != nil else { return }
    let realScale = scaleSwitch.selectedSegmentIndex == 1
    let drop = realScale ? Self.realSize : Self.tabletop
    let cam = frame.camera.transform
    // The camera looks down its -Z axis; flatten that to the horizontal plane (world is Y up).
    var forward = SIMD2<Float>(-cam.columns.2.x, -cam.columns.2.z)
    if simd_length(forward) < 1e-3 { forward = SIMD2(cam.columns.1.x, cam.columns.1.z) }
    forward = simd_normalize(forward)
    let position = SIMD3<Float>(
      cam.columns.3.x + forward.x * drop.distance,
      cam.columns.3.y - drop.below,
      cam.columns.3.z + forward.y * drop.distance)
    let yaw = atan2(-forward.x, -forward.y)
    var transform = simd_float4x4(simd_quatf(angle: yaw, axis: SIMD3(0, 1, 0)))
    transform.columns.3 = SIMD4(position, 1)
    let anchor = ARAnchor(name: "villa", transform: transform)
    placed[anchor.identifier] = realScale
    arView.session.add(anchor: anchor)
  }

  func renderer(_ renderer: SCNSceneRenderer, nodeFor anchor: ARAnchor) -> SCNNode? {
    guard let realScale = placed[anchor.identifier], let model = model?.clone() else { return nil }
    let villa = VillaNode()
    villa.baseScale = realScale ? 1 : ModelSource.scale(model, toFit: 1)
    villa.addChildNode(model)
    let holder = SCNNode()
    holder.addChildNode(villa)
    DispatchQueue.main.async { self.applyAdjustments() }
    return holder
  }

  private func applyAdjustments() {
    let villas = arView.scene.rootNode.childNodes(passingTest: { node, _ in node is VillaNode })
    for case let node as VillaNode in villas {
      let s = node.baseScale * userScale
      node.scale = SCNVector3(s, s, s)
      node.position = SCNVector3(0, heightOffset, 0)
      node.eulerAngles.y = userYaw
    }
  }

  private func nudge(_ delta: Float) {
    heightOffset += delta
    applyAdjustments()
  }

  private func clear() {
    for anchor in arView.session.currentFrame?.anchors ?? [] where placed[anchor.identifier] != nil {
      arView.session.remove(anchor: anchor)
    }
    placed.removeAll()
    heightOffset = 0
    userScale = 1
    userYaw = 0
  }

  @objc private func pinched(_ gesture: UIPinchGestureRecognizer) {
    userScale = max(0.05, min(20, userScale * Float(gesture.scale)))
    gesture.scale = 1
    applyAdjustments()
  }

  @objc private func twisted(_ gesture: UIRotationGestureRecognizer) {
    userYaw -= Float(gesture.rotation)
    gesture.rotation = 0
    applyAdjustments()
  }
}

/// A placed copy of the model; remembers the scale it was placed at (1 = real size).
private final class VillaNode: SCNNode {
  var baseScale: Float = 1
}

// MARK: - VR view

/// Side-by-side stereo for a Cardboard-style headset: two cameras 64 mm apart, head-tracked with the
/// phone's motion sensors. The model stands 7 m in front of you, scaled to 6 m. Tap to recenter.
/// Plain stereo split, no lens distortion correction.
final class VRViewerController: UIViewController {
  private let motion = CMMotionManager()
  private let scene = SCNScene()
  private let world = SCNNode()   // rotated to recenter
  private let head = SCNNode()
  private let leftView = SCNView()
  private let rightView = SCNView()
  private var lastOrientation = simd_quatf(angle: 0, axis: SIMD3(0, 1, 0))

  override var supportedInterfaceOrientations: UIInterfaceOrientationMask { .landscape }
  override var prefersStatusBarHidden: Bool { true }
  override var prefersHomeIndicatorAutoHidden: Bool { true }

  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = .black
    scene.background.contents = UIColor(red: 0.62, green: 0.76, blue: 0.9, alpha: 1)
    scene.rootNode.addChildNode(world)

    let floor = SCNNode(geometry: SCNFloor())
    floor.geometry?.firstMaterial?.diffuse.contents = UIColor(red: 0.42, green: 0.5, blue: 0.38, alpha: 1)
    floor.geometry?.firstMaterial?.lightingModel = .lambert
    (floor.geometry as? SCNFloor)?.reflectivity = 0
    floor.position = SCNVector3(0, -1.6, 0)   // eye height 1.6 m
    world.addChildNode(floor)

    if let model = ModelSource.loadModel() {
      let s = ModelSource.scale(model, toFit: 6)
      model.scale = SCNVector3(s, s, s)
      model.position = SCNVector3(0, -1.6, -7)
      world.addChildNode(model)
    } else {
      showMissingModel(in: self)
    }

    let sun = SCNNode()
    sun.light = SCNLight()
    sun.light?.type = .directional
    sun.light?.intensity = 900
    sun.eulerAngles = SCNVector3(-Float.pi / 3, Float.pi / 5, 0)
    scene.rootNode.addChildNode(sun)
    let ambient = SCNNode()
    ambient.light = SCNLight()
    ambient.light?.type = .ambient
    ambient.light?.intensity = 450
    scene.rootNode.addChildNode(ambient)

    scene.rootNode.addChildNode(head)
    for (eyeView, offset) in [(leftView, Float(-0.032)), (rightView, Float(0.032))] {
      let eye = SCNNode()
      eye.camera = SCNCamera()
      eye.camera?.fieldOfView = 90
      eye.camera?.zNear = 0.05
      eye.camera?.zFar = 500
      eye.position = SCNVector3(offset, 0, 0)
      head.addChildNode(eye)
      eyeView.scene = scene
      eyeView.pointOfView = eye
      eyeView.isPlaying = true
      eyeView.antialiasingMode = .multisampling2X
      eyeView.isUserInteractionEnabled = false
      eyeView.translatesAutoresizingMaskIntoConstraints = false
      view.addSubview(eyeView)
    }
    NSLayoutConstraint.activate([
      leftView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
      leftView.topAnchor.constraint(equalTo: view.topAnchor),
      leftView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
      leftView.widthAnchor.constraint(equalTo: view.widthAnchor, multiplier: 0.5),
      rightView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
      rightView.topAnchor.constraint(equalTo: view.topAnchor),
      rightView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
      rightView.widthAnchor.constraint(equalTo: view.widthAnchor, multiplier: 0.5),
    ])
    let divider = UIView()
    divider.backgroundColor = .black
    divider.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(divider)
    NSLayoutConstraint.activate([
      divider.centerXAnchor.constraint(equalTo: view.centerXAnchor),
      divider.widthAnchor.constraint(equalToConstant: 2),
      divider.topAnchor.constraint(equalTo: view.topAnchor),
      divider.bottomAnchor.constraint(equalTo: view.bottomAnchor),
    ])
    view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(recenter)))
    addCloseButton(to: self)
  }

  override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)
    if #available(iOS 16.0, *), let scene = view.window?.windowScene {
      setNeedsUpdateOfSupportedInterfaceOrientations()
      scene.requestGeometryUpdate(.iOS(interfaceOrientations: .landscape))
    }
    UIApplication.shared.isIdleTimerDisabled = true
    guard motion.isDeviceMotionAvailable else { return }
    motion.deviceMotionUpdateInterval = 1.0 / 60
    motion.startDeviceMotionUpdates(using: .xArbitraryZVertical, to: .main) { [weak self] data, _ in
      guard let self, let q = data?.attitude.quaternion else { return }
      self.updateHead(simd_quatf(ix: Float(q.x), iy: Float(q.y), iz: Float(q.z), r: Float(q.w)))
    }
  }

  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    motion.stopDeviceMotionUpdates()
    UIApplication.shared.isIdleTimerDisabled = false
  }

  /// Device attitude (Z up) -> SceneKit camera (Y up, looking down -Z), corrected for landscape.
  private func updateHead(_ attitude: simd_quatf) {
    let zUpToYUp = simd_quatf(angle: -.pi / 2, axis: SIMD3(1, 0, 0))
    let landscapeLeft = view.window?.windowScene?.interfaceOrientation == .landscapeLeft
    let screenRoll = simd_quatf(angle: landscapeLeft ? .pi / 2 : -.pi / 2, axis: SIMD3(0, 0, 1))
    let orientation = zUpToYUp * attitude * screenRoll
    lastOrientation = orientation
    head.simdOrientation = orientation
  }

  /// Turns the world so the model is straight ahead of where you're looking now.
  @objc private func recenter() {
    let forward = lastOrientation.act(SIMD3<Float>(0, 0, -1))
    let yaw = atan2(-forward.x, -forward.z)
    world.simdOrientation = simd_quatf(angle: yaw, axis: SIMD3(0, 1, 0))
  }
}
