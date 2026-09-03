package com.yanginradar.service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webauthn4j.appattest.DeviceCheckManager;
import com.webauthn4j.appattest.authenticator.DCAppleDevice;
import com.webauthn4j.appattest.authenticator.DCAppleDeviceImpl;
import com.webauthn4j.appattest.data.DCAssertionData;
import com.webauthn4j.appattest.data.DCAssertionParameters;
import com.webauthn4j.appattest.data.DCAssertionRequest;
import com.webauthn4j.appattest.data.DCAttestationData;
import com.webauthn4j.appattest.data.DCAttestationParameters;
import com.webauthn4j.appattest.data.DCAttestationRequest;
import com.webauthn4j.appattest.server.DCServerProperty;
import com.webauthn4j.anchor.TrustAnchorRepository;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.statement.AttestationStatement;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.verifier.attestation.trustworthiness.certpath.DefaultCertPathTrustworthinessVerifier;
import com.yanginradar.config.AppProperties;
import com.yanginradar.domain.AttestedDevice;
import com.yanginradar.repo.AttestedDeviceRepository;

/**
 * Apple App Attest dogrulamasi.
 *
 * <p>Ne kanitlar: istegin gercekten <b>bizim imzaladigimiz uygulamadan</b> ve
 * <b>gercek bir Apple cihazindan</b> geldigini. Apple, cihazin guvenli bolmesinde
 * uretilen anahtar icin bir sertifika zinciri imzalar; zincir Apple'in kok
 * sertifikasina kadar dogrulanir.
 *
 * <p>Ne kanitlamaz: fotografin gercekten yangin gosterdigini. Bu katman kaynagin
 * sahteligini engeller, icerigin dogrulugunu degil.
 *
 * <p>Kayit bir kez yapilir ({@link #cihaziKaydet}); sonraki her fotograf yuklemesinde
 * o anahtarla uretilmis bir imza beklenir ({@link #imzayiDogrula}). Imza sayaci her
 * seferinde kesin olarak artmali - artmiyorsa imza tekrar oynatiliyordur.
 */
@Service
public class AppAttestService {

    private static final Logger log = LoggerFactory.getLogger(AppAttestService.class);

    private final AppProperties props;
    private final AttestedDeviceRepository deviceRepo;
    private final DeviceCheckManager manager;
    private final ObjectConverter objectConverter;
    private final AttestedCredentialDataConverter credentialConverter;

    public AppAttestService(AppProperties props, AttestedDeviceRepository deviceRepo) {
        this.props = props;
        this.deviceRepo = deviceRepo;
        this.objectConverter = DeviceCheckManager.createObjectConverter();
        this.credentialConverter = new AttestedCredentialDataConverter(objectConverter);
        this.manager = new DeviceCheckManager(
                new DefaultCertPathTrustworthinessVerifier(appleKokDeposu()), objectConverter);
    }

    public boolean acik() {
        return props.getAttest().isEnabled();
    }

    /**
     * Cihazin App Attest anahtarini kaydeder.
     *
     * @param nonce  sunucunun urettigi meydan okuma (ham metin)
     * @param keyIdB64 Apple'in urettigi anahtar tanitici, base64
     * @param attestationObjectB64 Apple'in urettigi attestation nesnesi, base64
     */
    @Transactional
    public void cihaziKaydet(String nonce, String keyIdB64, String attestationObjectB64) {
        byte[] keyId = Base64.getDecoder().decode(keyIdB64);
        byte[] attestationObject = Base64.getDecoder().decode(attestationObjectB64);
        byte[] challenge = nonce.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] clientDataHash = sha256(challenge);

        DCServerProperty sunucu = new DCServerProperty(
                props.getAttest().getTeamId(),
                props.getAttest().getBundleId(),
                new DefaultChallenge(challenge));

        DCAttestationData sonuc = manager.validate(
                new DCAttestationRequest(keyId, attestationObject, clientDataHash),
                new DCAttestationParameters(sunucu));

        AttestedCredentialData kimlik = sonuc.getAttestationObject()
                .getAuthenticatorData().getAttestedCredentialData();
        AttestationStatement ifade = sonuc.getAttestationObject().getAttestationStatement();
        long sayac = sonuc.getAttestationObject().getAuthenticatorData().getSignCount();

        deviceRepo.save(new AttestedDevice(
                keyIdB64,
                credentialConverter.convert(kimlik),
                objectConverter.getCborConverter().writeValueAsBytes(ifade),
                sayac));

        log.info("App Attest cihazi kaydedildi: {}", kisalt(keyIdB64));
    }

    /**
     * Fotograf yuklemesindeki imzayi dogrular.
     *
     * @param keyIdB64 kayitli anahtar tanitici
     * @param assertionB64 cihazin urettigi imza
     * @param imzalananVeri imzanin kapsadigi veri (nonce + ihbar no + fotograf ozeti)
     * @return dogrulama basarili ise true
     */
    @Transactional
    public boolean imzayiDogrula(String keyIdB64, String assertionB64,
                                 byte[] imzalananVeri, String nonce) {
        AttestedDevice cihaz = deviceRepo.findById(keyIdB64).orElse(null);
        if (cihaz == null) {
            log.warn("Bilinmeyen App Attest anahtari: {}", kisalt(keyIdB64));
            return false;
        }

        try {
            DCServerProperty sunucu = new DCServerProperty(
                    props.getAttest().getTeamId(),
                    props.getAttest().getBundleId(),
                    new DefaultChallenge(nonce.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

            DCAppleDevice appleDevice = new DCAppleDeviceImpl(
                    credentialConverter.convert(cihaz.getAttestedCredentialData()),
                    objectConverter.getCborConverter()
                            .readValue(cihaz.getAttestationStatement(), AttestationStatement.class),
                    cihaz.getSignCount(),
                    null);

            DCAssertionData sonuc = manager.validate(
                    new DCAssertionRequest(
                            Base64.getDecoder().decode(keyIdB64),
                            Base64.getDecoder().decode(assertionB64),
                            sha256(imzalananVeri)),
                    new DCAssertionParameters(sunucu, appleDevice));

            long yeniSayac = sonuc.getAuthenticatorData().getSignCount();
            if (yeniSayac <= cihaz.getSignCount()) {
                // Sayac artmadiysa ayni imza yeniden oynatiliyor demektir.
                log.warn("App Attest imza sayaci artmadi ({} -> {}): {}",
                        cihaz.getSignCount(), yeniSayac, kisalt(keyIdB64));
                return false;
            }
            cihaz.setSignCount(yeniSayac);
            deviceRepo.save(cihaz);
            return true;

        } catch (Exception e) {
            log.warn("App Attest imzasi dogrulanamadi ({}): {}", kisalt(keyIdB64), e.toString());
            return false;
        }
    }

    /** Apple App Attest kok sertifikasi - sertifika zincirinin dayandigi tek nokta. */
    private TrustAnchorRepository appleKokDeposu() {
        try (InputStream in = new ClassPathResource("apple-app-attest-root-ca.pem").getInputStream()) {
            X509Certificate kok = (X509Certificate) CertificateFactory
                    .getInstance("X.509").generateCertificate(in);
            Set<TrustAnchor> capalar = Set.of(new TrustAnchor(kok, null));

            return new TrustAnchorRepository() {
                @Override
                public Set<TrustAnchor> find(AAGUID aaguid) {
                    return capalar;
                }

                @Override
                public Set<TrustAnchor> find(byte[] attestationCertificateKeyIdentifier) {
                    return capalar;
                }
            };
        } catch (Exception e) {
            throw new IllegalStateException("Apple kök sertifikası okunamadı", e);
        }
    }

    private static byte[] sha256(byte[] veri) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(veri);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String kisalt(String s) {
        return s == null ? "-" : s.substring(0, Math.min(12, s.length())) + "...";
    }
}
