package com.z33eculab;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Interoperability implementation of the independently documented BlueDriver BLE wire encoding. */
public final class BlueProtocol {
    private BlueProtocol() {}
    public static final String CMD_UUID = "a9da6040-0823-4995-94ec-9ce41ca28833";
    public static final String DATA_UUID = "a73e9a10-628f-4494-a099-12efaf72258f";
    public static final String STATUS_UUID = "75a9f022-af03-4e41-b4bc-9de90a47d50b";
    public static final String SERVICE_UUID = "331a36f5-2459-45ea-9d95-6142f0c4b307";
    public static final byte[] CREDIT = new byte[] { 0x00, (byte)0xFF, 0x7F };
    public static final String[] INIT = {"LMI", "LMD", "LML0", "LMI", "LMD", "LMDP", "LMI", "LMX1", "LMX0"};

    public static byte[] codec(byte[] source) {
        byte[] result = new byte[source.length];
        for (int i = 0; i < source.length; i++) {
            int b = source[i] & 0xFF;
            result[i] = (byte) ((b == 0x0D || b == 0x3E) ? b : (b ^ 0x26));
        }
        return result;
    }
    public static byte[] command(String command) {
        return codec((command + "\r").getBytes(StandardCharsets.US_ASCII));
    }
    public static byte[] ack(int fragmentLength) {
        if (fragmentLength < 1 || fragmentLength > 255) throw new IllegalArgumentException("Invalid BLE fragment length");
        return new byte[]{0x01, (byte)fragmentLength, 0x00};
    }
    public static String decoded(byte[] packet) {
        return new String(codec(packet), StandardCharsets.US_ASCII);
    }
    public static String hex(byte[] packet) {
        StringBuilder s = new StringBuilder();
        for (byte b : packet) s.append(String.format(Locale.US, "%02X", b & 0xFF));
        return s.toString();
    }
    public static boolean allowedPid(String value) {
        return value.equals("0100") || value.equals("0120") || value.equals("0140")
                || value.equals("0105") || value.equals("0106") || value.equals("0107")
                || value.equals("010C") || value.equals("010D") || value.equals("010E")
                || value.equals("010F") || value.equals("0110") || value.equals("0111")
                || value.equals("0900") || value.equals("0903") || value.equals("0904")
                || value.equals("0905") || value.equals("0906") || value.equals("0901") || value.equals("0902");
    }
    /** Transport outcome labels used by UI/export; never include raw data. */
    public static String classifyResponse(String response, boolean complete) {
        if (!complete) return "timeout/partial";
        String value=response==null?"":response.trim();
        String upper=value.toUpperCase(Locale.US);
        if (upper.contains("NO DATA")) return "NO DATA";
        if (value.isEmpty() || upper.contains("ERROR") || upper.contains("UNABLE")
                || upper.contains("STOPPED") || upper.contains("?")) return "malformed";
        return value.endsWith(">") ? "complete" : "malformed";
    }
}
