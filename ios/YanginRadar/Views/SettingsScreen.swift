import SwiftUI

struct SettingsScreen: View {

    @Environment(AppStore.self) private var store
    @Environment(LocationProvider.self) private var konum
    @Environment(AttestationService.self) private var attest

    @State private var denemeSonucu: String?
    @State private var deneniyor = false

    var body: some View {
        @Bindable var store = store

        NavigationStack {
            Form {
                Section {
                    TextField("http://localhost:8080", text: $store.sunucuAdresi)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .font(.callout.monospaced())

                    Button {
                        Task { await baglantiyiDene() }
                    } label: {
                        HStack {
                            Text("Bağlantıyı sına")
                            Spacer()
                            if deneniyor { ProgressView() }
                        }
                    }
                    .disabled(deneniyor)

                    if let denemeSonucu {
                        Text(denemeSonucu)
                            .font(.caption)
                            .foregroundStyle(denemeSonucu.hasPrefix("✅") ? .green : .red)
                    }

                    if store.sunucuAdresi != AppStore.varsayilanAdres {
                        Button("Varsayılana dön") {
                            store.sunucuAdresi = AppStore.varsayilanAdres
                        }
                    }
                } header: {
                    Text("Sunucu")
                } footer: {
                    Text("Simülatörde `http://localhost:8080` kullanın. Gerçek telefonda Mac'in **.local adını** yazın — örneğin `http://mac-adiniz.local:8080`. Düz IP adresi (192.168.x.x) uygulama tarafından engellenir.")
                }

                Section {
                    TextField("Takma ad (isteğe bağlı)", text: $store.takmaAd)
                } header: {
                    Text("Kimlik")
                } footer: {
                    Text("Takma ad cihazda kalır ve ihbarlarda gösterilir. Telefon numarası ya da e-posta gönderilmez.")
                }

                Section {
                    LabeledContent("Canlı akış") {
                        SimgeliDeger(metin: store.canliBagli ? "Bağlı" : "Kapalı",
                                     simge: store.canliBagli ? "checkmark.circle.fill" : "xmark.circle",
                                     renk: store.canliBagli ? .green : .secondary)
                    }
                    LabeledContent("Konum izni") {
                        Text(konumDurumu).foregroundStyle(.secondary)
                    }
                    if !konum.izinVerildi {
                        Button("Konum izni iste") { konum.izinIste() }
                    }

                    LabeledContent("Cihaz kimliği") {
                        SimgeliDeger(metin: cihazDurumu.metin,
                                     simge: cihazDurumu.simge,
                                     renk: cihazDurumu.renk)
                    }

                    // Gercek cihazda App Attest neden calismadigini gorebilmek icin;
                    // hata metni olmadan teshis kor topuk kaliyor.
                    if let hata = attest.sonHata {
                        Text(hata)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                } header: {
                    Text("Durum")
                } footer: {
                    Text("Cihaz kimliği, çektiğin fotoğrafın gerçekten bu uygulamadan ve gerçek bir cihazdan geldiğini kanıtlar. Simülatörde çalışmaz.")
                }

                Section("Özet") {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                        SayacKutusu(baslik: "Toplam kayıt", deger: store.ozet.toplam,
                                    simge: "square.stack.3d.up", renk: .secondary)
                        SayacKutusu(baslik: "Doğrulanan", deger: store.ozet.dogrulanan,
                                    simge: "checkmark.seal.fill", renk: .green)
                        SayacKutusu(baslik: "Vatandaş ihbarı", deger: store.ozet.vatandasIhbari,
                                    simge: "person.wave.2.fill", renk: .orange)
                        SayacKutusu(baslik: "Uydu tespiti", deger: store.ozet.uyduKaydi,
                                    simge: "antenna.radiowaves.left.and.right", renk: .blue)
                        SayacKutusu(baslik: "Yetkiliye iletilen", deger: store.ozet.yetkiliyeIletilen,
                                    simge: "paperplane.fill", renk: .indigo)
                        SayacKutusu(baslik: "Söndürülen", deger: store.ozet.sonduruldu,
                                    simge: "drop.fill", renk: .teal)
                    }
                    .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
                }

                Section {
                    Text("Yangın Radar bir **yangın gözlem ve topluluk bildirim ağıdır**. Resmî ihbar hattı değildir ve itfaiyeyi ya da orman ekiplerini kendiliğinden çağırmaz.")
                        .font(.footnote)
                    Text("Can güvenliği söz konusuysa **112**'yi, orman yangını için **177**'yi arayın.")
                        .font(.footnote.weight(.semibold))
                } header: {
                    Text("Uygulama hakkında")
                }
            }
            .navigationTitle("Ayarlar")
        }
    }

    private var cihazDurumu: (metin: String, simge: String, renk: Color) {
        if !attest.destekleniyor {
            return ("Desteklenmiyor", "lock.slash", .secondary)
        }
        if attest.kayitliMi {
            return ("Doğrulandı", "lock.shield.fill", .green)
        }
        return ("Kaydedilmedi", "lock.open", .orange)
    }

    private var konumDurumu: String {
        switch konum.izinDurumu {
        case .notDetermined: "Sorulmadı"
        case .denied: "Reddedildi"
        case .restricted: "Kısıtlı"
        case .authorizedWhenInUse: "Kullanırken"
        case .authorizedAlways: "Her zaman"
        @unknown default: "Bilinmiyor"
        }
    }

    private func baglantiyiDene() async {
        deneniyor = true
        defer { deneniyor = false }

        guard let sunucu = store.sunucu else {
            denemeSonucu = "❌ Adres okunamadı."
            return
        }

        do {
            let ozet = try await APIClient(sunucu: sunucu).ozet()
            denemeSonucu = "✅ Bağlandı — \(ozet.toplam) kayıt görünüyor."
            await store.tazele()
        } catch {
            denemeSonucu = "❌ \(error.localizedDescription)"
        }
    }
}
