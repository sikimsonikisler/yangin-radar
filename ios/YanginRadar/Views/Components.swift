import SwiftUI

/// Haritadaki yangin isareti. Buyuklugu siddete, solgunlugu duruma gore degisir.
struct YanginIsareti: View {
    let ihbar: FireReport
    var secili = false

    private var boyut: CGFloat {
        switch ihbar.severity {
        case .dumanGoruldu: 26
        case .kucukYangin: 30
        case .buyukYangin: 36
        case .hizlaYayiliyor: 40
        }
    }

    var body: some View {
        ZStack {
            Circle()
                .fill(ihbar.severity.renk.opacity(ihbar.status.aktif ? 0.25 : 0.12))
                .frame(width: boyut * 1.7, height: boyut * 1.7)

            Circle()
                .fill(ihbar.severity.renk.opacity(ihbar.status.aktif ? 1 : 0.45))
                .frame(width: boyut, height: boyut)
                .overlay {
                    Circle().stroke(.white, lineWidth: secili ? 3 : 2)
                }
                .shadow(radius: secili ? 6 : 2)

            Image(systemName: ihbar.source == .uydu ? "antenna.radiowaves.left.and.right"
                                                    : ihbar.severity.simge)
                .font(.system(size: boyut * 0.45, weight: .bold))
                .foregroundStyle(.white)
        }
        .scaleEffect(secili ? 1.15 : 1)
        .animation(.spring(duration: 0.25), value: secili)
    }
}

/// Simge + yazi ciftini yan yana gosterir.
///
/// `LabeledContent` icine dogrudan `Label` konuldugunda satir yuksekligi asiri
/// buyuyor (iOS 26'da gozlendi); bu yuzden bu ekranlarda `Label` yerine bu
/// bilesen kullaniliyor.
struct SimgeliDeger: View {
    let metin: String
    let simge: String
    var renk: Color = .primary

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: simge)
            Text(metin)
        }
        .foregroundStyle(renk)
    }
}

/// "Doğrulandı" gibi kucuk durum rozeti.
struct DurumRozeti: View {
    let durum: ReportStatus

    var body: some View {
        Text(durum.label)
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(durum.renk.opacity(0.18), in: Capsule())
            .foregroundStyle(durum.renk)
    }
}

/// Guven puani cubugu.
struct GuvenCubugu: View {
    let puan: Int

    private var renk: Color {
        switch puan {
        case ..<40: .red
        case 40..<75: .orange
        default: .green
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("Güven puanı")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Text("\(puan)/100")
                    .font(.caption.weight(.semibold).monospacedDigit())
                    .foregroundStyle(renk)
            }
            GeometryReader { alan in
                ZStack(alignment: .leading) {
                    Capsule().fill(.quaternary)
                    Capsule()
                        .fill(renk)
                        .frame(width: alan.size.width * CGFloat(max(0, min(100, puan))) / 100)
                }
            }
            .frame(height: 6)
        }
    }
}

/// Listedeki tek ihbar satiri.
struct IhbarSatiri: View {
    let ihbar: FireReport

    var body: some View {
        HStack(spacing: 12) {
            YanginIsareti(ihbar: ihbar)
                .frame(width: 44)

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(ihbar.yerAdi)
                        .font(.headline)
                        .lineLimit(1)
                    DurumRozeti(durum: ihbar.status)
                }

                Text(ihbar.severity.label)
                    .font(.subheadline)
                    .foregroundStyle(ihbar.severity.renk)

                HStack(spacing: 10) {
                    Label(ihbar.gecenSure, systemImage: "clock")
                    Label(ihbar.source.label, systemImage: ihbar.source.simge)
                    if ihbar.photoCount > 0 {
                        Label("\(ihbar.photoCount)", systemImage: "photo")
                    }
                }
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            }

            Spacer(minLength: 0)

            Text("\(ihbar.confidence)")
                .font(.headline.monospacedDigit())
                .foregroundStyle(ihbar.confidence >= 75 ? .green
                                 : ihbar.confidence >= 40 ? .orange : .red)
        }
        .padding(.vertical, 4)
    }
}

/// Ozet ekranindaki tek sayac kutusu.
struct SayacKutusu: View {
    let baslik: String
    let deger: Int
    let simge: String
    var renk: Color = .accentColor

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: simge)
                .foregroundStyle(renk)
            Text("\(deger)")
                .font(.title2.bold().monospacedDigit())
            Text(baslik)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2, reservesSpace: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 12))
    }
}

/// Bos liste / hata durumlarinda gosterilen aciklama.
struct BosDurum: View {
    let simge: String
    let baslik: String
    let aciklama: String
    var eylemBasligi: String?
    var eylem: (() -> Void)?

    var body: some View {
        ContentUnavailableView {
            Label(baslik, systemImage: simge)
        } description: {
            Text(aciklama)
        } actions: {
            if let eylemBasligi, let eylem {
                Button(eylemBasligi, action: eylem)
                    .buttonStyle(.borderedProminent)
            }
        }
    }
}
