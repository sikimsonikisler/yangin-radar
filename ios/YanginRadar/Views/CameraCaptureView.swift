import AVFoundation
import CoreLocation
import SwiftUI

/// Uygulama ici kamera ekrani.
///
/// Cekim aninda iki sey birlikte alinir: kare ve cihazin **o andaki** konum olcumu.
/// Ikisi birlikte imzalanip gonderilir; boylece "bu fotograf su noktada, su an
/// cekildi" iddiasi EXIF'e degil imzaya dayanir.
struct CameraCaptureView: View {

    /// Cekilen kare ve o anki olcumler.
    struct Sonuc {
        let veri: Data
        let konum: CLLocationCoordinate2D?
        let dogrulukMetre: Double?
        let cekimAni: Date
    }

    var tamamlandi: (Sonuc) -> Void

    @Environment(\.dismiss) private var kapat
    @Environment(LocationProvider.self) private var konumSaglayici

    @State private var kamera = CameraService()
    @State private var hata: String?

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()

                switch kamera.durum {
                case .hazir:
                    CameraPreview(oturum: kamera.oturum)
                        .ignoresSafeArea()

                case .hazirlaniyor:
                    ProgressView("Kamera hazırlanıyor…")
                        .tint(.white)
                        .foregroundStyle(.white)

                case .izinYok:
                    engel(simge: "camera.fill",
                          baslik: "Kamera izni yok",
                          aciklama: "İhbarına fotoğraf ekleyebilmek için Ayarlar’dan kamera iznini açman gerekiyor.")

                case let .kameraYok(mesaj):
                    engel(simge: "camera.badge.ellipsis",
                          baslik: "Kamera bulunamadı",
                          aciklama: mesaj + " Simülatörde kamera donanımı yoktur; gerçek cihazda çalışır.")
                }

                if kamera.kullanilabilir {
                    VStack {
                        bilgiSeridi
                        Spacer()
                        deklansor
                    }
                }
            }
            .navigationTitle("Fotoğraf çek")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Vazgeç") { kapat() }
                }
            }
            .task {
                konumSaglayici.izinIste()
                await kamera.hazirla()
            }
            .onDisappear { kamera.durdur() }
            .alert("Fotoğraf çekilemedi", isPresented: .init(
                get: { hata != nil },
                set: { if !$0 { hata = nil } })) {
                Button("Tamam", role: .cancel) {}
            } message: {
                Text(hata ?? "")
            }
        }
    }

    private var bilgiSeridi: some View {
        Text("Yangını çerçeveye al. Konumun fotoğrafla birlikte imzalanıp gönderilecek.")
            .font(.footnote)
            .multilineTextAlignment(.center)
            .padding(10)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
            .padding(.horizontal)
            .padding(.top, 8)
    }

    private var deklansor: some View {
        Button {
            Task { await cek() }
        } label: {
            ZStack {
                Circle().fill(.white).frame(width: 72, height: 72)
                Circle().stroke(.white.opacity(0.6), lineWidth: 4).frame(width: 88, height: 88)
                if kamera.cekiliyor {
                    ProgressView().tint(.black)
                }
            }
        }
        .disabled(kamera.cekiliyor)
        .padding(.bottom, 28)
        .accessibilityLabel("Fotoğraf çek")
    }

    private func engel(simge: String, baslik: String, aciklama: String) -> some View {
        VStack(spacing: 14) {
            Image(systemName: simge)
                .font(.system(size: 44))
                .foregroundStyle(.white.opacity(0.8))
            Text(baslik)
                .font(.headline)
                .foregroundStyle(.white)
            Text(aciklama)
                .font(.footnote)
                .foregroundStyle(.white.opacity(0.75))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
    }

    private func cek() async {
        // Konumu kareyle es zamanli iste: ikisi de "su an"a ait olsun.
        async let kare = kamera.cek()
        async let nokta = konumSaglayici.tazeKonum()

        let (veri, konum) = await (kare, nokta)

        guard let veri else {
            hata = "Kare alınamadı, tekrar dener misin?"
            return
        }

        tamamlandi(Sonuc(veri: veri,
                         konum: konum,
                         dogrulukMetre: konumSaglayici.dogrulukMetre,
                         cekimAni: .now))
        kapat()
    }
}
