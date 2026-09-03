import SwiftUI

@main
struct YanginRadarApp: App {

    @State private var store = AppStore()
    @State private var konum = LocationProvider()
    @State private var attest = AttestationService()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
                .environment(konum)
                .environment(attest)
                .task {
                    store.canliyiBaslat()
                    await store.tazele()

                    // Cihaz kimligini bir kez kaydet. Desteklenmiyorsa (simulator)
                    // sessizce gecer; fotograflar "cihaz dogrulanmadi" sayilir.
                    if let sunucu = store.sunucu {
                        await attest.hazirla(api: APIClient(sunucu: sunucu))
                    }
                }
        }
    }
}
