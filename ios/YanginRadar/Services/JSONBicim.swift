import Foundation

/// Sunucu `Instant` degerlerini ISO-8601 metni olarak yollar; kesirli saniye
/// hane sayisi degisebildigi icin (`.123` ya da `.123456789`) once 3 haneye kirpiyoruz.
enum ISO8601Tarih {

    private static let kesirli: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private static let duz: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    static func ayristir(_ metin: String) -> Date? {
        let kirpik = kesiriKirp(metin)
        return kesirli.date(from: kirpik)
            ?? duz.date(from: kirpik)
            ?? duz.date(from: metin)
    }

    /// "2026-08-28T17:24:33.123456789Z" -> "2026-08-28T17:24:33.123Z"
    private static func kesiriKirp(_ metin: String) -> String {
        guard let nokta = metin.firstIndex(of: ".") else { return metin }
        let sonrasi = metin.index(after: nokta)
        var son = sonrasi
        while son < metin.endIndex, metin[son].isNumber {
            son = metin.index(after: son)
        }
        let haneler = metin[sonrasi..<son]
        guard haneler.count > 3 else { return metin }
        let kisa = haneler.prefix(3)
        return String(metin[metin.startIndex..<sonrasi]) + String(kisa) + String(metin[son...])
    }
}

extension JSONDecoder {
    /// Sunucunun tarih bicimini anlayan cozucu.
    static let yanginRadar: JSONDecoder = {
        let cozucu = JSONDecoder()
        cozucu.dateDecodingStrategy = .custom { decoder in
            let kap = try decoder.singleValueContainer()
            let metin = try kap.decode(String.self)
            guard let tarih = ISO8601Tarih.ayristir(metin) else {
                throw DecodingError.dataCorruptedError(
                    in: kap, debugDescription: "Tarih çözülemedi: \(metin)")
            }
            return tarih
        }
        return cozucu
    }()
}
