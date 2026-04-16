package cn.v7soft.core.utils;

import java.math.BigInteger;

public class Base36Utils {
    private static final String BASE36_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static String encode(byte[] data) {
        BigInteger number = new BigInteger(1, data); // Create a positive BigInteger
        StringBuilder encoded = new StringBuilder();

        while (number.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divmod = number.divideAndRemainder(BigInteger.valueOf(36));
            encoded.insert(0, BASE36_CHARS.charAt(divmod[1].intValue()));
            number = divmod[0];
        }

        return encoded.toString();
    }

    public static byte[] decode(String encoded) {
        BigInteger number = BigInteger.ZERO;
        for (int i = 0; i < encoded.length(); i++) {
            number = number.multiply(BigInteger.valueOf(36));
            number = number.add(BigInteger.valueOf(BASE36_CHARS.indexOf(encoded.charAt(i))));
        }

        // Convert BigInteger to byte array
        byte[] decoded = number.toByteArray();

        // Handle leading zero byte for positive BigInteger
        if (decoded[0] == 0) {
            byte[] tmp = new byte[decoded.length - 1];
            System.arraycopy(decoded, 1, tmp, 0, tmp.length);
            decoded = tmp;
        }

        return decoded;
    }

    public static void main(String[] args) {
        String data = "1817599510952542208";
        System.out.println("Original data: " + data);

        String encoded = encode(data.getBytes());
        System.out.println("Encoded data: " + encoded);

        byte[] decodedBytes = decode(encoded);
        String decoded = new String(decodedBytes);
        System.out.println("Decoded data: " + decoded);
    }
}
