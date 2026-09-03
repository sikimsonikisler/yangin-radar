import CoreLocation
import Foundation
import Observation

/// Uygulamanin tek veri kaynagi: ihbar listesi, ozet sayaclar, canli akis ve sunucu adresi.
@MainActor
@Observable
final class AppStore {

    // MARK: - Yayinlanan durum

    var ihbarlar: [FireReport] = []
    var ozet: Stats = .bos
    var yukleniyor = false
    var hata: String?

    var canliBagli = false
    var sonCanliOlay: String?

    /// Ayarlar ekranindan degistirilir; her degisiklikte akis yeniden kurulur.
    var sunucuAdresi: String {
        didSet {
            guard sunucuAdresi != oldValue else { return }
            UserDefaults.standard.set(sunucuAdresi, forKey: Self.adresAnahtari)
            yenidenBaglan()
        }
    }

    /// Ihbar ederken kullanilan takma ad — cihazda kalir, sunucuya sadece bu gonderilir.
    var takmaAd: String {
        didSet { UserDefaults.standard.set(takmaAd, forKey: Self.adAnahtari) }
    }

    // MARK: - Kurulum

    private static let adresAnahtari = "sunucuAdresi"
    private static let adAnahtari = "takmaAd"
    private static let cihazAnahtari = "cihazKimligi"
    static let varsayilanAdres = "http://localhost:8080"

    private var canliGorev: Task<Void, Never>?

    /// Bu cihaza ait, kisisel bilgi tasimayan kimlik.
    ///
    /// Yalnizca oy tekillestirmede kullanilir: onsuz ayni kisi bir ihbari sinirsiz
    /// kez destekleyip guven puanini sisirebiliyordu. Sunucuya yalnizca bu rastgele
    /// dizi gider; cihazi tanimlayan hicbir bilgi tasimaz.
    let cihazKimligi: String

    init() {
        sunucuAdresi = UserDefaults.standard.string(forKey: Self.adresAnahtari)
            ?? Self.varsayilanAdres
        takmaAd = UserDefaults.standard.string(forKey: Self.adAnahtari) ?? ""

        if let kayitli = UserDefaults.standard.string(forKey: Self.cihazAnahtari) {
            cihazKimligi = kayitli
        } else {
            let yeni = UUID().uuidString
            UserDefaults.standard.set(yeni, forKey: Self.cihazAnahtari)
            cihazKimligi = yeni
        }
    }

    var sunucu: URL? {
        URL(string: sunucuAdresi.trimmingCharacters(in: .whitespaces))
    }

    private var api: APIClient? {
        sunucu.map { APIClient(sunucu: $0) }
    }

    // MARK: - Turetilen gorunumler

    /// Haritada ve listede one cikan kayitlar: kapanmamis ihbarlar, yeniden eskiye.
    var aktifIhbarlar: [FireReport] {
        ihbarlar.filter { $0.status.aktif }.sorted { $0.createdAt > $1.createdAt }
    }

    var tumIhbarlarYeniden: [FireReport] {
        ihbarlar.sorted { $0.createdAt > $1.createdAt }
    }

    func ihbar(id: Int) -> FireReport? {
        ihbarlar.first { $0.id == id }
    }

    // MARK: - Veri cekme

    func tazele() async {
        guard let api else {
            hata = APIHata.gecersizAdres.localizedDescription
            return
        }

        yukleniyor = true
        defer { yukleniyor = false }

        do {
            async let liste = api.ihbarlar()
            async let sayac = api.ozet()
            ihbarlar = try await liste
            ozet = try await sayac
            hata = nil
        } catch {
            hata = error.localizedDescription
        }
    }

    // MARK: - Islemler

    func ihbarOlustur(_ istek: CreateReportRequest) async throws -> FireReport {
        guard let api else { throw APIHata.gecersizAdres }
        let yeni = try await api.ihbarOlustur(istek)
        birlestir(yeni)
        return yeni
    }

    /// Kanit zinciri olmadan yukleme - kitapliktan secilmis fotograf.
    func fotografYukle(ihbarId: Int, veri: Data, dosyaAdi: String, tur: String) async throws {
        guard let api else { throw APIHata.gecersizAdres }
        let guncel = try await api.fotografYukle(id: ihbarId, veri: veri,
                                                 dosyaAdi: dosyaAdi, tur: tur)
        birlestir(guncel)
    }

    /// Kanit zinciriyle yukleme.
    ///
    /// Sirasiyla: sunucudan tek kullanimlik kod alinir, kod + ihbar numarasi +
    /// fotografin ozeti App Attest ile imzalanir, hepsi birlikte gonderilir.
    /// Imza uretilemiyorsa (simulator, eski cihaz) yukleme yine yapilir; sunucu
    /// fotografi "cihaz dogrulanmadi" olarak isaretler.
    func kanitliFotografYukle(ihbarId: Int,
                              veri: Data,
                              dosyaAdi: String,
                              tur: String,
                              konum: CLLocationCoordinate2D?,
                              dogrulukMetre: Double?,
                              cekimAni: Date,
                              kamerayla: Bool,
                              attest: AttestationService) async throws {
        guard let api else { throw APIHata.gecersizAdres }

        let kod = try await api.cekimKodu()
        let imza = await attest.imzala(nonce: kod.nonce, ihbarId: ihbarId, fotograf: veri)

        let beyan = CekimBeyani(
            challengeId: kod.challengeId,
            lat: konum?.latitude,
            lon: konum?.longitude,
            dogrulukMetre: dogrulukMetre,
            cekimAni: cekimAni,
            kaynak: kamerayla ? "KAMERA" : "KITAPLIK")

        let guncel = try await api.fotografYukle(id: ihbarId, veri: veri,
                                                 dosyaAdi: dosyaAdi, tur: tur,
                                                 beyan: beyan,
                                                 keyId: imza?.keyId,
                                                 assertion: imza?.assertion)
        birlestir(guncel)
    }

    func oyVer(ihbarId: Int, dogruluyorum: Bool) async {
        guard let api else { return }
        do {
            let guncel = try await api.oyVer(id: ihbarId, dogruluyorum: dogruluyorum,
                                             voterId: cihazKimligi)
            birlestir(guncel)
        } catch {
            hata = error.localizedDescription
        }
    }

    private func birlestir(_ ihbar: FireReport) {
        if let sira = ihbarlar.firstIndex(where: { $0.id == ihbar.id }) {
            ihbarlar[sira] = ihbar
        } else {
            ihbarlar.append(ihbar)
        }
    }

    // MARK: - Canli akis

    func canliyiBaslat() {
        guard canliGorev == nil, let sunucu else { return }

        let akis = LiveFeedClient(sunucu: sunucu).akis()
        canliGorev = Task { [weak self] in
            for await olay in akis {
                guard let self else { return }
                self.canliBagli = true
                if let ihbar = olay.ihbar {
                    self.birlestir(ihbar)
                }
                if let metin = olay.aciklama {
                    self.sonCanliOlay = metin
                }
                // Sayaclar sunucuda hesaplaniyor; olay geldikce tazeleyelim.
                if olay.tur != "hosgeldin", let api = self.api {
                    self.ozet = (try? await api.ozet()) ?? self.ozet
                }
            }
            self?.canliBagli = false
        }
    }

    func canliyiDurdur() {
        canliGorev?.cancel()
        canliGorev = nil
        canliBagli = false
    }

    private func yenidenBaglan() {
        canliyiDurdur()
        ihbarlar = []
        ozet = .bos
        canliyiBaslat()
        Task { await tazele() }
    }
}
