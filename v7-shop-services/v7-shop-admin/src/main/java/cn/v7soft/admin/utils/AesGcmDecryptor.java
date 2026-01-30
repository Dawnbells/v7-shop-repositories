package cn.v7soft.admin.utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import cn.hutool.core.util.StrUtil;

public class AesGcmDecryptor {

    // 与前端保持一致
    private static final int SALT_LEN = 16;       // 16 bytes
    private static final int IV_LEN = 12;         // 12 bytes for GCM
    private static final int TAG_LEN_BIT = 128;   // 128-bit auth tag
    private static final int ITERATIONS = 100_000;
    private static final int KEY_LEN_BIT = 256;
    private static final String PASSWORD = "R9u$A2@xL8w#Q7vBf1^Ze6nM!Gp4TyXh";

    public static String decrypt(String packetB64) {
        try {
            if (StrUtil.isBlank(packetB64)) {
                return "";
            }
            return decrypt(packetB64, PASSWORD);
        } catch (Exception e) {
            return "";
        }
    }

    public static String decrypt(String packetB64, String password) throws Exception {
        byte[] packet = Base64.getDecoder().decode(packetB64);

        ByteBuffer bb = ByteBuffer.wrap(packet);
        byte[] salt = new byte[SALT_LEN];
        byte[] iv = new byte[IV_LEN];
        byte[] ct = new byte[packet.length - SALT_LEN - IV_LEN];
        bb.get(salt);
        bb.get(iv);
        bb.get(ct);

        SecretKey aesKey = deriveKey(password.toCharArray(), salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LEN_BIT, iv);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec);
        byte[] pt = cipher.doFinal(ct);
        return new String(pt, StandardCharsets.UTF_8);
    }

    private static SecretKey deriveKey(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LEN_BIT);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    // 简单测试
    public static void main(String[] args) throws Exception {
        String decrypted = decrypt("fMGGqtevsQsPzUTCKdvXkZPxzrCzvRFl6%2Be3XFc4a2GN9sR/ktZeKn6cywUZWHDN6fSn/b8cNEAr6nEzYg==", "R9u$A2@xL8w#Q7vBf1^Ze6nM!Gp4TyXh");
        System.out.println(decrypted);
    }
}
