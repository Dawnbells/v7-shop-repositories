package cn.v7soft.admin.task;

/** Content-based detection before translation dispatch; no image decoding or filename assumptions. */
final class AnimatedImageDetector {
    private AnimatedImageDetector() {
    }

    static boolean isAnimated(byte[] data) {
        if (data == null) return false;
        // Keep the existing policy of skipping all GIFs, including single-frame GIFs.
        if (matches(data, 0, "GIF87a") || matches(data, 0, "GIF89a")) return true;
        if (matches(data, 0, "\u0089PNG\r\n\u001a\n")) return isApng(data);
        if (matches(data, 0, "RIFF") && matches(data, 8, "WEBP")) return isAnimatedWebp(data);
        return false;
    }

    private static boolean isApng(byte[] data) {
        // https://www.w3.org/TR/png/#acTL-chunk
        // Walk chunk boundaries so text/metadata containing "acTL" cannot trigger a skip.
        int offset = 8;
        while (offset <= data.length - 12) {
            long length = uint32(data, offset, false);
            long next = offset + 12L + length; // length + type + payload + CRC
            if (next > data.length) return false;
            if (matches(data, offset + 4, "acTL")) {
                return length == 8 && uint32(data, offset + 8, false) > 0;
            }
            // Animation control must precede the first image-data chunk.
            if (matches(data, offset + 4, "IDAT") || matches(data, offset + 4, "IEND")) return false;
            offset = (int) next;
        }
        return false;
    }

    private static boolean isAnimatedWebp(byte[] data) {
        // https://developers.google.com/speed/webp/docs/riff_container#extended_file_format
        long end = 8L + uint32(data, 4, true);
        if (end > data.length || end < 12) return false;
        int offset = 12;
        while (offset <= end - 8) {
            long length = uint32(data, offset + 4, true);
            long next = offset + 8L + length + (length & 1); // RIFF chunks are word-aligned.
            if (next > end) return false;
            if (matches(data, offset, "VP8X")) {
                return length == 10 && (data[offset + 8] & 0x02) != 0;
            }
            offset = (int) next;
        }
        return false;
    }

    private static boolean matches(byte[] data, int offset, String signature) {
        if (offset > data.length - signature.length()) return false;
        for (int i = 0; i < signature.length(); i++) {
            if ((data[offset + i] & 0xff) != signature.charAt(i)) return false;
        }
        return true;
    }

    private static long uint32(byte[] data, int offset, boolean littleEndian) {
        long value = 0;
        for (int i = 0; i < 4; i++) {
            value = (value << 8) | (data[offset + (littleEndian ? 3 - i : i)] & 0xff);
        }
        return value;
    }
}
