import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Independent JDK provider verification of PUBLIC fixtures; never packaged into the app. */
class VerifyCryptoVectors {
    static byte[] join(byte[]... values) {
        int size = Arrays.stream(values).mapToInt(v -> v.length).sum();
        byte[] out = new byte[size]; int offset = 0;
        for (byte[] value : values) { System.arraycopy(value, 0, out, offset, value.length); offset += value.length; }
        return out;
    }
    static byte[] crypt(int mode, byte[] key, byte[] nonce, byte[] aad, byte[] input) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(input);
    }
    static void reject(byte[] key, byte[] nonce, byte[] aad, byte[] input) throws Exception {
        try { crypt(Cipher.DECRYPT_MODE, key, nonce, aad, input); }
        catch (AEADBadTagException expected) { return; }
        throw new AssertionError("Unauthenticated input accepted");
    }
    static byte[] flip(byte[] bytes, int index) {
        byte[] copy = bytes.clone(); copy[index] ^= 1; return copy;
    }
    public static void main(String[] args) throws Exception {
        int positives = 0, negatives = 0;
        var b64 = Base64.getDecoder(); var hex = HexFormat.of();
        for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            String[] f = line.split("\t", -1);
            byte[] salt = f[2].equals("-") ? new byte[0] : b64.decode(f[2]);
            byte[] key = hex.parseHex(f[3]), nonce = b64.decode(f[4]), aad = hex.parseHex(f[5]);
            byte[] plain = b64.decode(f[6]), encrypted = join(b64.decode(f[7]), b64.decode(f[8]));
            if (!f[1].equals("-")) {
                char[] password = new String(b64.decode(f[1]), StandardCharsets.UTF_8).toCharArray();
                PBEKeySpec spec = new PBEKeySpec(password, salt, 200_000, 256);
                byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
                spec.clearPassword(); Arrays.fill(password, '\0');
                if (!Arrays.equals(derived, key)) throw new AssertionError("PBKDF2 mismatch");
            }
            if (!Arrays.equals(crypt(Cipher.ENCRYPT_MODE, key, nonce, aad, plain), encrypted)) throw new AssertionError("Encryption mismatch");
            if (!Arrays.equals(crypt(Cipher.DECRYPT_MODE, key, nonce, aad, encrypted), plain)) throw new AssertionError("Decryption mismatch");
            positives++;
            for (String domain : new String[]{"aqz:v1", "aqbak:v1", "aqsnapshot:v1"}) {
                if (!domain.equals(f[0])) {
                    reject(key, nonce, join(domain.getBytes(StandardCharsets.UTF_8), salt, nonce), encrypted); negatives++;
                }
            }
            reject(key, nonce, new byte[0], encrypted); negatives++;
            reject(key, nonce, aad, flip(encrypted, 0)); negatives++;
            reject(key, nonce, aad, flip(encrypted, encrypted.length - 1)); negatives++;
            reject(flip(key, 0), nonce, aad, encrypted); negatives++;
        }
        System.out.printf("Java: %d exact-vector round trips; %d authentication rejections passed.%n", positives, negatives);
    }
}
