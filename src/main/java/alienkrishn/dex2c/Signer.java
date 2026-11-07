package alienkrishn.dex2c;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkSigner.SignerConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collections;

final class Signer {

    private static final String CERTIFICATE = "/keys/testkey.x509.pem";
    private static final String PRIVATE_KEY = "/keys/testkey.pk8";

    private Signer() {
    }

    static void sign(File input, File output) throws Exception {
        File parent = output.getAbsoluteFile().getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        File temporary = File.createTempFile("dex2c-signed-", ".apk", parent);
        boolean moved = false;
        try {
            ApkSigner signer = new ApkSigner.Builder(Collections.singletonList(loadKey()))
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(true)
                    .setV4SigningEnabled(false)
                    .setCreatedBy("dex2c")
                    .setInputApk(input)
                    .setOutputApk(temporary)
                    .build();
            signer.sign();
            Files.move(temporary.toPath(), output.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) {
                //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
        }
    }

    private static SignerConfig loadKey() throws Exception {
        X509Certificate certificate;
        try (InputStream in = resource(CERTIFICATE)) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(in);
        }
        PrivateKey key;
        try (InputStream in = resource(PRIVATE_KEY)) {
            key = readPrivateKey(in.readAllBytes());
        }
        return new SignerConfig.Builder("dex2c", key,
                Collections.singletonList(certificate)).build();
    }

    private static PrivateKey readPrivateKey(byte[] bytes) throws Exception {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (InvalidKeySpecException e) {
            return KeyFactory.getInstance("DSA").generatePrivate(spec);
        }
    }

    private static InputStream resource(String name) throws IOException {
        InputStream in = Signer.class.getResourceAsStream(name);
        if (in == null) {
            throw new IOException("No bundled signing key: " + name);
        }
        return in;
    }
}
