import Flutter
import UIKit
import UniformTypeIdentifiers

/// Answers the Flutter home screen's "uedc_ss_villa/viewer" calls by opening the native
/// SceneKit / ARKit viewers in ModelViewers.swift.
final class ViewerChannel: NSObject, FlutterPlugin, UIDocumentPickerDelegate {
  private var pendingPick: FlutterResult?

  static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "uedc_ss_villa/viewer", binaryMessenger: registrar.messenger())
    registrar.addMethodCallDelegate(ViewerChannel(), channel: channel)
  }

  func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "open3d": present(Viewer3DController(), result)
    case "openAr": present(ARViewerController(), result)
    case "openVr": present(VRViewerController(), result)
    case "currentModel": result(ModelSource.describe())
    case "useBundledModel":
      ModelSource.useBundled()
      result(ModelSource.describe())
    case "pickModel": pickModel(result)
    default: result(FlutterMethodNotImplemented)
    }
  }

  private func present(_ controller: UIViewController, _ result: FlutterResult) {
    guard let top = Self.topController() else {
      result(FlutterError(code: "no_window", message: "No window to show the viewer in", details: nil))
      return
    }
    controller.modalPresentationStyle = .fullScreen
    top.present(controller, animated: true)
    result(nil)
  }

  private func pickModel(_ result: @escaping FlutterResult) {
    guard let top = Self.topController() else { return result(nil) }
    pendingPick?(nil)
    pendingPick = result
    let types = ["usdz", "usd", "usdc", "scn", "dae", "obj"].compactMap { UTType(filenameExtension: $0) }
    let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: true)
    picker.delegate = self
    top.present(picker, animated: true)
  }

  func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
    guard let url = urls.first else { return documentPickerWasCancelled(controller) }
    do {
      try ModelSource.importModel(from: url)
      pendingPick?(ModelSource.describe())
    } catch {
      pendingPick?(FlutterError(code: "import_failed", message: error.localizedDescription, details: nil))
    }
    pendingPick = nil
  }

  func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
    pendingPick?(nil)
    pendingPick = nil
  }

  static func topController() -> UIViewController? {
    let scene = UIApplication.shared.connectedScenes
      .compactMap { $0 as? UIWindowScene }
      .first { $0.activationState == .foregroundActive } ??
      UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
    var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController ?? scene?.windows.first?.rootViewController
    while let presented = top?.presentedViewController { top = presented }
    return top
  }
}
