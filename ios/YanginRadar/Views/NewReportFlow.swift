import CoreLocation
import MapKit
import PhotosUI
import SwiftUI

/// Uc adimli ihbar akisi: yer sec -> buyukluk sec -> anlat ve gonder.
struct NewReportFlow: View {

    var bittiginde: () -> Void = {}

    @Environment(AppStore.self) private var store
    @Environment(LocationProvider.self) private var konum
    @Environment(AttestationService.self) private var attest

    @State private var adim = 0
    @State private var merkez = MapScreen.turkiye.center
    @State private var kamera: MapCameraPosition = .region(MapScreen.turkiye)
    @State private var severity: Severity?
    @State private var aciklama = ""
    @State private var il: String?
    @State private var ilce: String?

    @State private var secilenFoto: PhotosPickerItem?
    @State private var fotoVerisi: Data?
    @State private var fotoTuru = "image/jpeg"

    /// Uygulama ici kamerayla cekilmisse cekim anindaki olcumler burada durur.
    /// Kitapliktan secimde nil kalir - o zaman kanit zinciri calismaz.
    @State private var cekim: CameraCaptureView.Sonuc?
    @State private var kameraAcik = false

    @State private var konumAraniyor = false
    @State private var gonderiliyor = false
    @State private var sonuc: FireReport?
    @State private var hata: String?

    var body: some View {
        NavigationStack {
            Group {
                if let sonuc {
                    basariGorunumu(sonuc)
                } else {
                    VStack(spacing: 0) {
                        adimGostergesi
                        Divider()
                        adimIcerigi
                        altBar
                    }
                }
            }
            .navigationTitle(sonuc == nil ? "İhbar Et" : "Gönderildi")
            .navigationBarTitleDisplayMode(.inline)
            .alert("Gönderilemedi", isPresented: .init(
                get: { hata != nil },
                set: { if !$0 { hata = nil } }
            )) {
                Button("Tamam", role: .cancel) {}
            } message: {
                Text(hata ?? "")
            }
        }
    }

    // MARK: - Adim gostergesi

    private var adimGostergesi: some View {
        HStack(spacing: 8) {
            ForEach(0..<3, id: \.self) { sira in
                Capsule()
                    .fill(sira <= adim ? Color.orange : Color.secondary.opacity(0.25))
                    .frame(height: 4)
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 10)
    }

    @ViewBuilder
    private var adimIcerigi: some View {
        switch adim {
        case 0: konumAdimi
        case 1: buyuklukAdimi
        default: anlatAdimi
        }
    }

    // MARK: - 1. adim: yer

    private var konumAdimi: some View {
        ZStack {
            Map(position: $kamera)
                .onMapCameraChange(frequency: .onEnd) { durum in
                    merkez = durum.region.center
                }

            // Haritanin tam ortasindaki nisangah: kullanici haritayi kaydirarak yer secer.
            Image(systemName: "flame.circle.fill")
                .font(.system(size: 44))
                .foregroundStyle(.red)
                .background(Circle().fill(.white).padding(6))
                .shadow(radius: 4)
                .allowsHitTesting(false)

            VStack {
                Text("Haritayı kaydırarak yangının yerini ortadaki işarete getir")
                    .font(.footnote)
                    .multilineTextAlignment(.center)
                    .padding(10)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
                    .padding(.horizontal)

                Spacer()

                HStack {
                    Button {
                        Task {
                            konumAraniyor = true
                            defer { konumAraniyor = false }

                            guard let benim = await konum.tazeKonum() else { return }
                            merkez = benim
                            withAnimation {
                                kamera = .region(MKCoordinateRegion(
                                    center: benim,
                                    span: MKCoordinateSpan(latitudeDelta: 0.02,
                                                           longitudeDelta: 0.02)))
                            }
                        }
                    } label: {
                        if konumAraniyor {
                            Label {
                                Text("Konum alınıyor…")
                            } icon: {
                                ProgressView().tint(.white)
                            }
                        } else {
                            Label("Konumumu kullan", systemImage: "location.fill")
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.blue)
                    .disabled(konumAraniyor)

                    Spacer()
                }
                .padding()

                Text(String(format: "%.5f, %.5f", merkez.latitude, merkez.longitude))
                    .font(.caption.monospaced())
                    .padding(6)
                    .background(.regularMaterial, in: Capsule())
                    .padding(.bottom, 8)
            }
        }
    }

    // MARK: - 2. adim: buyukluk

    private var buyuklukAdimi: some View {
        ScrollView {
            VStack(spacing: 12) {
                Text("Ne görüyorsun?")
                    .font(.title3.bold())
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top)

                ForEach(Severity.allCases) { secenek in
                    Button {
                        severity = secenek
                    } label: {
                        HStack(spacing: 14) {
                            Image(systemName: secenek.simge)
                                .font(.title2)
                                .foregroundStyle(secenek.renk)
                                .frame(width: 36)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(secenek.label).font(.headline)
                                Text(secenek.aciklama)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .multilineTextAlignment(.leading)
                            }

                            Spacer()

                            if severity == secenek {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(secenek.renk)
                            }
                        }
                        .padding()
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(severity == secenek
                                      ? secenek.renk.opacity(0.12)
                                      : Color(.secondarySystemBackground))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(severity == secenek ? secenek.renk : .clear, lineWidth: 2)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal)
        }
    }

    // MARK: - 3. adim: anlat

    private var anlatAdimi: some View {
        Form {
            Section("Kısa açıklama") {
                TextField("Ne görüyorsun? Yol tarifi, rüzgâr yönü…",
                          text: $aciklama, axis: .vertical)
                    .lineLimit(3...6)
            }

            Section {
                Button {
                    kameraAcik = true
                } label: {
                    Label(cekim == nil ? "Fotoğraf çek" : "Yeniden çek",
                          systemImage: "camera.fill")
                }

                PhotosPicker(selection: $secilenFoto, matching: .images) {
                    Label("Kitaplıktan seç", systemImage: "photo.on.rectangle")
                        .foregroundStyle(.secondary)
                }

                if let fotoVerisi, let gorsel = UIImage(data: fotoVerisi) {
                    Image(uiImage: gorsel)
                        .resizable()
                        .scaledToFit()
                        .frame(maxHeight: 200)
                        .clipShape(RoundedRectangle(cornerRadius: 10))

                    kanitEtiketi

                    Button("Fotoğrafı kaldır", role: .destructive) {
                        self.fotoVerisi = nil
                        self.secilenFoto = nil
                        self.cekim = nil
                    }
                }
            } header: {
                Text("Fotoğraf (isteğe bağlı)")
            } footer: {
                if fotoVerisi == nil {
                    Text("Fotoğrafsız da gönderebilirsin; ihbarın haritada görünür. Ama tek başına doğrulanamaz ve yetkiliye iletilemez — bunun için ya bir fotoğraf ya da yakında başka bir ihbar gerekir.")
                } else if cekim == nil {
                    Text("Kitaplıktan seçilen kare her yerden gelmiş olabilir; yalnızca üstverisiyle değerlendirilir. En güçlü kanıt, buradan çekilen fotoğraftır.")
                } else {
                    Text("Kare bu uygulamayla çekildi; konumu ve çekim anı fotoğrafla birlikte imzalanarak gönderilecek.")
                }
            }

            Section {
                LabeledContent("Yer") {
                    Text([il, ilce].compactMap { $0 }.joined(separator: " · "))
                        .foregroundStyle(.secondary)
                }
                LabeledContent("Koordinat") {
                    Text(String(format: "%.5f, %.5f", merkez.latitude, merkez.longitude))
                        .font(.footnote.monospaced())
                        .foregroundStyle(.secondary)
                }
                if let severity {
                    LabeledContent("Büyüklük") {
                        Text(severity.label).foregroundStyle(severity.renk)
                    }
                }
                LabeledContent("Kanıt") {
                    SimgeliDeger(metin: kanitMetni, simge: kanitSimgesi, renk: kanitRengi)
                }
            } header: {
                Text("Özet")
            } footer: {
                Text("Bu bir gözlem bildirimidir, resmî ihbar hattı değildir. Can güvenliği söz konusuysa önce 112'yi arayın.")
            }
        }
        .onChange(of: secilenFoto) { _, yeni in
            Task { await fotografiYukle(yeni) }
        }
        .sheet(isPresented: $kameraAcik) {
            CameraCaptureView { sonuc in
                cekim = sonuc
                fotoVerisi = sonuc.veri
                fotoTuru = "image/jpeg"
                secilenFoto = nil
            }
        }
        .task(id: "\(merkez.latitude),\(merkez.longitude)") {
            let ad = await konum.yerAdi(for: merkez)
            il = ad.il
            ilce = ad.ilce
        }
    }

    // MARK: - Kanit gostergeleri

    private var kanitMetni: String {
        if fotoVerisi == nil { return "Fotoğraf yok" }
        return cekim == nil ? "Kitaplıktan seçildi" : "Uygulamayla çekildi"
    }

    private var kanitSimgesi: String {
        if fotoVerisi == nil { return "photo.badge.exclamationmark" }
        return cekim == nil ? "photo.on.rectangle" : "checkmark.seal.fill"
    }

    private var kanitRengi: Color {
        if fotoVerisi == nil { return .orange }
        return cekim == nil ? .secondary : .green
    }

    /// Fotografin altinda, karenin nereden geldigini ve neyin imzalanacagini soyler.
    @ViewBuilder
    private var kanitEtiketi: some View {
        if let cekim {
            VStack(alignment: .leading, spacing: 4) {
                Label("Uygulamayla çekildi", systemImage: "checkmark.seal.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.green)

                if cekim.konum != nil {
                    Label {
                        Text("Çekim konumu ölçüldü"
                             + (cekim.dogrulukMetre.map { String(format: " (±%.0f m)", $0) } ?? ""))
                    } icon: {
                        Image(systemName: "location.fill")
                    }
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                } else {
                    Label("Çekim anında konum alınamadı", systemImage: "location.slash")
                        .font(.caption2)
                        .foregroundStyle(.orange)
                }

                if attest.destekleniyor && attest.kayitliMi {
                    Label("Cihaz kimliği imzalanacak", systemImage: "lock.shield.fill")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                } else {
                    Label("Cihaz kimliği imzalanamıyor (bu cihazda desteklenmiyor)",
                          systemImage: "lock.slash")
                        .font(.caption2)
                        .foregroundStyle(.orange)
                }
            }
        } else {
            Label("Kitaplıktan seçildi — yalnızca üstverisiyle değerlendirilecek",
                  systemImage: "photo.on.rectangle")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - Alt bar

    private var altBar: some View {
        HStack {
            if adim > 0 {
                Button("Geri") { withAnimation { adim -= 1 } }
                    .buttonStyle(.bordered)
            }

            Spacer()

            if adim < 2 {
                Button("Devam") { withAnimation { adim += 1 } }
                    .buttonStyle(.borderedProminent)
                    .tint(.orange)
                    .disabled(adim == 1 && severity == nil)
            } else {
                Button {
                    Task { await gonder() }
                } label: {
                    if gonderiliyor {
                        ProgressView().tint(.white)
                    } else {
                        Label("İhbarı gönder", systemImage: "paperplane.fill")
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
                .disabled(gonderiliyor || severity == nil)
            }
        }
        .padding()
        .background(.bar)
    }

    // MARK: - Basari

    private func basariGorunumu(_ ihbar: FireReport) -> some View {
        VStack(spacing: 18) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(.green)

            Text("İhbarın kaydedildi")
                .font(.title2.bold())

            Text("\(ihbar.yerAdi) — \(ihbar.severity.label)")
                .foregroundStyle(.secondary)

            GuvenCubugu(puan: ihbar.confidence)
                .padding(.horizontal, 40)

            Text(ihbar.photoCount == 0
                 ? "Fotoğraf eklemediğin için bu ihbar tek başına doğrulanmıyor. Yakındaki başka bir ihbar ya da uydu kaydı desteklerse kendiliğinden yükselir."
                 : "Yakındaki başka ihbarlar bu kaydı desteklerse güven puanı kendiliğinden yükselir.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Button("Haritada gör") {
                sifirla()
                bittiginde()
            }
            .buttonStyle(.borderedProminent)
            .tint(.orange)

            Button("Yeni ihbar") { sifirla() }
                .buttonStyle(.bordered)
        }
        .padding()
    }

    // MARK: - Islemler

    private func fotografiYukle(_ secim: PhotosPickerItem?) async {
        guard let secim else { return }
        // Kitapliktan secim, kamerayla cekilmis kareyi ve onun kanit baglamini gecersiz kilar.
        cekim = nil
        fotoTuru = secim.supportedContentTypes
            .compactMap(\.preferredMIMEType)
            .first { $0.hasPrefix("image/") } ?? "image/jpeg"
        fotoVerisi = try? await secim.loadTransferable(type: Data.self)
    }

    private func gonder() async {
        guard let severity else { return }

        gonderiliyor = true
        defer { gonderiliyor = false }

        let istek = CreateReportRequest(
            lat: merkez.latitude,
            lon: merkez.longitude,
            il: il,
            ilce: ilce,
            aciklama: aciklama.isEmpty ? nil : aciklama,
            severity: severity,
            reporterAlias: store.takmaAd.isEmpty ? nil : store.takmaAd,
            reporterContact: nil)

        do {
            var kayit = try await store.ihbarOlustur(istek)

            if let fotoVerisi {
                let uzanti = fotoTuru == "image/png" ? "png" : "jpg"
                let dosyaAdi = "ihbar-\(kayit.id).\(uzanti)"

                if let cekim {
                    // Uygulama ici cekim: tek kullanimlik kod + konum + imza birlikte gider.
                    try await store.kanitliFotografYukle(
                        ihbarId: kayit.id,
                        veri: fotoVerisi,
                        dosyaAdi: dosyaAdi,
                        tur: fotoTuru,
                        konum: cekim.konum,
                        dogrulukMetre: cekim.dogrulukMetre,
                        cekimAni: cekim.cekimAni,
                        kamerayla: true,
                        attest: attest)
                } else {
                    // Kitapliktan secim: kanit zinciri yok, yalnizca EXIF degerlendirilir.
                    try await store.fotografYukle(ihbarId: kayit.id,
                                                  veri: fotoVerisi,
                                                  dosyaAdi: dosyaAdi,
                                                  tur: fotoTuru)
                }
                kayit = store.ihbar(id: kayit.id) ?? kayit
            }

            sonuc = kayit
        } catch {
            hata = error.localizedDescription
        }
    }

    private func sifirla() {
        adim = 0
        severity = nil
        aciklama = ""
        fotoVerisi = nil
        secilenFoto = nil
        cekim = nil
        sonuc = nil
    }
}
