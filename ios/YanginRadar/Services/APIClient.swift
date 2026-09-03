import Foundation

/// App Attest anahtar kaydi icin sunucudan alinan meydan okuma.
struct AttestKodu: Decodable {
    let challengeId: String
    let nonce: String
    let enabled: Bool
}

/// Fotograf cekiminden hemen once alinan tek kullanimlik kod.
struct CekimKodu: Decodable {
    let challengeId: String
    let nonce: String
}

/// Fotografla birlikte gonderilen cekim beyani. Sunucudaki `CaptureEnvelope` karsiligi.
struct CekimBeyani: Encodable {
    let challengeId: String
    let lat: Double?
    let lon: Double?
    let dogrulukMetre: Double?
    let cekimAni: Date
    /// "KAMERA" ya da "KITAPLIK"
    let kaynak: String
}

/// Sunucudan donen hata govdesi: {"zaman":..., "kod":400, "hata":"..."}
private struct SunucuHatasi: Decodable {
    let kod: Int
    let hata: String
}

enum APIHata: LocalizedError {
    case gecersizAdres
    case sunucu(kod: Int, mesaj: String)
    case baglanti(String)
    case cozumleme(String)

    var errorDescription: String? {
        switch self {
        case .gecersizAdres:
            "Sunucu adresi geçersiz. Ayarlar'dan kontrol edin."
        case let .sunucu(kod, mesaj):
            "Sunucu \(kod) döndü — \(mesaj)"
        case let .baglanti(mesaj):
            "Sunucuya ulaşılamadı — \(mesaj)"
        case let .cozumleme(mesaj):
            "Sunucu yanıtı okunamadı — \(mesaj)"
        }
    }
}

/// YanginRadar REST ucuna baglanan istemci. Tum uclar `/api` altinda.
struct APIClient {

    let sunucu: URL

    /// Tek paylasilan oturum: her istekte yeni URLSession yaratmak baglantilari sizdirir.
    private static let oturum: URLSession = {
        let ayar = URLSessionConfiguration.default
        ayar.timeoutIntervalForRequest = 15
        ayar.waitsForConnectivity = false
        return URLSession(configuration: ayar)
    }()

    // MARK: - Okuma

    func ihbarlar() async throws -> [FireReport] {
        try await iste(yol: "/api/reports")
    }

    func ihbar(id: Int) async throws -> FireReport {
        try await iste(yol: "/api/reports/\(id)")
    }

    func ozet() async throws -> Stats {
        try await iste(yol: "/api/stats")
    }

    // MARK: - Yazma

    func ihbarOlustur(_ govde: CreateReportRequest) async throws -> FireReport {
        let veri = try JSONEncoder().encode(govde)
        return try await iste(yol: "/api/reports", yontem: "POST",
                              govde: veri, tur: "application/json")
    }

    /// Ihbari destekler ya da yalanlar.
    ///
    /// `voterId` cihazda uretilen, kisisel bilgi tasimayan bir kimliktir; sunucu
    /// bunu ayni cihazin ayni ihbara birden fazla oy vermesini engellemek icin ister.
    func oyVer(id: Int, dogruluyorum: Bool, voterId: String) async throws -> FireReport {
        let kimlik = voterId.addingPercentEncoding(
            withAllowedCharacters: .alphanumerics) ?? voterId
        return try await iste(
            yol: "/api/reports/\(id)/vote?confirm=\(dogruluyorum)&voterId=\(kimlik)",
            yontem: "POST")
    }

    // MARK: - Kanit zinciri

    func attestKodu() async throws -> AttestKodu {
        try await iste(yol: "/api/attest/challenge", yontem: "POST")
    }

    func cihazKaydet(challengeId: String, keyId: String, attestationObject: String) async throws {
        struct Istek: Encodable {
            let challengeId: String
            let keyId: String
            let attestationObject: String
        }
        struct Cevap: Decodable { let kayitli: Bool }

        let veri = try JSONEncoder().encode(
            Istek(challengeId: challengeId, keyId: keyId, attestationObject: attestationObject))
        let _: Cevap = try await iste(yol: "/api/attest/register", yontem: "POST",
                                      govde: veri, tur: "application/json", zamanAsimi: 30)
    }

    func cekimKodu() async throws -> CekimKodu {
        try await iste(yol: "/api/capture/challenge", yontem: "POST")
    }

    /// Fotograf yukler.
    ///
    /// `beyan` verilirse kanit zinciri devreye girer: tek kullanimlik kod harcanir ve
    /// varsa App Attest imzasi dogrulanir. Beyan yoksa fotograf yalnizca EXIF ile
    /// degerlendirilir - kitapliktan secim boyledir.
    func fotografYukle(id: Int, veri: Data, dosyaAdi: String, tur: String,
                       beyan: CekimBeyani? = nil,
                       keyId: String? = nil,
                       assertion: String? = nil) async throws -> FireReport {
        let sinir = "YanginRadar-\(UUID().uuidString)"
        var govde = Data()

        func alanEkle(_ ad: String, _ deger: String) {
            govde.ekle("--\(sinir)\r\n")
            govde.ekle("Content-Disposition: form-data; name=\"\(ad)\"\r\n\r\n")
            govde.ekle(deger)
            govde.ekle("\r\n")
        }

        govde.ekle("--\(sinir)\r\n")
        govde.ekle("Content-Disposition: form-data; name=\"file\"; filename=\"\(dosyaAdi)\"\r\n")
        govde.ekle("Content-Type: \(tur)\r\n\r\n")
        govde.append(veri)
        govde.ekle("\r\n")

        if let beyan {
            let kodlayici = JSONEncoder()
            kodlayici.dateEncodingStrategy = .iso8601
            if let json = try? kodlayici.encode(beyan),
               let metin = String(data: json, encoding: .utf8) {
                alanEkle("cekim", metin)
            }
        }
        if let keyId { alanEkle("keyId", keyId) }
        if let assertion { alanEkle("assertion", assertion) }

        govde.ekle("--\(sinir)--\r\n")

        return try await iste(yol: "/api/reports/\(id)/photos", yontem: "POST",
                              govde: govde,
                              tur: "multipart/form-data; boundary=\(sinir)",
                              zamanAsimi: 60)
    }

    // MARK: - Ortak istek

    private func iste<T: Decodable>(yol: String,
                                    yontem: String = "GET",
                                    govde: Data? = nil,
                                    tur: String? = nil,
                                    zamanAsimi: TimeInterval = 15) async throws -> T {
        guard let adres = URL(string: yol, relativeTo: sunucu) else {
            throw APIHata.gecersizAdres
        }

        var istek = URLRequest(url: adres)
        istek.httpMethod = yontem
        istek.httpBody = govde
        istek.timeoutInterval = zamanAsimi
        if let tur {
            istek.setValue(tur, forHTTPHeaderField: "Content-Type")
        }

        let veri: Data
        let yanit: URLResponse
        do {
            (veri, yanit) = try await Self.oturum.data(for: istek)
        } catch {
            throw APIHata.baglanti(error.localizedDescription)
        }

        guard let http = yanit as? HTTPURLResponse else {
            throw APIHata.baglanti("Beklenmeyen yanıt türü")
        }

        guard (200..<300).contains(http.statusCode) else {
            let mesaj = (try? JSONDecoder().decode(SunucuHatasi.self, from: veri))?.hata
                ?? String(data: veri, encoding: .utf8)
                ?? "ayrıntı yok"
            throw APIHata.sunucu(kod: http.statusCode, mesaj: mesaj)
        }

        do {
            return try JSONDecoder.yanginRadar.decode(T.self, from: veri)
        } catch {
            throw APIHata.cozumleme(error.localizedDescription)
        }
    }
}

private extension Data {
    mutating func ekle(_ metin: String) {
        if let veri = metin.data(using: .utf8) {
            append(veri)
        }
    }
}
