package cn.v7soft.admin.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AnimatedImageDetectorTest {
    @ParameterizedTest
    @MethodSource("animatedImages")
    void detectsAnimatedContent(byte[] data) {
        assertTrue(AnimatedImageDetector.isAnimated(data));
    }

    static Stream<byte[]> animatedImages() {
        return Stream.of(
                bytes("GIF87a"), bytes("GIF89a"),
                png(chunk("acTL", ByteBuffer.allocate(8).putInt(2).putInt(0).array(), false)),
                webp(chunk("VP8X", new byte[] {2, 0, 0, 0, 0, 0, 0, 0, 0, 0}, true)),
                webp(chunk("JUNK", new byte[] {1}, true),
                        chunk("VP8X", new byte[] {2, 0, 0, 0, 0, 0, 0, 0, 0, 0}, true)));
    }

    @ParameterizedTest
    @MethodSource("staticImages")
    void doesNotMistakeMetadataForAnimation(byte[] data) {
        assertFalse(AnimatedImageDetector.isAnimated(data));
    }

    static Stream<byte[]> staticImages() {
        return Stream.of(
                png(chunk("tEXt", bytes("comment\0acTL ANIM GIF89a"), false)),
                webp(chunk("VP8X", new byte[10], true), chunk("EXIF", bytes("ANIM"), true)),
                webp(chunk("VP8 ", bytes("ANIM"), true)),
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff},
                new byte[0]);
    }

    @Test
    void rejectsTruncatedAndOversizedChunksWithoutThrowing() {
        assertFalse(AnimatedImageDetector.isAnimated(null));
        for (byte[] data : animatedImages().toList()) {
            for (int length = 0; length < data.length; length++) {
                assertFalse(AnimatedImageDetector.isAnimated(Arrays.copyOf(data, length)));
            }
        }
        byte[] png = png(chunk("acTL", new byte[8], false));
        Arrays.fill(png, 33, 37, (byte) 0xff);
        assertFalse(AnimatedImageDetector.isAnimated(png));
        byte[] webp = webp(chunk("VP8X", new byte[10], true));
        Arrays.fill(webp, 16, 20, (byte) 0xff);
        assertFalse(AnimatedImageDetector.isAnimated(webp));
    }

    @Test
    void ignoresAnimationMarkersAfterPngImageDataOrOutsideWebpContainer() {
        assertFalse(AnimatedImageDetector.isAnimated(png(
                chunk("IDAT", new byte[0], false),
                chunk("acTL", ByteBuffer.allocate(8).putInt(2).putInt(0).array(), false))));
        byte[] webp = webp(chunk("VP8 ", new byte[0], true),
                chunk("VP8X", new byte[] {2, 0, 0, 0, 0, 0, 0, 0, 0, 0}, true));
        ByteBuffer.wrap(webp).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 12);
        assertFalse(AnimatedImageDetector.isAnimated(webp));
    }

    @Test
    void rejectsInvalidAnimationControl() {
        assertFalse(AnimatedImageDetector.isAnimated(png(chunk("acTL", new byte[8], false))));
        assertFalse(AnimatedImageDetector.isAnimated(png(chunk("acTL", new byte[] {1}, false))));
        assertFalse(AnimatedImageDetector.isAnimated(webp(chunk("VP8X", new byte[] {2}, true))));
    }

    // Container fixtures exercise header parsing without depending on an image decoder.
    private static byte[] png(byte[]... chunks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(bytes("\u0089PNG\r\n\u001a\n"));
        out.writeBytes(chunk("IHDR", ByteBuffer.allocate(13).putInt(1).putInt(1)
                .put((byte) 8).put((byte) 6).array(), false));
        for (byte[] chunk : chunks) out.writeBytes(chunk);
        return out.toByteArray();
    }

    private static byte[] webp(byte[]... chunks) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(bytes("WEBP"));
        for (byte[] chunk : chunks) body.writeBytes(chunk);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(bytes("RIFF"));
        out.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size()).array());
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    private static byte[] chunk(String type, byte[] payload, boolean riff) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] size = ByteBuffer.allocate(4).order(riff ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN)
                .putInt(payload.length).array();
        out.writeBytes(riff ? bytes(type) : size);
        out.writeBytes(riff ? size : bytes(type));
        out.writeBytes(payload);
        if (riff) {
            if ((payload.length & 1) != 0) out.write(0);
        } else {
            CRC32 crc = new CRC32();
            crc.update(bytes(type));
            crc.update(payload);
            out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        }
        return out.toByteArray();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }
}
