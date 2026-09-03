import MapKit
import SwiftUI

struct MapScreen: View {

    @Environment(AppStore.self) private var store
    @Environment(LocationProvider.self) private var konum

    @State private var kamera: MapCameraPosition = .region(Self.turkiye)
    @State private var secili: FireReport?
    @State private var sadeceAktif = true

    /// Acilista gosterilen Turkiye kutusu.
    ///
    /// Boylam araligi ulkenin gercek genisliginden (26–45) biraz genis tutuldu;
    /// aksi halde Mugla ve Edirne cevresindeki isaretler ekranin kenarinda kaliyor.
    static let turkiye = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 39.1, longitude: 35.4),
        span: MKCoordinateSpan(latitudeDelta: 7, longitudeDelta: 18))

    private var gosterilecek: [FireReport] {
        sadeceAktif ? store.aktifIhbarlar : store.tumIhbarlarYeniden
    }

    var body: some View {
        NavigationStack {
            Map(position: $kamera) {
                UserAnnotation()

                ForEach(gosterilecek) { ihbar in
                    Annotation(ihbar.yerAdi, coordinate: ihbar.koordinat) {
                        Button {
                            secili = ihbar
                        } label: {
                            YanginIsareti(ihbar: ihbar, secili: secili?.id == ihbar.id)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .annotationTitles(.hidden)
            }
            .mapControls {
                MapUserLocationButton()
                MapCompass()
            }
            .safeAreaInset(edge: .top) { ustBar }
            .navigationTitle("Yangın Radar")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Toggle("Sadece açık kayıtlar", isOn: $sadeceAktif)
                        Button("Yenile", systemImage: "arrow.clockwise") {
                            Task { await store.tazele() }
                        }
                        Button("Türkiye'ye sığdır", systemImage: "scope") {
                            withAnimation { kamera = .region(Self.turkiye) }
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
            .sheet(item: $secili) { ihbar in
                NavigationStack {
                    ReportDetailScreen(ihbarId: ihbar.id)
                }
                .presentationDetents([.medium, .large])
            }
            .task { konum.izinIste() }
        }
    }

    /// Harita ustundeki ince bilgi seridi: sayaclar, son canli olay, hata.
    private var ustBar: some View {
        VStack(spacing: 6) {
            HStack(spacing: 14) {
                // Canli akis gostergesi: dar arac cubugunda yazisi kirpildigi icin burada.
                Image(systemName: store.canliBagli ? "dot.radiowaves.left.and.right" : "wifi.slash")
                    .font(.caption)
                    .foregroundStyle(store.canliBagli ? .green : .secondary)
                    .accessibilityLabel(store.canliBagli ? "Canlı akış bağlı" : "Canlı akış kapalı")

                sayac("Açık", store.ozet.bekleyen + store.ozet.dogrulanan, .orange)
                sayac("Doğrulanan", store.ozet.dogrulanan, .green)
                sayac("Uydu", store.ozet.uyduKaydi, .blue)
                sayac("Toplam", store.ozet.toplam, .secondary)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(.regularMaterial, in: Capsule())

            if let olay = store.sonCanliOlay {
                Text(olay)
                    .font(.caption)
                    .lineLimit(1)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(.regularMaterial, in: Capsule())
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .id(olay)
            }

            if let hata = store.hata {
                Text(hata)
                    .font(.caption)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(.red.opacity(0.9), in: Capsule())
                    .onTapGesture { Task { await store.tazele() } }
            }
        }
        .padding(.top, 4)
        .animation(.easeInOut, value: store.sonCanliOlay)
    }

    private func sayac(_ baslik: String, _ deger: Int, _ renk: Color) -> some View {
        VStack(spacing: 1) {
            Text("\(deger)")
                .font(.subheadline.bold().monospacedDigit())
                .foregroundStyle(renk)
            Text(baslik)
                .font(.system(size: 10))
                .foregroundStyle(.secondary)
        }
    }
}
