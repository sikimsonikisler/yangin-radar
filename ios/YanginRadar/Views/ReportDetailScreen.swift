import MapKit
import SwiftUI

struct ReportDetailScreen: View {

    let ihbarId: Int

    @Environment(AppStore.self) private var store
    @State private var oyGonderiliyor = false

    private var ihbar: FireReport? { store.ihbar(id: ihbarId) }

    var body: some View {
        Group {
            if let ihbar {
                icerik(ihbar)
            } else {
                ProgressView().task { await store.tazele() }
            }
        }
        .navigationTitle(ihbar?.yerAdi ?? "İhbar")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func icerik(_ ihbar: FireReport) -> some View {
        List {
            Section {
                Map(initialPosition: .region(MKCoordinateRegion(
                    center: ihbar.koordinat,
                    span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)))) {
                    Annotation(ihbar.yerAdi, coordinate: ihbar.koordinat) {
                        YanginIsareti(ihbar: ihbar)
                    }
                    .annotationTitles(.hidden)
                }
                .frame(height: 180)
                .listRowInsets(EdgeInsets())
                .allowsHitTesting(false)
            }

            Section {
                LabeledContent("Büyüklük") {
                    SimgeliDeger(metin: ihbar.severity.label,
                                 simge: ihbar.severity.simge,
                                 renk: ihbar.severity.renk)
                }
                LabeledContent("Durum") { DurumRozeti(durum: ihbar.status) }
                LabeledContent("Kaynak") {
                    SimgeliDeger(metin: ihbar.source.label, simge: ihbar.source.simge)
                }
                LabeledContent("Bildirilme") {
                    Text(ihbar.createdAt, format: .dateTime.day().month().hour().minute())
                }
                LabeledContent("Koordinat") {
                    Text(String(format: "%.5f, %.5f", ihbar.lat, ihbar.lon))
                        .font(.footnote.monospaced())
                }
                if let parlaklik = ihbar.brightness {
                    LabeledContent("Uydu parlaklığı") {
                        Text(String(format: "%.1f K", parlaklik))
                    }
                }
                if let bildiren = ihbar.reporterAlias, !bildiren.isEmpty {
                    LabeledContent("Bildiren", value: bildiren)
                }
            }

            if let aciklama = ihbar.aciklama, !aciklama.isEmpty {
                Section("Açıklama") {
                    Text(aciklama)
                }
            }

            Section {
                GuvenCubugu(puan: ihbar.confidence)
                    .padding(.vertical, 4)

                if ihbar.photos.isEmpty && ihbar.source == .vatandas {
                    Label("Fotoğraf kanıtı yok — bu ihbar tek başına doğrulanamaz, yetkiliye de iletilemez.",
                          systemImage: "photo.badge.exclamationmark")
                        .font(.footnote)
                        .foregroundStyle(.orange)
                }

                if ihbar.dispatched {
                    Label("Yetkiliye iletildi", systemImage: "paperplane.fill")
                        .foregroundStyle(.blue)
                        .font(.subheadline)
                }
            } footer: {
                Text("Güven puanı; fotoğraf üstverisi, yakın ihbarların birbirini desteklemesi ve topluluk oylarından hesaplanır.")
            }

            oylamaBolumu(ihbar)

            if !ihbar.photos.isEmpty {
                fotografBolumu(ihbar)
            }
        }
    }

    private func oylamaBolumu(_ ihbar: FireReport) -> some View {
        Section("Sen de oradaysan") {
            HStack(spacing: 12) {
                oyDugmesi(baslik: "Doğruluyorum",
                          simge: "hand.thumbsup.fill",
                          sayi: ihbar.confirmCount,
                          renk: .green) {
                    await store.oyVer(ihbarId: ihbar.id, dogruluyorum: true)
                }

                oyDugmesi(baslik: "Göremiyorum",
                          simge: "hand.thumbsdown.fill",
                          sayi: ihbar.denyCount,
                          renk: .secondary) {
                    await store.oyVer(ihbarId: ihbar.id, dogruluyorum: false)
                }
            }
            .disabled(oyGonderiliyor)
        }
    }

    private func oyDugmesi(baslik: String, simge: String, sayi: Int,
                           renk: Color, eylem: @escaping () async -> Void) -> some View {
        Button {
            Task {
                oyGonderiliyor = true
                await eylem()
                oyGonderiliyor = false
            }
        } label: {
            VStack(spacing: 4) {
                Image(systemName: simge)
                Text(baslik).font(.caption)
                Text("\(sayi)").font(.caption2.monospacedDigit()).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
        .buttonStyle(.bordered)
        .tint(renk)
    }

    private func fotografBolumu(_ ihbar: FireReport) -> some View {
        Section("Fotoğraf kanıtı") {
            ForEach(ihbar.photos) { foto in
                VStack(alignment: .leading, spacing: 10) {
                    if let adres = store.sunucu.flatMap({ foto.tamURL(sunucu: $0) }) {
                        AsyncImage(url: adres) { asama in
                            switch asama {
                            case let .success(gorsel):
                                gorsel.resizable().scaledToFit()
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                            case .failure:
                                Label("Fotoğraf yüklenemedi", systemImage: "photo.badge.exclamationmark")
                                    .foregroundStyle(.secondary)
                            default:
                                ProgressView().frame(maxWidth: .infinity, minHeight: 120)
                            }
                        }
                    }

                    HStack {
                        Label(foto.verdict.label, systemImage: foto.verdict.simge)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(foto.verdict.renk)
                        Spacer()
                        Text("\(foto.score)/100")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }

                    // Kanit zinciri rozetleri: karenin nereden geldigi ve dogrulanip
                    // dogrulanmadigi, EXIF'ten once soylenir - asil bilgi budur.
                    if foto.cameraCaptured {
                        Label(foto.deviceAttested
                              ? "Uygulamayla çekildi · cihaz kimliği doğrulandı"
                              : "Uygulamayla çekildi · cihaz kimliği doğrulanmadı",
                              systemImage: foto.deviceAttested ? "lock.shield.fill" : "lock.open")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(foto.deviceAttested ? .green : .orange)
                    }

                    if let cekimMesafe = foto.cekimMesafeMetni {
                        Label(cekimMesafe, systemImage: "scope")
                            .font(.caption).foregroundStyle(.secondary)
                    }

                    if let mesafe = foto.mesafeMetni {
                        Label(mesafe, systemImage: "location")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    if let cekim = foto.takenAt {
                        Label {
                            Text("Çekim: ") + Text(cekim, format: .dateTime.day().month().hour().minute())
                        } icon: {
                            Image(systemName: "camera")
                        }
                        .font(.caption).foregroundStyle(.secondary)
                    }

                    if !foto.checks.isEmpty {
                        DisclosureGroup("Doğrulama ayrıntısı") {
                            VStack(alignment: .leading, spacing: 4) {
                                ForEach(foto.checks, id: \.self) { satir in
                                    Text(satir).font(.caption)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .font(.caption)
                    }
                }
                .padding(.vertical, 6)
            }
        }
    }
}
