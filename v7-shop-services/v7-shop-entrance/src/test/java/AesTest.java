import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class AesTest {
    public static void main(String[] args) throws Exception {
        // 密钥和明文
        String keyString = "EAC10-1600R-WG";
        // 填充到16字节
        keyString = String.format("%-16s", keyString).replace(' ', '\0');

        byte[] keyBytes = keyString.getBytes();
        String dataHex = "1A5A010A01050100000D0241";
        byte[] dataBytes = hexStringToByteArray(dataHex);

        // ZeroPadding
        dataBytes = zeroPad(dataHex.getBytes(), 16);

        // 加密
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(dataBytes);


        // Base64编码
        String encryptedBase64 = Base64.getEncoder().encodeToString(encryptedBytes);

        byte[] decode = Base64.getDecoder().decode(encryptedBase64);

        // 解密
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decryptedBytes = cipher.doFinal(decode);

        System.out.println("Encrypted (Base64): " + encryptedBase64);
        System.out.println("Decrypted (Base64): " + bytes2hex(decryptedBytes));
        System.out.println("Decrypted (Str): " + new String(removeZeroPadding(decryptedBytes)));

        System.out.println("Key bytes: " + bytes2hex(keyBytes));
        System.out.println("Data Bytes: " + bytes2hex(dataBytes));
        System.out.println("Encrypted doc: " + bytes2hex(decode));
        System.out.println("Encrypted java: " + bytes2hex(encryptedBytes));
    }

    // 去除ZeroPadding
    private static byte[] removeZeroPadding(byte[] data) {
        int i = data.length - 1;
        while (i >= 0 && data[i] == 0) {
            i--;
        }
        byte[] newArray = new byte[i + 1];
        System.arraycopy(data, 0, newArray, 0, i + 1);
        return newArray;
    }

    // ZeroPadding填充函数
    private static byte[] zeroPad(byte[] data, int blockSize) {
        int paddingRequired = blockSize - (data.length % blockSize);
        byte[] paddedData = new byte[data.length + paddingRequired];
        System.arraycopy(data, 0, paddedData, 0, data.length);
        return paddedData;
    }

    // 将十六进制字符串转换为字节数组
    private static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }

    // 将字节数组转换为十六进制字符串
    private static String bytes2hex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
