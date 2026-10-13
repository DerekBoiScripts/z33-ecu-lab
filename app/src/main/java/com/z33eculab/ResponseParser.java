package com.z33eculab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Conservative parser: never claims to have identified an ECU when Mode 09 data is ambiguous. */
public final class ResponseParser {
    private ResponseParser() {}

    public static boolean unavailable(String raw) {
        if(raw==null) return true;
        String r = raw.toUpperCase(Locale.US);
        return r.contains("NO DATA") || r.contains("ERROR") || r.contains("UNABLE")
                || r.contains("STOPPED") || r.contains("?") || r.contains("TIMEOUT") || r.trim().isEmpty();
    }
    /** Parse a simple single-PID RPM response. Device may omit the mode/PID header. */
    public static String rpm(String raw) {
        if (unavailable(raw)) return "Not available";
        if (!hexResponse(raw)) return "Unrecognized response";
        List<Integer> data = bytes(raw);
        if (data.size() >= 4 && data.get(0) == 0x41 && data.get(1) == 0x0C) data = data.subList(2, data.size());
        if (data.size() != 2) return "Unrecognized response";
        int rpm = (data.get(0) * 256 + data.get(1)) / 4;
        if (rpm < 0 || rpm > 12000) return "Unrecognized response";
        return rpm + " RPM";
    }
    public static String voltage(String raw) {
        if (unavailable(raw)) return "Not available";
        List<Integer> bytes = bytes(raw);
        if (bytes.size() >= 4 && bytes.get(0) == 0x41 && bytes.get(1) == 0x42) bytes = bytes.subList(2, bytes.size());
        if (bytes.size() < 2) return "Unrecognized response";
        return String.format(Locale.US, "%.2f V", (bytes.get(0) * 256 + bytes.get(1)) / 1000.0);
    }
    /** SAE J1979 Mode 01 support bitmap. PID 20/40 is the continuation bit. */
    public static boolean mode01Advertises(String raw, int rangeStart, int pid) {
        if (unavailable(raw) || !hexResponse(raw) || pid < rangeStart + 1 || pid > rangeStart + 32) return false;
        List<Integer> b=bytes(raw); int h=findHeader(b,0x41,rangeStart);
        int p=h>=0?h+2:0; if(b.size()-p!=4)return false;
        long map=0; for(int i=0;i<4;i++)map=(map<<8)|b.get(p+i);
        return (map&(1L<<(32-(pid-rangeStart))))!=0;
    }
    /** Mode 01 PID 01; null means unavailable or structurally invalid. */
    public static final class MonitorStatus {
        public final boolean milOn;
        public final int storedDtcCount;
        public final String summary;
        private MonitorStatus(int a) {
            milOn=(a&0x80)!=0; storedDtcCount=a&0x7F;
            summary="ECU MIL "+(milOn?"ON":"OFF")+"; stored generic DTC count reported: "+storedDtcCount;
        }
    }
    public static MonitorStatus monitorStatus(String raw) {
        if (unavailable(raw) || !hexResponse(raw)) return null;
        List<Integer> b=bytes(raw);
        if (b.size()==6 && b.get(0)==0x41 && b.get(1)==0x01) return new MonitorStatus(b.get(2));
        if (b.size()==4) return new MonitorStatus(b.get(0));
        return null;
    }
    /** Safe, range-checked decoders for the allowlisted live Mode 01 PIDs. */
    public static String live(String pid, String raw) {
        if (unavailable(raw)) return "Not available";
        if (!hexResponse(raw)) return "Unrecognized response";
        List<Integer> b=bytes(raw); int wanted=Integer.parseInt(pid.substring(2),16);
        int h=findHeader(b,0x41,wanted); if(h>=0)b=b.subList(h+2,b.size());
        if (pid.equals("0105") || pid.equals("010F")) return one(b,-40,215,"°C",1.0,-40);
        if (pid.equals("0106") || pid.equals("0107")) return one(b,-100,100,"%",100.0/128,-100);
        if (pid.equals("010D")) return one(b,0,255,"km/h",1,0);
        if (pid.equals("010E")) return one(b,-64,63.75,"°",0.5,-64);
        if (pid.equals("0110")) { if(b.size()!=2)return "Unrecognized response"; double v=(b.get(0)*256+b.get(1))/100.0; return v<=655.35?String.format(Locale.US,"%.2f g/s",v):"Unrecognized response"; }
        if (pid.equals("0111")) return one(b,0,100,"%",100.0/255,0);
        if (pid.equals("0104")) return one(b,0,100,"%",100.0/255,0);
        if (pid.equals("010B")) return one(b,0,255,"kPa",1,0);
        if (pid.equals("012F")) return one(b,0,100,"%",100.0/255,0);
        if (pid.equals("0142")) { if(b.size()!=2)return "Unrecognized response"; return String.format(Locale.US,"%.2f V",(b.get(0)*256+b.get(1))/1000.0); }
        return "Unrecognized response";
    }
    private static String one(List<Integer>b,double lo,double hi,String unit,double scale,double offset){
        if(b.size()!=1)return "Unrecognized response"; double v=b.get(0)*scale+offset;
        return v<lo||v>hi?"Unrecognized response":String.format(Locale.US,"%.1f %s",v,unit);
    }
    /** Generic emissions only; no conclusion about enhanced/module diagnostics. */
    public static String dtcs(String raw) {
        return dtcs("03", raw);
    }
    public static String dtcs(String mode, String raw) {
        if (!BlueProtocol.allowedDtcMode(mode)) throw new IllegalArgumentException("Unsupported DTC category");
        String category="03".equals(mode)?"stored":"07".equals(mode)?"pending":"permanent";
        String empty="No "+category+" DTCs reported";
        String normalized=raw==null?"":raw.replace(">", "").trim();
        if (normalized.equalsIgnoreCase("NO DATA")) return "ECU/adapter returned NO DATA — no "+category+" generic DTC payload exposed";
        if (normalized.equalsIgnoreCase("TIMEOUT")) return "DTC request timed out/partial";
        if (unavailable(raw)) return "DTC request unavailable";
        StringBuilder out=new StringBuilder();
        boolean payload=false;
        for(String line:raw.replace(">", "").split("[\\r\\n]+")) {
            if(line.trim().isEmpty()) continue;
            if(!hexResponse(line)) return "Malformed DTC response";
            List<Integer> b=bytes(line);
            // Odd length denotes a service header; even length is header-stripped data.
            // This preserves valid stripped C03xx/C07xx/C10xx and C3Fxx words.
            if(b.size()==3 && b.get(0)==0x7F && b.get(1)==Integer.parseInt(mode,16)) return "DTC request rejected by ECU";
            int p=b.size()%2==1 && b.get(0)==(0x40+Integer.parseInt(mode,16))?1:0;
            if(b.size()-p<2 || (b.size()-p)%2!=0) return "Malformed DTC response";
            payload=true;
            for(;p<b.size();p+=2) { int a=b.get(p), c=b.get(p+1); if(a==0&&c==0) continue;
                char system="PCBU".charAt((a>>6)&3); String code=String.format(Locale.US,"%c%01X%03X",system,(a>>4)&3,((a&15)<<8)|c);
                if(out.length()>0)out.append(", "); out.append(code);
            }
        }
        if(!payload) return "Malformed DTC response";
        return out.length()==0?empty:out.toString();
    }
    /** Empty for no codes OR unavailable/malformed; use dtcs() for the conservative outcome. */
    public static List<String> dtcCodes(String mode, String raw) {
        String summary=dtcs(mode,raw);
        List<String> result=new ArrayList<>();
        if(summary.matches("[PCBU][0-3][0-9A-F]{3}(, [PCBU][0-3][0-9A-F]{3})*"))
            for(String code:summary.split(", ")) if(!result.contains(code)) result.add(code);
        return java.util.Collections.unmodifiableList(result);
    }
    private static boolean hexResponse(String raw) {
        return raw != null && raw.replace(">", " ").trim().matches("(?i)[0-9a-f]{2}(?:\\s+[0-9a-f]{2})*");
    }
    /** Require every continuation bit, not merely a cached downstream bitmap. */
    public static boolean advertisedLive(String first,String second,String third,int pid) {
        if(pid<=0x20) return mode01Advertises(first,0,pid);
        if(!mode01Advertises(first,0,0x20)) return false;
        if(pid<=0x40) return mode01Advertises(second,0x20,pid);
        return mode01Advertises(second,0x20,0x40) && mode01Advertises(third,0x40,pid);
    }
    /** Multi-record data cannot be declared complete when its count is unknown. */
    public static boolean needsExhaustiveRetry(int expected, int observed, boolean complete, boolean conflicts) {
        if (conflicts || !complete) return true;
        if (expected > 0) return observed != expected;
        return observed > 0;
    }
    /**
     * BlueDriver normally removes the 49 04 header.  On the tested 2005 Z33,
     * 0904 returned 01 31 43 46 30: record/count=01, ASCII identifier=1CF0.
     * Display only the bytes actually returned; NEVER fill in a presumed CF010.
     */
    public static String calibrationId(String raw) {
        if (unavailable(raw)) return "Not provided by ECU";
        List<Integer> b = bytes(raw);
        int header = findHeader(b, 0x49, 0x04);
        int start = header >= 0 ? header + 2 : 0;
        if (start >= b.size()) return "Unconfirmed (inspect raw response)";
        // ISO 9141 / K-Line Mode 09: first response-data byte may be a count.
        // Strip only a plausible 01..04 count when followed by ASCII digits/letters.
        int first = b.get(start);
        if (first >= 1 && first <= 4 && start + 1 < b.size()
                && isAsciiIdChar(b.get(start + 1))) start++;
        StringBuilder id = new StringBuilder();
        for (int i = start; i < b.size(); i++) {
            int v = b.get(i);
            if (v == 0 || v == 0xFF) continue; // optional padding
            if (!isAsciiIdChar(v)) return "Unconfirmed (inspect raw response)";
            id.append((char)v);
        }
        String result = id.toString().trim();
        if (result.length() < 4 || result.length() > 32) return "Unconfirmed (inspect raw response)";
        return result + " (reported; exact ROM unverified)";
    }
    private static boolean isAsciiIdChar(int b) {
        return (b >= '0' && b <= '9') || (b >= 'A' && b <= 'Z')
                || (b >= 'a' && b <= 'z') || b == '-' || b == '_';
    }
    /** Mode 09 / 03: CALID message count on legacy non-CAN protocols. */
    public static String calibrationMessageCount(String raw) {
        if (unavailable(raw)) return "Not provided by ECU";
        List<Integer> b = bytes(raw);
        int h = findHeader(b, 0x49, 0x03);
        int pos = h >= 0 ? h + 2 : 0;
        if (pos >= b.size() || b.size() != pos + 1) return "Unconfirmed (inspect raw response)";
        int count = b.get(pos);
        if (count < 1 || count > 32) return "Unexpected count: " + count;
        return count + " (reported; check against CALID response)";
    }
    public static String cvn(String raw) {
        if (unavailable(raw)) return "Not provided by ECU";
        List<Integer> bytes = bytes(raw);
        int i = findHeader(bytes, 0x49, 0x06);
        if (i < 0 || i + 6 >= bytes.size()) return "Unconfirmed (inspect raw response)";
        StringBuilder result = new StringBuilder();
        for (int j = i + 3; j < Math.min(i + 7, bytes.size()); j++) {
            result.append(String.format(Locale.US, "%02X", bytes.get(j)));
        }
        return result.toString();
    }
    private static int findHeader(List<Integer> d, int a, int b) {
        for (int i = 0; i + 1 < d.size(); i++) if (d.get(i) == a && d.get(i + 1) == b) return i;
        return -1;
    }
    /** Removes ELM frame labels such as 0: and 1:; accepts both spaced and compact hex. */
    static List<Integer> bytes(String raw) {
        String s = raw.toUpperCase(Locale.US).replace(">", " ").replaceAll("(?m)(?:^|\\s)[0-9A-F]:", " ").trim();
        List<Integer> result = new ArrayList<>();
        for (String part : s.split("\\s+")) {
            if (part.matches("[0-9A-F]{2,}" ) && part.length() % 2 == 0) {
                for (int i = 0; i < part.length(); i += 2) result.add(Integer.parseInt(part.substring(i, i + 2), 16));
            }
        }
        return result;
    }
    /** Checks a Mode 09 PID against the reported 0900 availability bitfield. */
    public static boolean mode09Advertises(String raw, int pid) {
        if (unavailable(raw) || pid < 1 || pid > 32) return false;
        List<Integer> b = bytes(raw);
        int h = findHeader(b, 0x49, 0x00);
        int pos = h >= 0 ? h + 2 : 0;
        // Tested BlueDriver strips service headers and returns '01 FF 00 00 00'.
        if (b.size() - pos == 5 && b.get(pos) == 1) pos++;
        if (b.size() - pos != 4) return false;
        long bitmap = 0;
        for (int i = 0; i < 4; i++) bitmap = (bitmap << 8) | b.get(pos + i);
        return (bitmap & (1L << (32 - pid))) != 0;
    }

    /**
     * Legacy Mode 09 / 04: one record is a sequence byte and four ASCII bytes.
     * Records may be delivered with or without 49 04 service headers; multiple
     * records can appear in one scanner answer. Never infer missing characters.
     */
    public static final class CalidAnalysis {
        public final int expectedCount;
        public final int observedCount;
        public final String missing;
        public final String displayedId;
        public final String fullIds;
        public final String detail;
        public final boolean conflicts;

        private CalidAnalysis(int expectedCount, int observedCount, String missing,
                              String displayedId, String fullIds, String detail, boolean conflicts) {
            this.expectedCount=expectedCount;
            this.observedCount=observedCount;
            this.missing=missing;
            this.displayedId=displayedId;
            this.fullIds=fullIds;
            this.detail=detail;
            this.conflicts=conflicts;
        }
    }
    public static int calidRecordCount(String raw) {
        java.util.TreeMap<Integer, byte[]> records = new java.util.TreeMap<>();
        addCalidRecords(records, new java.util.ArrayList<Integer>(), raw);
        return records.size();
    }
    public static CalidAnalysis analyzeCalid(String fast, String exhaustive, String reportedCount) {
        java.util.TreeMap<Integer, byte[]> records = new java.util.TreeMap<>();
        java.util.ArrayList<Integer> conflicts = new java.util.ArrayList<>();
        addCalidRecords(records, conflicts, fast);
        addCalidRecords(records, conflicts, exhaustive);
        int expected = parseCount(reportedCount);
        StringBuilder missing = new StringBuilder();
        if (expected > 0) {
            for (int i = 1; i <= expected; i++) {
                if (!records.containsKey(i)) {
                    if (missing.length() > 0) missing.append(", ");
                    missing.append(i);
                }
            }
        }
        StringBuilder full = new StringBuilder();
        boolean conflict = !conflicts.isEmpty();
        // Four 4-byte records form a 16-byte identifier on legacy K-Line systems.
        int limit = expected > 0 ? Math.min(expected, 32) :
                (records.isEmpty() ? 0 : Math.min(((records.lastKey()+3)/4)*4, 32));
        for (int base = 1; base <= limit; base += 4) {
            if (base + 3 > limit) break;
            if (!records.containsKey(base) || !records.containsKey(base+1) ||
                    !records.containsKey(base+2) || !records.containsKey(base+3)) continue;
            boolean groupConflict = false;
            for (int i=base; i<base+4; i++) if (conflicts.contains(i)) groupConflict=true;
            if (groupConflict) continue;
            StringBuilder id = new StringBuilder();
            for (int i=base; i<base+4; i++) {
                for (byte b : records.get(i)) {
                    int value = b & 0xFF;
                    if (value >= 0x20 && value <= 0x7E) id.append((char)value);
                    else if (value == 0 || value == 0xFF) id.append(' ');
                    else groupConflict = true;
                }
            }
            if (!groupConflict) {
                if (full.length()>0) full.append(" | ");
                full.append(id.toString().trim());
            }
        }
        String prefix = "";
        // Show only contiguous records from sequence 1; no fabricated suffixes.
        for (int i=1; i<=32 && records.containsKey(i) && !conflicts.contains(i); i++) {
            for (byte b : records.get(i)) {
                int v=b&0xFF;
                if (v>=0x20 && v<=0x7E) prefix+=(char)v;
            }
        }
        String displayed;
        if (records.isEmpty()) displayed = "No valid indexed CALID records returned";
        else if (full.length()>0 && missing.length()==0 && expected>0 && !conflict)
            displayed = full.toString() + " (complete messages; ROM not independently verified)";
        else if (full.length()>0 && missing.length()>0)
            displayed = full.toString() + " (complete observed ID; other CALID records missing)";
        else if (!prefix.isEmpty()) displayed = prefix + " (partial CALID; full ROM not verified)";
        else displayed = "Indexed fragments received; inspect raw responses";
        String detail = (expected>0 ? records.size()+" / "+expected : records.size()+" / unknown")
                + " distinct CALID record(s); missing sequence(s): "
                + (expected<=0 ? "unknown" : missing.length()==0 ? "none" : missing.toString())
                + (conflict ? "; conflicting sequence(s) "+conflicts.toString() : "")
                + ". Fast and exhaustive responses have been combined only where indexed data agrees.";
        return new CalidAnalysis(expected, records.size(), missing.toString(), displayed,
                full.toString(), detail, conflict);
    }
    private static int parseCount(String s) {
        if (s == null || unavailable(s)) return -1;
        List<Integer> b = bytes(s);
        int h=findHeader(b,0x49,0x03);
        int pos=h>=0?h+2:0;
        if (b.size()!=pos+1) return -1;
        int count=b.get(pos);
        return count>=1&&count<=32?count:-1;
    }
    private static void addCalidRecords(java.util.Map<Integer,byte[]> records,
                                        List<Integer> conflicts, String raw) {
        if (raw == null || unavailable(raw)) return;
        for (String line : raw.replace(">", "").split("\\r|\\n")) {
            // Avoid reading ELM console errors as hex data.
            List<Integer> b=bytes(line);
            if (b.isEmpty()) continue;
            int pos=0;
            while (pos < b.size()) {
                if (pos+1<b.size() && b.get(pos)==0x49 && b.get(pos+1)==0x04) pos+=2;
                if (pos+5>b.size()) break;
                int number=b.get(pos++);
                byte[] chunk = new byte[4];
                boolean valid=number>=1&&number<=32;
                for (int i=0;i<4;i++) {
                    int n=b.get(pos++);
                    chunk[i]=(byte)n;
                    if (!((n>=0x20&&n<=0x7E) || n==0 || n==0xFF)) valid=false;
                }
                if (!valid) break;
                byte[] old=records.putIfAbsent(number, chunk);
                if (old != null && !java.util.Arrays.equals(old,chunk) && !conflicts.contains(number))
                    conflicts.add(number);
            }
        }
    }


    /** Mode 09 count reports for legacy four-data-byte records (01=VIN, 05=CVN). */
    public static int mode09MessageCount(String raw, int infoPid) {
        if (infoPid != 1 && infoPid != 3 && infoPid != 5) return -1;
        if (raw == null || unavailable(raw)) return -1;
        List<Integer> b = bytes(raw);
        int h = findHeader(b, 0x49, infoPid);
        int pos = h >= 0 ? h + 2 : 0;
        if (b.size() != pos+1) return -1;
        int count = b.get(pos);
        return count > 0 && count <= 32 ? count : -1;
    }

    private static final class Records {
        final java.util.TreeMap<Integer, byte[]> records = new java.util.TreeMap<>();
        final java.util.TreeSet<Integer> conflicts = new java.util.TreeSet<>();
        int malformed;
    }
    /** Parse 4-byte indexed records, refusing unindexed, malformed or overlapping bytes. */
    private static void collect(Records out, String raw, int pid) {
        if (raw == null || unavailable(raw)) return;
        for (String line: raw.replace(">", "").split("\\r|\\n")) {
            List<Integer> b=bytes(line);
            if (b.isEmpty()) continue;
            int pos=0;
            while (pos+1<b.size() && b.get(pos)==0x49 && b.get(pos+1)==pid) pos+=2;
            if (b.size()-pos!=5) { out.malformed++; continue; }
            int sequence=b.get(pos);
            if (sequence < 1 || sequence>32) { out.malformed++; continue; }
            byte[] value=new byte[4];
            for (int i=0;i<4;i++) value[i]=(byte)(int)b.get(pos+1+i);
            byte[] prior=out.records.putIfAbsent(sequence,value);
            if (prior != null && !java.util.Arrays.equals(prior,value)) out.conflicts.add(sequence);
        }
    }
    private static String missingRecords(Records r, int expected) {
        if (expected < 1) return "unknown";
        StringBuilder s=new StringBuilder();
        for(int i=1;i<=expected;i++) if (!r.records.containsKey(i)) {
            if(s.length()>0)s.append(", ");
            s.append(i);
        }
        return s.length()==0 ? "none" : s.toString();
    }
    public static final class CvnAnalysis {
        public final int expected, observed;
        public final String values, detail;
        public final boolean complete, conflicts;
        private CvnAnalysis(int expected, int observed, String values, String detail,
                            boolean complete, boolean conflicts) {
            this.expected=expected;this.observed=observed;this.values=values;
            this.detail=detail;this.complete=complete;this.conflicts=conflicts;
        }
    }
    public static CvnAnalysis analyzeCvn(String fast, String exhaustive, String countRaw) {
        Records r=new Records();
        collect(r,fast,0x06);collect(r,exhaustive,0x06);
        int expected=mode09MessageCount(countRaw,5);
        StringBuilder values=new StringBuilder();
        for(java.util.Map.Entry<Integer,byte[]> item:r.records.entrySet()) {
            if (!r.conflicts.contains(item.getKey())) {
                if(values.length()>0)values.append(" | ");
                values.append('#').append(item.getKey()).append(": ");
                for(byte b:item.getValue()) values.append(String.format(Locale.US,"%02X",b&0xFF));
            }
        }
        boolean good = expected>0 && r.records.size()==expected && r.conflicts.isEmpty() && r.malformed==0;
        for (int i=1; i<=expected && good; i++) if (!r.records.containsKey(i)) good=false;
        // Even when a count is unavailable, CVNs may be decoded but cannot be declared validated.
        String detail=(expected>0 ? r.records.size()+" / "+expected : r.records.size()+" / unknown")
               + " indexed CVN record(s). Missing: "+ missingRecords(r,expected)
               + (r.conflicts.isEmpty() ? "" : "; conflicting sequences: "+r.conflicts)
               + (r.malformed==0 ? "" : "; malformed response lines: "+r.malformed)
               + (good?". Count agrees with records.":". Count/record validation incomplete.");
        return new CvnAnalysis(expected,r.records.size(),values.length()>0?values.toString():"No indexed CVNs returned",detail,good,!r.conflicts.isEmpty());
    }
    public static final class VinAnalysis {
        public final int expected, observed;
        public final String vin, detail;
        public final boolean complete;
        private VinAnalysis(int expected,int observed,String vin,String detail,boolean complete){
            this.expected=expected;this.observed=observed;this.vin=vin;this.detail=detail;this.complete=complete;
        }
    }
    public static VinAnalysis analyzeVin(String fast, String exhaustive, String countRaw) {
        Records r=new Records();
        collect(r,fast,0x02); collect(r,exhaustive,0x02);
        int expected=mode09MessageCount(countRaw,1);
        String vin="Not confirmed";
        boolean all = expected==5 && r.records.size()==5 && r.conflicts.isEmpty() && r.malformed==0;
        if (all) {
            // ISO 9141-2 uses 5 records x 4 bytes = 20, with 3 leading null bytes.
            java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();
            for(int i=1;i<=5;i++) {
                if(!r.records.containsKey(i)){all=false;break;}
                for(byte b:r.records.get(i))data.write(b&0xFF);
            }
            if(all){
                byte[] payload=data.toByteArray();
                int start=0,end=payload.length;
                while(start<end && ((payload[start]&0xFF)==0 || (payload[start]&0xFF)==0xFF)) start++;
                while(end>start && ((payload[end-1]&0xFF)==0 || (payload[end-1]&0xFF)==0xFF)) end--;
                String candidate=new String(payload,start,end-start,java.nio.charset.StandardCharsets.US_ASCII);
                if(candidate.matches("[A-HJ-NPR-Z0-9]{17}")) vin=candidate;
                else all=false;
            }
        }
        String detail=(expected>0 ? r.records.size()+" / "+expected : r.records.size()+" / unknown")
                + " VIN record(s). Missing: "+missingRecords(r,expected)
                + (r.conflicts.isEmpty()?"":"; conflicting sequences: "+r.conflicts)
                + (r.malformed==0?"":"; malformed lines: "+r.malformed)
                + (all?". Valid 17-character VIN reconstructed.":". VIN incomplete, unavailable, or unrecognized; do not infer missing characters.");
        return new VinAnalysis(expected,r.records.size(),vin,detail,all);
    }
    /** VIN final-byte diagnostics. Never fabricate a 17th VIN character or send it to the ECU. */
    public static final class VinByteAnalysis {
        public final String summary;
        public final int expectedRecords, observedRecords, validVinCharacters;
        public final int lastByte; // -1 when record 5 is absent
        public final boolean conflicts;
        private VinByteAnalysis(String summary, int expectedRecords, int observedRecords,
                int validVinCharacters, int lastByte, boolean conflicts) {
            this.summary=summary;this.expectedRecords=expectedRecords;
            this.observedRecords=observedRecords;this.validVinCharacters=validVinCharacters;
            this.lastByte=lastByte;this.conflicts=conflicts;
        }
    }
    private static boolean vinAscii(int value) {
        return (value>='A' && value<='H') || (value>='J' && value<='N') ||
                (value>='P' && value<='R') || (value>='S' && value<='Z') ||
                (value>='0' && value<='9');
    }
    private static String hexByte(int b) {
        return b<0 ? "unknown" : String.format(Locale.US,"0x%02X",b);
    }
    /**
     * Reports only lengths and the very last VIN-byte field. The incoming raw
     * payload is kept out of this analysis and exported reports for privacy.
     * 0x0D may be a literal bad byte OR an adapter text-framing artifact;
     * neither explanation may be inferred from this high-level PID reply.
     */
    public static VinByteAnalysis analyzeVinFinalByte(String fast, String exhaustive,
            String countRaw, String userExpectedLast) {
        Records r=new Records();
        collect(r,fast,0x02);collect(r,exhaustive,0x02);
        int expected=mode09MessageCount(countRaw,1);
        int valid=0,last=-1;
        if(r.records.containsKey(5) && !r.conflicts.contains(5))
            last=r.records.get(5)[3]&0xFF;
        for(int seq=1;seq<=5;seq++) {
            byte[] record=r.records.get(seq);
            if(record==null || r.conflicts.contains(seq))continue;
            for(int j=0;j<4;j++) {
                int absolute=(seq-1)*4+j;
                if(absolute < 3) continue; // 3 leading zero-pad bytes
                if(vinAscii(record[j]&0xFF)) valid++;
            }
        }
        StringBuilder summary=new StringBuilder();
        summary.append(r.records.size()).append(" / ")
                .append(expected>0?expected:"unknown")
                .append(" indexed VIN records; ").append(valid)
                .append(" / 17 valid VIN character bytes. ");
        if(last<0) summary.append("Record 5 terminal byte unavailable. ");
        else {
            summary.append("Record 5 fourth byte: ").append(hexByte(last));
            if(last==0x0D) summary.append(" (CR/control; could also reflect diagnostic framing)");
            else if(!vinAscii(last)) summary.append(" (not a permitted VIN character)");
            summary.append(". ");
        }
        String expectedChar=userExpectedLast==null?"":userExpectedLast.toUpperCase(Locale.US).trim();
        if(expectedChar.length()==1 && vinAscii(expectedChar.charAt(0))) {
            int expectedByte=expectedChar.charAt(0);
            summary.append("User-provided final character: '").append(expectedChar)
                    .append("' (").append(hexByte(expectedByte)).append("); ");
            if(last<0) summary.append("cannot compare: no terminal record byte. ");
            else summary.append(last==expectedByte?"matches recorded terminal byte. ":
                    "does not match recorded terminal byte. ");
        } else if(!expectedChar.isEmpty()) {
            summary.append("Expected character must be a single valid VIN character. ");
        }
        if(!r.conflicts.isEmpty())summary.append("Conflicting indexed records present. ");
        if(r.malformed>0)summary.append("Malformed response lines present. ");
        summary.append("No VIN registration, correction, or ECU write performed. ")
               .append("Raw OBD/adapter capture is needed to distinguish ECU contents from transport truncation.");
        return new VinByteAnalysis(summary.toString(),expected,r.records.size(),valid,last,!r.conflicts.isEmpty());
    }

    public static String maskedVin(String value) {
        if (value == null || !value.matches("[A-HJ-NPR-Z0-9]{17}")) return "Unavailable or unconfirmed";
        return "*************"+value.substring(value.length()-4);
    }
    /** Local metadata match only; the hardware ROM bytes have NOT been read or verified. */
    public static String romDefinitionMatch(CalidAnalysis data) {
        if (data.conflicts || data.expectedCount!=8 || data.observedCount!=8) return "No conclusive definition match (CALID records incomplete or conflicting)";
        for(String id:data.fullIds.split("\\s*\\|\\s*")) if(id.equals("1CF010"))
            return "CALID 1CF010 matches CF010 definition: 2005 USDM 350Z, SH7058 listed; transmission unknown. ROM binary NOT verified.";
        return "No matching local 350Z ROM definition for the reported CALID(s)";
    }

}
