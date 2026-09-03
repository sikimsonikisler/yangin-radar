import CoreLocation
import Observation

/// Cihaz konumu ve "burasi neresi" cevirisi.
@Observable
final class LocationProvider: NSObject, CLLocationManagerDelegate {

    var konum: CLLocationCoordinate2D?
    /// Son olcumun yaticap hata payi (metre). Kanit degerlendirmesine girer.
    var dogrulukMetre: Double?
    var izinDurumu: CLAuthorizationStatus = .notDetermined
    var hata: String?

    private let yonetici = CLLocationManager()
    private let cozucu = CLGeocoder()

    /// `tazeKonum()` cagrisinin bekledigi devam noktasi. Ayni anda yalnizca bir tane olur.
    private var bekleyen: CheckedContinuation<CLLocationCoordinate2D?, Never>?

    override init() {
        super.init()
        yonetici.delegate = self
        yonetici.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        izinDurumu = yonetici.authorizationStatus
    }

    var izinVerildi: Bool {
        izinDurumu == .authorizedWhenInUse || izinDurumu == .authorizedAlways
    }

    func izinIste() {
        if izinDurumu == .notDetermined {
            yonetici.requestWhenInUseAuthorization()
        } else if izinVerildi {
            yonetici.requestLocation()
        }
    }

    func konumTazele() {
        guard izinVerildi else { return }
        yonetici.requestLocation()
    }

    /// Yeni bir konum olcumu ister ve **gelene kadar bekler**.
    ///
    /// `requestLocation()` cagirip hemen ardindan `konum` okumak bayat deger verir:
    /// olcum temsilciye sonradan ulasir. Bu yuzden cagiran taraf burada bekler.
    /// Izin henuz sorulmamissa izin istenir ve nil donulur; kullanici izni
    /// verdikten sonra yeniden cagirmasi beklenir.
    func tazeKonum() async -> CLLocationCoordinate2D? {
        guard izinVerildi else {
            izinIste()
            return nil
        }

        // Onceki bekleyen varsa serbest birak; devam noktasi bir kez sonlanmali.
        bekleyeniBitir(nil)

        return await withCheckedContinuation { devam in
            bekleyen = devam
            yonetici.requestLocation()
        }
    }

    private func bekleyeniBitir(_ deger: CLLocationCoordinate2D?) {
        guard let devam = bekleyen else { return }
        bekleyen = nil
        devam.resume(returning: deger)
    }

    /// Koordinati il / ilce adina cevirir. Cevrilemezse (nil, nil) doner.
    func yerAdi(for koordinat: CLLocationCoordinate2D) async -> (il: String?, ilce: String?) {
        let nokta = CLLocation(latitude: koordinat.latitude, longitude: koordinat.longitude)
        guard let yer = try? await cozucu.reverseGeocodeLocation(
            nokta, preferredLocale: Locale(identifier: "tr_TR")).first else {
            return (nil, nil)
        }
        return (yer.administrativeArea, yer.subAdministrativeArea ?? yer.locality)
    }

    // MARK: - CLLocationManagerDelegate

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        izinDurumu = manager.authorizationStatus
        if izinVerildi {
            manager.requestLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let son = locations.last else { return }
        konum = son.coordinate
        dogrulukMetre = son.horizontalAccuracy >= 0 ? son.horizontalAccuracy : nil
        hata = nil
        bekleyeniBitir(son.coordinate)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Simulatorde konum secilmemisse burasi surekli tetiklenir; sessiz gecmek yerine
        // kullaniciya gosterelim ki "neden bos" sorusu ortada kalmasin.
        hata = error.localizedDescription
        bekleyeniBitir(nil)
    }
}
