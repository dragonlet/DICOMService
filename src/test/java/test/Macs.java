package test;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;
import java.net.InterfaceAddress;

public class Macs {

    private static final int MAX_DEPTH = 4;

    private static int depth = 0;


    private static void prnt(String text) {
        for (int i = 0; i < depth; i++) {
            System.out.print("    ");
        }
        System.out.println(text);
    }

    private static void printInterfaceAddress(InterfaceAddress ia) throws Exception {
        if (depth > MAX_DEPTH) return;
        depth++;
        printInterfaceAddress(ia);
        printInetAddress(ia.getAddress());
        printInetAddress(ia.getBroadcast());
        prnt("getNetworkPrefixLength : " + ia.getNetworkPrefixLength());
        depth--;
    }

    private static void printNetIF(NetworkInterface networkInterface) throws Exception {
        if (depth > MAX_DEPTH) return;
        if (networkInterface == null) return;
        depth++;
        prnt("net getDisplayName        : " + networkInterface.getDisplayName());
        prnt("net getMTU                : " + networkInterface.getMTU());
        prnt("net getName               : " + networkInterface.getName());
        List<InterfaceAddress> ifList = networkInterface.getInterfaceAddresses();
        prnt("ifList size: " + ifList.size());
        for (InterfaceAddress ia : ifList) {
            printInterfaceAddress(ia);
        }
        depth--;
    }

    private static void printInetAddress(InetAddress addr) throws Exception {
        if (depth > MAX_DEPTH) return;
        if (addr == null) return;
        depth++;
        prnt(addr.toString());
        prnt("getCanonicalHostName : " + addr.getCanonicalHostName());
        prnt("getHostAddress       : " + addr.getHostAddress());
        prnt("getHostName          : " + addr.getHostName());
        byte[] bAddr =   addr.getAddress();
        String textAd = "";
        for (int i = 0; i < bAddr.length; i++) {
            textAd += " " + (bAddr[i] & 255);
        }
        prnt("getAddress           :" + textAd);

        printNetIF(NetworkInterface.getByInetAddress(addr));

        InetAddress[] all =  InetAddress.getAllByName(addr.getHostName());
        prnt("all.length         : " + all.length);
        if (depth > 0) {
            for (InetAddress ia : all) {
                printInetAddress(ia);
            }
        }
        depth--;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        try {
            InetAddress localMachine = InetAddress.getLocalHost();
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(localMachine);
            byte[] hwAddr = networkInterface.getHardwareAddress();
            System.out.println("hwAddr null: " + ((hwAddr == null) ? "is null" : "not a null"));
            Enumeration<InetAddress> inetAddrList =  networkInterface.getInetAddresses();
            while (inetAddrList.hasMoreElements()) {
                printInetAddress(inetAddrList.nextElement());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
