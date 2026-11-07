package alienkrishn.dex2c;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

final class Identity {

    private static final byte[] KEY = Base64.getDecoder().decode("EZboBlIhYPCU11d4RtFTGg==");
    private static final String AUTHOR = decode("UPqBYzxKEpnnvzk=");
    private static final String HANDLE = decode("UPiHaGZ4D4U=");
    private static final String REPO = decode("dv+cbidDTpP7ung5KL49Lkj5nSk2RBjC9w==");
    private static final byte[] CHECK = decodeBytes("gK5mVok+G6atBet730PNpjxnv7DZms52zxNB4RdQqZg=");

    private Identity() {
    }

    static String author() {
        ensureValid();
        return AUTHOR;
    }

    static String handle() {
        ensureValid();
        return HANDLE;
    }

    static String repo() {
        ensureValid();
        return REPO;
    }

    static void ensureValid() {
        if (!check()) {
            throw new IllegalStateException("dex2c: this build has been modified");
        }
    }

    private static boolean check() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] actual = digest.digest((AUTHOR + "\n" + HANDLE + "\n" + REPO)
                    .getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(CHECK, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static String decode(String value) {
        return new String(decodeBytes(value), StandardCharsets.UTF_8);
    }

    private static byte[] decodeBytes(String value) {
        byte[] data = Base64.getDecoder().decode(value);
        for (int i = 0; i < data.length; i++) {
            data[i] ^= KEY[i % KEY.length];
        }
        return data;
    }
}
