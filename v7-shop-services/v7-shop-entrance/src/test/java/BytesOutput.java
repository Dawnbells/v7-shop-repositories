import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.HexUtil;
import org.springframework.data.util.ReactiveWrappers;

public class BytesOutput {

    public static void main(String[] args) {
        byte[] bytes = FileUtil.readBytes("D:\\Messages\\WXWork\\1688851192994744\\Cache\\File\\2024-06\\SAPOS CA.cer");
        int index = 0;

        String s = HexUtil.encodeHexStr(bytes).toUpperCase();
        while (index < bytes.length) {
            if (Byte.compareUnsigned(bytes[index], (byte)0x80) >= 0) {
                System.out.print("(byte) ");
            }
            System.out.print("0x");
            System.out.print(s.substring(index * 2, index * 2 + 2));
            System.out.print(", ");
            if (index % 16 == 15) {
                System.out.println();
            }
            ++index;
        }
    }
}
