// AVFoundation tipleri henuz Sendable isaretlemesi tasimiyor; oturumu arka plan
// kuyruguna gecirirken cikan uyarilar bu yuzden bastiriliyor.
@preconcurrency import AVFoundation
import CoreLocation
import Observation
import SwiftUI
import UIKit

/// Uygulama ici kamera.
///
/// Neden kitaplik yerine kamera: kitapliktan secilen bir kare her yerden gelmis
/// olabilir - internetten indirilmis, aylar once cekilmis, baskasindan gelmis.
/// Uygulama ici cekimde kareyi biz uretiriz ve o anin konumunu kendimiz olceriz.
///
/// Simulatorde kamera donanimi yoktur; {@code kullanilabilir} false doner.
@Observable
final class CameraService: NSObject {

    enum Durum {
        case hazirlaniyor
        case hazir
        case izinYok
        case kameraYok(String)
    }

    var durum: Durum = .hazirlaniyor
    var cekiliyor = false

    let oturum = AVCaptureSession()

    private let cikti = AVCapturePhotoOutput()
    private let kuyruk = DispatchQueue(label: "yanginradar.kamera")
    private var tamamlandi: ((Data?) -> Void)?

    /// Kamera donanimi ve izni var mi?
    var kullanilabilir: Bool {
        if case .hazir = durum { return true }
        return false
    }

    func hazirla() async {
        let izin = AVCaptureDevice.authorizationStatus(for: .video)
        switch izin {
        case .notDetermined:
            guard await AVCaptureDevice.requestAccess(for: .video) else {
                durum = .izinYok
                return
            }
        case .denied, .restricted:
            durum = .izinYok
            return
        default:
            break
        }

        guard let cihaz = AVCaptureDevice.default(.builtInWideAngleCamera,
                                                  for: .video, position: .back),
              let girdi = try? AVCaptureDeviceInput(device: cihaz) else {
            // Simulatorde buraya dusulur: kamera donanimi yok.
            durum = .kameraYok("Bu cihazda kamera bulunamadı.")
            return
        }

        oturum.beginConfiguration()
        oturum.sessionPreset = .photo
        if oturum.canAddInput(girdi) { oturum.addInput(girdi) }
        if oturum.canAddOutput(cikti) { oturum.addOutput(cikti) }
        oturum.commitConfiguration()

        kuyruk.async { [oturum] in
            if !oturum.isRunning { oturum.startRunning() }
        }
        durum = .hazir
    }

    func durdur() {
        kuyruk.async { [oturum] in
            if oturum.isRunning { oturum.stopRunning() }
        }
    }

    /// Kareyi ceker ve JPEG verisini doner.
    func cek() async -> Data? {
        guard kullanilabilir else { return nil }
        cekiliyor = true
        defer { cekiliyor = false }

        return await withCheckedContinuation { devam in
            tamamlandi = { veri in devam.resume(returning: veri) }

            let ayar = AVCapturePhotoSettings(format: [
                AVVideoCodecKey: AVVideoCodecType.jpeg
            ])
            ayar.flashMode = .auto
            cikti.capturePhoto(with: ayar, delegate: self)
        }
    }
}

extension CameraService: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput,
                     didFinishProcessingPhoto photo: AVCapturePhoto,
                     error: Error?) {
        let veri = error == nil ? photo.fileDataRepresentation() : nil
        let bitir = tamamlandi
        tamamlandi = nil
        bitir?(veri)
    }
}

/// Kamera onizlemesini SwiftUI'ye tasiyan katman.
struct CameraPreview: UIViewRepresentable {
    let oturum: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        let v = PreviewView()
        v.videoPreviewLayer.session = oturum
        v.videoPreviewLayer.videoGravity = .resizeAspectFill
        return v
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }

        var videoPreviewLayer: AVCaptureVideoPreviewLayer {
            layer as! AVCaptureVideoPreviewLayer
        }
    }
}
