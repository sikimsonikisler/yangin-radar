import CryptoKit
import DeviceCheck
import Foundation
import Observation

/// Apple App Attest ile cihaz kimligi.
///
/// Ne yapar: cihazin guvenli bolmesinde bir anahtar uretir, Apple'a imzalatir ve
/// sunucuya kaydettirir. Sonra her fotograf yuklemesinde o anahtarla bir imza atar.
/// Sunucu imzayi Apple'in kok sertifikasina kadar dogrular.
///
/// Ne kanitlar: istegin gercekten **bu uygulamadan** ve **gercek bir cihazdan**
/// geldigini. Sahte istemci yazan biri bu imzayi uretemez.
///
/// Nerede calismaz: **Simulatorde**. `DCAppAttestService.isSupported` false doner.
/// O durumda imza gonderilmez; sunucu fotografi "cihaz dogrulanmadi" olarak isaretler.
/// Uygulama calismaya devam eder, kanit degeri duser.
@Observable
final class AttestationService {

    /// Cihazin App Attest anahtar taniticisi. Gizli degil - asil anahtar guvenli bolmede.
    private(set) var keyId: String? {
        didSet { UserDefaults.standard.set(keyId, forKey: Self.anahtarKey) }
    }

    private(set) var kayitliMi = false
    private(set) var sonHata: String?

    private static let anahtarKey = "appAttestKeyId"
    private let servis = DCAppAttestService.shared

    init() {
        keyId = UserDefaults.standard.string(forKey: Self.anahtarKey)
        kayitliMi = keyId != nil
    }

    var destekleniyor: Bool {
        servis.isSupported
    }

    /// Anahtari uretir, Apple'a imzalatir ve sunucuya kaydeder. Cihaz basina bir kez.
    ///
    /// Zaten kayitliysa ya da cihaz desteklemiyorsa sessizce doner.
    func hazirla(api: APIClient) async {
        guard destekleniyor else {
            sonHata = "Bu cihaz App Attest desteklemiyor (simülatörde beklenen durum)."
            return
        }
        guard keyId == nil else {
            kayitliMi = true
            return
        }

        do {
            let kod = try await api.attestKodu()
            guard kod.enabled else {
                sonHata = "Sunucuda cihaz doğrulaması kapalı."
                return
            }

            let yeniKeyId = try await servis.generateKey()
            let nonceVerisi = Data(kod.nonce.utf8)
            let clientDataHash = Data(SHA256.hash(data: nonceVerisi))

            let attestation = try await servis.attestKey(yeniKeyId, clientDataHash: clientDataHash)

            try await api.cihazKaydet(challengeId: kod.challengeId,
                                      keyId: yeniKeyId,
                                      attestationObject: attestation.base64EncodedString())

            keyId = yeniKeyId
            kayitliMi = true
            sonHata = nil
        } catch {
            sonHata = error.localizedDescription
        }
    }

    /// Fotograf yuklemesi icin imza uretir.
    ///
    /// Imza; sunucunun verdigi nonce, ihbar numarasi ve **fotografin ozeti** uzerinden
    /// atilir. Boylece imzali bir yuklemede kare degistirilemez.
    ///
    /// - Returns: imza (base64) ve anahtar tanitici; imza uretilemiyorsa nil.
    func imzala(nonce: String, ihbarId: Int, fotograf: Data) async -> (keyId: String, assertion: String)? {
        guard destekleniyor, let keyId, kayitliMi else { return nil }

        let veri = Self.imzalanacakVeri(nonce: nonce, ihbarId: ihbarId, fotograf: fotograf)
        let clientDataHash = Data(SHA256.hash(data: veri))

        do {
            let assertion = try await servis.generateAssertion(keyId, clientDataHash: clientDataHash)
            return (keyId, assertion.base64EncodedString())
        } catch {
            sonHata = error.localizedDescription
            return nil
        }
    }

    /// `nonce|ihbarNo|fotografOzeti` — sunucudaki `PhotoIntakeService` ile birebir ayni.
    static func imzalanacakVeri(nonce: String, ihbarId: Int, fotograf: Data) -> Data {
        let ozet = SHA256.hash(data: fotograf)
            .map { String(format: "%02x", $0) }
            .joined()
        return Data("\(nonce)|\(ihbarId)|\(ozet)".utf8)
    }
}
