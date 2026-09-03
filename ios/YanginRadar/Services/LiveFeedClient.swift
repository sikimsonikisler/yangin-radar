import Foundation

/// Canli akis olayi. `hosgeldin` disindaki tum olaylar `data` icinde bir ihbar tasir.
struct LiveEvent {
    let tur: String
    let ihbar: FireReport?

    var aciklama: String? {
        guard let ihbar else { return nil }
        return switch tur {
        case "yeni-ihbar": "Yeni ihbar — \(ihbar.yerAdi)"
        case "uydu-noktasi": "Uydu tespiti — \(ihbar.yerAdi)"
        case "fotograf-eklendi": "Fotoğraf eklendi — \(ihbar.yerAdi)"
        case "oy-verildi": "Yeni oy — \(ihbar.yerAdi)"
        case "durum-degisti": "\(ihbar.yerAdi) — \(ihbar.status.label)"
        case "yetkiliye-iletildi": "Yetkiliye iletildi — \(ihbar.yerAdi)"
        default: nil
        }
    }
}

/// `/api/stream` ucundaki Server-Sent Events akisini okur.
///
/// Baglanti koptugunda artan gecikmeyle yeniden dener; `akis()` iptal edilene
/// kadar olay uretmeye devam eder.
struct LiveFeedClient {

    let sunucu: URL

    /// Akis oturumu ayri: istek zaman asimi normal isteklerden cok daha uzun.
    private static let oturum: URLSession = {
        let ayar = URLSessionConfiguration.default
        ayar.timeoutIntervalForRequest = 24 * 60 * 60
        ayar.timeoutIntervalForResource = 24 * 60 * 60
        return URLSession(configuration: ayar)
    }()

    func akis() -> AsyncStream<LiveEvent> {
        AsyncStream { devam in
            let gorev = Task {
                var beklemeSaniye: UInt64 = 1

                while !Task.isCancelled {
                    do {
                        try await dinle { olay in devam.yield(olay) }
                        beklemeSaniye = 1
                    } catch is CancellationError {
                        break
                    } catch {
                        // Sunucu kapaliysa sessizce bekleyip yeniden dene.
                        try? await Task.sleep(for: .seconds(beklemeSaniye))
                        beklemeSaniye = min(beklemeSaniye * 2, 30)
                    }
                }
                devam.finish()
            }

            devam.onTermination = { _ in gorev.cancel() }
        }
    }

    private func dinle(_ olayGeldi: (LiveEvent) -> Void) async throws {
        guard let adres = URL(string: "/api/stream", relativeTo: sunucu) else {
            throw APIHata.gecersizAdres
        }

        var istek = URLRequest(url: adres)
        istek.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        // SSE baglantisi acik kalir; zaman asimi pratikte devre disi birakilir.
        // (URLSession sonsuz degeri kabul etmez, bu yuzden bir gunluk pay veriliyor.)
        let birGun: TimeInterval = 24 * 60 * 60
        istek.timeoutInterval = birGun

        let (satirlar, yanit) = try await Self.oturum.bytes(for: istek)

        guard let http = yanit as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw APIHata.baglanti("Canlı akış açılamadı")
        }

        var sonTur = "message"

        for try await satir in satirlar.lines {
            try Task.checkCancellation()

            if satir.hasPrefix("event:") {
                sonTur = satir.dropFirst(6).trimmingCharacters(in: .whitespaces)
            } else if satir.hasPrefix("data:") {
                let govde = satir.dropFirst(5).trimmingCharacters(in: .whitespaces)
                olayGeldi(LiveEvent(tur: sonTur, ihbar: ihbarCikar(govde)))
            } else if satir.isEmpty {
                sonTur = "message"
            }
            // ":" ile baslayan satirlar sunucunun ping yorumu — atlanir.
        }
    }

    /// Govde `{"type":..,"at":..,"data":{...}}` seklinde; `data` bir ihbar degilse nil.
    ///
    /// Not: burada `JSONSerialization` kullanilmamali. "hosgeldin" olayinda `data`
    /// bir metindir ve `dataWithJSONObject:` boyle bir govdede Objective-C istisnasi
    /// firlatir; Swift'in `try?` ifadesi bunu yakalayamaz, uygulama coker.
    private func ihbarCikar(_ govde: String) -> FireReport? {
        guard let veri = govde.data(using: .utf8) else { return nil }
        return (try? JSONDecoder.yanginRadar.decode(OlayGovdesi.self, from: veri))?.data
    }
}

/// SSE govdesinin cozumlenebilir hali. `data` bir ihbar degilse sessizce nil kalir.
private struct OlayGovdesi: Decodable {
    let data: FireReport?

    enum CodingKeys: String, CodingKey { case data }

    init(from decoder: Decoder) throws {
        let kap = try decoder.container(keyedBy: CodingKeys.self)
        data = try? kap.decode(FireReport.self, forKey: .data)
    }
}
