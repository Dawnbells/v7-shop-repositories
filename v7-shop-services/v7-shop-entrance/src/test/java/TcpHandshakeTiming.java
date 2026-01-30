import cn.hutool.json.JSONUtil;
import org.pcap4j.core.*;
import org.pcap4j.packet.*;

import java.util.concurrent.TimeoutException;

public class TcpHandshakeTiming {

    public static void main(String[] args) {
        String filter = "tcp and host 217.24.68.80 and port 80"; // Replace with your target IP and port

        try {
            for (PcapNetworkInterface allDev : Pcaps.findAllDevs()) {
                System.out.println(allDev.getName() + ", " + allDev.getDescription() + ", " + allDev.isRunning() + ", " + allDev.isLocal() + ", " + allDev.isUp() + "," + allDev.isLoopBack());
            }

            // 1. Select a network interface
            PcapNetworkInterface nif = Pcaps.getDevByName("\\Device\\NPF_{E00B565D-50AE-4BD1-8DC7-74E1EC4309B0}"); // Replace with your network interface
            if (nif == null) {
                System.err.println("No network interface found.");
                return;
            }

            // 2. Open the network interface for capturing
            PcapHandle handle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10 * 1000);

            // 3. Set a filter to capture only TCP packets for the target
            handle.setFilter(filter, BpfProgram.BpfCompileMode.OPTIMIZE);

            Packet synAckPacket = null;
            Packet ackPacket = null;
            long synAckTime = 0;
            long ackTime = 0;

            System.out.println("Listening for TCP handshake packets...");

            // 4. Capture packets and analyze
            while (true) {
                try {
                    Packet packet = handle.getNextPacketEx();
                    TcpPacket tcpPacket = packet.get(TcpPacket.class);
                    if (tcpPacket == null) continue;

                    // Extract TCP Flags
                    TcpPacket.TcpHeader header = tcpPacket.getHeader();
                    boolean isSynAck = header.getSyn() && header.getAck();
                    boolean isAck = !header.getSyn() && header.getAck();

                    if (isSynAck && synAckPacket == null) {
                        synAckPacket = packet;
                        synAckTime = handle.getTimestamp().getTime();
                        System.out.println("Captured SYN-ACK packet at " + synAckTime);
                    } else if (isAck && synAckPacket != null && ackPacket == null) {
                        ackPacket = packet;
                        ackTime = handle.getTimestamp().getTime();
                        System.out.println("Captured ACK packet at " + ackTime);
                        break;
                    }
                } catch (TimeoutException e) {
                    // Timeout: No packet received, continue listening
                }
            }

            // 5. Calculate the time difference
            if (synAckTime > 0 && ackTime > 0) {
                long delta = ackTime - synAckTime;
                System.out.println("Time between SYN-ACK and ACK: " + delta + " ms");
            } else {
                System.out.println("Failed to capture both SYN-ACK and ACK packets.");
            }

            // Close the handle
            handle.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
