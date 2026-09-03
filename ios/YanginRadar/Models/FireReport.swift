import CoreLocation
import Foundation

/// Sunucudaki `FireReportDto` karsiligi. Kisisel iletisim bilgisi tasimaz.
struct FireReport: Codable, Identifiable, Hashable {
    let id: Int
    let lat: Double
    let lon: Double
    let il: String?
    let ilce: String?
    let aciklama: String?
    let severity: Severity
    let source: ReportSource
    let status: ReportStatus
    let confidence: Int
    let reporterAlias: String?
    let confirmCount: Int
    let denyCount: Int
    let brightness: Double?
    let dispatched: Bool
    let photoCount: Int
    let photos: [PhotoEvidence]
    let createdAt: Date
    let updatedAt: Date

    var koordinat: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: lat, longitude: lon)
    }

    /// "Muğla · Marmaris" — ikisi de bossa koordinat gosterilir.
    var yerAdi: String {
        let parcalar = [il, ilce].compactMap { $0?.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
        if parcalar.isEmpty {
            return String(format: "%.4f, %.4f", lat, lon)
        }
        return parcalar.joined(separator: " · ")
    }

    var gecenSure: String {
        let bicim = RelativeDateTimeFormatter()
        bicim.locale = Locale(identifier: "tr_TR")
        bicim.unitsStyle = .full
        return bicim.localizedString(for: createdAt, relativeTo: .now)
    }
}

/// Sunucudaki `PhotoDto` karsiligi.
struct PhotoEvidence: Codable, Identifiable, Hashable {
    let id: Int
    /// Sunucuya gore goreli yol: "/api/photos/12/content"
    let url: String
    let verdict: PhotoVerdict
    let score: Int
    let distanceMeters: Double?
    let takenAt: Date?
    let hasGps: Bool
    /// Uygulamanin cekim aninda olctugu konumun ihbar noktasina uzakligi.
    let captureDistanceMeters: Double?
    /// Kare uygulama ici kamerayla mi cekildi?
    let cameraCaptured: Bool
    /// App Attest imzasi dogrulandi mi?
    let deviceAttested: Bool
    let checks: [String]
    let uploadedAt: Date

    func tamURL(sunucu: URL) -> URL? {
        URL(string: url, relativeTo: sunucu)
    }

    /// Cekim anindaki olculen konumun ihbar noktasina uzakligi.
    var cekimMesafeMetni: String? {
        guard let captureDistanceMeters else { return nil }
        if captureDistanceMeters < 1000 {
            return "Çekim konumu ihbar noktasına \(Int(captureDistanceMeters.rounded())) m"
        }
        return String(format: "Çekim konumu ihbar noktasına %.1f km", captureDistanceMeters / 1000)
    }

    var mesafeMetni: String? {
        guard let distanceMeters else { return nil }
        if distanceMeters < 1000 {
            return "İhbar noktasına \(Int(distanceMeters.rounded())) m"
        }
        return String(format: "İhbar noktasına %.1f km", distanceMeters / 1000)
    }
}

/// Ust bardaki ozet sayaclar — sunucudaki `StatsDto`.
struct Stats: Codable, Hashable {
    let toplam: Int
    let bekleyen: Int
    let dogrulanan: Int
    let reddedilen: Int
    let sonduruldu: Int
    let uyduKaydi: Int
    let vatandasIhbari: Int
    let yetkiliyeIletilen: Int
    let aktifDinleyici: Int

    static let bos = Stats(toplam: 0, bekleyen: 0, dogrulanan: 0, reddedilen: 0,
                           sonduruldu: 0, uyduKaydi: 0, vatandasIhbari: 0,
                           yetkiliyeIletilen: 0, aktifDinleyici: 0)
}

/// Yeni ihbar govdesi — sunucudaki `CreateReportRequest`.
struct CreateReportRequest: Codable {
    let lat: Double
    let lon: Double
    let il: String?
    let ilce: String?
    let aciklama: String?
    let severity: Severity
    let reporterAlias: String?
    let reporterContact: String?
}
