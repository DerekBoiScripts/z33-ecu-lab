package com.z33eculab;

import java.util.List;
import java.util.Locale;

/** Only generic Mode 02 frame 00. No arbitrary request construction. */
public final class FreezeFrame {
    private FreezeFrame() {}
    public static final String[] REQUESTS={"020200","020400","020500","020C00","020D00","020E00","020F00","021000","021100"};
    public static final String[] LABELS={"DTC causing frame","Calculated load","Coolant","RPM","Speed","Timing advance","Intake temperature","MAF","Throttle"};
    // Require an explicit frame byte, with optional service/PID header. Bare data is ambiguous.
    private static List<Integer> payload(String request,String raw) {
        if(!BlueProtocol.allowedFreezeFrame(request) || ResponseParser.unavailable(raw)) return null;
        String s=raw.replace(">", "").trim();
        if(!s.matches("(?i)[0-9a-f]{2}(?:[ \\t]+[0-9a-f]{2})*")) return null;
        List<Integer> b=ResponseParser.bytes(s);
        int pid=Integer.parseInt(request.substring(2,4),16);
        int length=pid==0?4:(pid==2||pid==12||pid==16?2:1);
        int p=0;
        if(b.size()==length+3) {
            if(b.get(0)!=0x42 || b.get(1)!=pid) return null;
            p=2;
        } else if(b.size()!=length+1) return null;
        if(b.get(p)!=0) return null;
        return b.subList(p+1,b.size());
    }
    public static boolean validSupport(String raw) { return payload("020000",raw)!=null; }
    public static boolean advertises(String raw,String request) {
        if(!BlueProtocol.allowedFreezeFrame(request) || request.equals("020000")) return false;
        List<Integer> b=payload("020000",raw); if(b==null)return false;
        long bits=0; for(int a:b)bits=(bits<<8)|a;
        int pid=Integer.parseInt(request.substring(2,4),16);
        return (bits&(1L<<(32-pid)))!=0;
    }
    public static String value(String request,String raw) {
        if(!BlueProtocol.allowedFreezeFrame(request)) return "Unsupported request";
        String s=raw==null?"":raw.replace(">", "").trim();
        if(s.equalsIgnoreCase("NO DATA"))return "NO DATA — frame unavailable through this path";
        if(s.matches("(?i)7F[ \\t]+02[ \\t]+[0-9A-F]{2}") || s.equals("?"))return "Unsupported/rejected by ECU or adapter";
        List<Integer> b=payload(request,raw); if(b==null)return "Unavailable or malformed frame-00 response";
        if(request.equals("020000"))return "Frame-00 support bitmap received";
        StringBuilder data=new StringBuilder(); for(int a:b) { if(data.length()>0)data.append(' '); data.append(String.format(Locale.US,"%02X",a)); }
        if(request.equals("020200")) {
            List<String> codes=ResponseParser.dtcCodes("03",data.toString());
            return codes.isEmpty()?"No DTC causing frame reported":codes.get(0)+" — "+DtcDescriptions.describe(codes.get(0));
        }
        if(request.equals("020C00"))return ResponseParser.rpm(data.toString());
        return ResponseParser.live("01"+request.substring(2,4),data.toString());
    }
}
