import SwiftUI

/// Sunucudaki `Severity` enum'unun karsiligi.
enum Severity: String, Codable, CaseIterable, Identifiable {
    case dumanGoruldu = "DUMAN_GORULDU"
    case kucukYangin = "KUCUK_YANGIN"
    case buyukYangin = "BUYUK_YANGIN"
    case hizlaYayiliyor = "HIZLA_YAYILIYOR"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .dumanGoruldu: "Duman görüldü"
        case .kucukYangin: "Küçük yangın"
        case .buyukYangin: "Büyük yangın"
        case .hizlaYayiliyor: "Hızla yayılıyor"
        }
    }

    var aciklama: String {
        switch self {
        case .dumanGoruldu: "Uzakta duman var, alev göremiyorum"
        case .kucukYangin: "Alev var ama sınırlı bir alanda"
        case .buyukYangin: "Geniş bir alan yanıyor"
        case .hizlaYayiliyor: "Rüzgârla hızla büyüyor"
        }
    }

    var renk: Color {
        switch self {
        case .dumanGoruldu: .yellow
        case .kucukYangin: .orange
        case .buyukYangin: .red
        case .hizlaYayiliyor: .purple
        }
    }

    var simge: String {
        switch self {
        case .dumanGoruldu: "smoke.fill"
        case .kucukYangin: "flame"
        case .buyukYangin: "flame.fill"
        case .hizlaYayiliyor: "wind"
        }
    }
}

/// Sunucudaki `ReportStatus` karsiligi.
enum ReportStatus: String, Codable {
    case bekliyor = "BEKLIYOR"
    case inceleniyor = "INCELENIYOR"
    case dogrulandi = "DOGRULANDI"
    case reddedildi = "REDDEDILDI"
    case sonduruldu = "SONDURULDU"

    var label: String {
        switch self {
        case .bekliyor: "Bekliyor"
        case .inceleniyor: "İnceleniyor"
        case .dogrulandi: "Doğrulandı"
        case .reddedildi: "Reddedildi"
        case .sonduruldu: "Söndürüldü"
        }
    }

    var renk: Color {
        switch self {
        case .bekliyor: .gray
        case .inceleniyor: .blue
        case .dogrulandi: .green
        case .reddedildi: .secondary
        case .sonduruldu: .teal
        }
    }

    /// Reddedilen ve sondurulen kayitlar haritada solgun gosterilir.
    var aktif: Bool { self == .bekliyor || self == .inceleniyor || self == .dogrulandi }
}

/// Sunucudaki `ReportSource` karsiligi.
enum ReportSource: String, Codable {
    case vatandas = "VATANDAS"
    case uydu = "UYDU"
    case resmi = "RESMI"

    var label: String {
        switch self {
        case .vatandas: "Vatandaş ihbarı"
        case .uydu: "Uydu tespiti"
        case .resmi: "Resmî kaynak"
        }
    }

    var simge: String {
        switch self {
        case .vatandas: "person.wave.2.fill"
        case .uydu: "antenna.radiowaves.left.and.right"
        case .resmi: "building.columns.fill"
        }
    }
}

/// Sunucudaki `PhotoVerdict` karsiligi.
enum PhotoVerdict: String, Codable {
    case gecerli = "GECERLI"
    case supheli = "SUPHELI"
    case gecersiz = "GECERSIZ"

    var label: String {
        switch self {
        case .gecerli: "Kanıt olarak geçerli"
        case .supheli: "Şüpheli — inceleme gerekli"
        case .gecersiz: "Geçersiz"
        }
    }

    var renk: Color {
        switch self {
        case .gecerli: .green
        case .supheli: .orange
        case .gecersiz: .red
        }
    }

    var simge: String {
        switch self {
        case .gecerli: "checkmark.seal.fill"
        case .supheli: "questionmark.circle.fill"
        case .gecersiz: "xmark.seal.fill"
        }
    }
}
