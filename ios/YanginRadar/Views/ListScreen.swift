import SwiftUI

struct ListScreen: View {

    @Environment(AppStore.self) private var store

    @State private var arama = ""
    @State private var suzgec = Suzgec.acik

    enum Suzgec: String, CaseIterable, Identifiable {
        case acik = "Açık"
        case dogrulanan = "Doğrulanan"
        case uydu = "Uydu"
        case tumu = "Tümü"

        var id: String { rawValue }
    }

    private var liste: [FireReport] {
        var sonuc = store.tumIhbarlarYeniden

        switch suzgec {
        case .acik: sonuc = sonuc.filter { $0.status.aktif }
        case .dogrulanan: sonuc = sonuc.filter { $0.status == .dogrulandi }
        case .uydu: sonuc = sonuc.filter { $0.source == .uydu }
        case .tumu: break
        }

        let anahtar = arama.trimmingCharacters(in: .whitespaces).lowercased()
        guard !anahtar.isEmpty else { return sonuc }

        return sonuc.filter {
            $0.yerAdi.lowercased().contains(anahtar)
                || ($0.aciklama ?? "").lowercased().contains(anahtar)
                || $0.severity.label.lowercased().contains(anahtar)
        }
    }

    var body: some View {
        NavigationStack {
            Group {
                if liste.isEmpty {
                    bosGorunum
                } else {
                    List(liste) { ihbar in
                        NavigationLink(value: ihbar.id) {
                            IhbarSatiri(ihbar: ihbar)
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("İhbarlar")
            // Buyuk baslik kipinde arama alani ile ust seridin arasinda bos bir
            // bant kaliyor ve baslik gorunmuyordu; harita ekraniyla ayni kipe alindi.
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: Int.self) { id in
                ReportDetailScreen(ihbarId: id)
            }
            .searchable(text: $arama, prompt: "İl, ilçe ya da açıklama")
            .refreshable { await store.tazele() }
            .safeAreaInset(edge: .top) {
                Picker("Süzgeç", selection: $suzgec) {
                    ForEach(Suzgec.allCases) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.bottom, 6)
                .background(.bar)
            }
        }
    }

    @ViewBuilder
    private var bosGorunum: some View {
        if let hata = store.hata {
            BosDurum(simge: "exclamationmark.triangle",
                     baslik: "Bağlanılamadı",
                     aciklama: hata,
                     eylemBasligi: "Yeniden dene") {
                Task { await store.tazele() }
            }
        } else if store.yukleniyor {
            ProgressView("Yükleniyor…")
        } else {
            BosDurum(simge: "checkmark.circle",
                     baslik: "Kayıt yok",
                     aciklama: arama.isEmpty
                        ? "Bu süzgeçte gösterilecek ihbar bulunmuyor."
                        : "“\(arama)” için sonuç yok.")
        }
    }
}
