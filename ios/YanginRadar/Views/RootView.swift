import SwiftUI

struct RootView: View {

    @Environment(AppStore.self) private var store
    @State private var secilenSekme = Sekme.harita

    enum Sekme: Hashable {
        case harita, liste, ihbar, ayarlar
    }

    var body: some View {
        TabView(selection: $secilenSekme) {
            MapScreen()
                .tabItem { Label("Harita", systemImage: "map.fill") }
                .tag(Sekme.harita)

            ListScreen()
                .tabItem { Label("İhbarlar", systemImage: "list.bullet") }
                .tag(Sekme.liste)

            NewReportFlow(bittiginde: { secilenSekme = .harita })
                .tabItem { Label("İhbar Et", systemImage: "plus.circle.fill") }
                .tag(Sekme.ihbar)

            SettingsScreen()
                .tabItem { Label("Ayarlar", systemImage: "gearshape.fill") }
                .tag(Sekme.ayarlar)
        }
        .tint(.orange)
    }
}
