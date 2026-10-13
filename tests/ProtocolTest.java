import com.z33eculab.BlueProtocol;
import com.z33eculab.ResponseParser;
import com.z33eculab.LivePollScheduler;
import com.z33eculab.CsvExport;
import com.z33eculab.DtcDescriptions;
import com.z33eculab.FreezeFrame;
import com.z33eculab.SampleRateTracker;

public class ProtocolTest {
  private static void eq(Object a, Object b) { if (!java.util.Objects.equals(a,b)) throw new AssertionError(a + " != " + b); }
  public static void main(String[] args) {
    eq("6A6B6F0D", BlueProtocol.hex(BlueProtocol.command("LMI")));
    eq("161F16120D", BlueProtocol.hex(BlueProtocol.command("0904")));
    eq("011000", BlueProtocol.hex(BlueProtocol.ack(16)));
    byte[] sample = new byte[]{0x69,0x75,0x67,0x76,0x6F,0x06,0x50,0x14,0x08,0x10,0x16,0x0D,0x69,0x6D,0x0D,0x3E};
    eq("OSAPI v2.60\rOK\r>", BlueProtocol.decoded(sample));
    eq("CF010 (reported; exact ROM unverified)", ResponseParser.calibrationId("49 04 01 43 46 30 31 30 00\r>"));
    eq("1CF0 (reported; exact ROM unverified)", ResponseParser.calibrationId("01 31 43 46 30 \r>"));
    eq("4 (reported; check against CALID response)", ResponseParser.calibrationMessageCount("04\r>"));
    eq("Not provided by ECU", ResponseParser.calibrationMessageCount("NO DATA\r>"));
    eq("Unconfirmed (inspect raw response)", ResponseParser.calibrationId("OK\r>"));
    eq("Not provided by ECU", ResponseParser.calibrationId("NO DATA\r>"));
    eq("800 RPM", ResponseParser.rpm("0C 80\r>"));
    eq("Unconfirmed (inspect raw response)", ResponseParser.cvn("49 06\r>"));
    eq("1234ABCD", ResponseParser.cvn("49 06 01 12 34 AB CD\r>"));
    if (BlueProtocol.allowedPid("2701")) throw new AssertionError("Unsafe commands allowed");
    for(String pid:new String[]{"0100","0101","0111","0104","010B","012F","0142","03","07","0A"}) eq(true,BlueProtocol.allowedPid(pid));
    for(String pid:FreezeFrame.REQUESTS) { eq(true,BlueProtocol.allowedFreezeFrame(pid)); eq(true,BlueProtocol.allowedPid(pid)); }
    eq(true,BlueProtocol.allowedFreezeFrame("020000"));
    for(String pid:new String[]{null,"02","0200","020001","020201","020600","020C01","020000\r","02000003"}) eq(false,BlueProtocol.allowedFreezeFrame(pid));
    for(String mode:new String[]{"03","07","0A"}) eq(true,BlueProtocol.allowedDtcMode(mode));
    for(String mode:new String[]{null,"","04","04\r","0400","0300","0700","0A00","0a"," 03","07 ","0A\r","03\n07","03\r04","0A;04","010C","08","09","0B","3","7","A"}) {
      eq(false,BlueProtocol.allowedDtcMode(mode));
      if(!"010C".equals(mode)) eq(false,BlueProtocol.allowedPid(mode));
    }
    for(String pid:new String[]{"04","0400","010","01010","0101 ","0101\r","0101\n03","0300","03\r04","2701","220000","3D00","1101"}) eq(false,BlueProtocol.allowedPid(pid));
    eq("complete",BlueProtocol.classifyResponse("41 0C 0C 80\r>",true));
    eq("NO DATA",BlueProtocol.classifyResponse("NO DATA\r>",true));
    eq("timeout/partial",BlueProtocol.classifyResponse("41 0C",false));
    eq("malformed",BlueProtocol.classifyResponse("OK",true));
    eq("malformed",BlueProtocol.classifyResponse("?\r>",true));
    // 0100: 05, 0C, 0D and continuation PID 20 advertised; 06 is not.
    eq(true,ResponseParser.mode01Advertises("41 00 08 18 00 01\r>",0,0x05));
    eq(true,ResponseParser.mode01Advertises("41 00 08 18 00 01\r>",0,0x0C));
    eq(false,ResponseParser.mode01Advertises("41 00 08 18 00 01\r>",0,0x06));
    eq(true,ResponseParser.mode01Advertises("41 00 08 18 00 01\r>",0,0x20));
    eq(false,ResponseParser.mode01Advertises("41 20 00 00 00 00\r>",0x20,0x40));
    eq("45.0 °C",ResponseParser.live("0105","41 05 55\r>"));
    eq("0.0 %",ResponseParser.live("0106","41 06 80\r>"));
    eq("40.0 km/h",ResponseParser.live("010D","41 0D 28\r>"));
    eq("0.0 °",ResponseParser.live("010E","41 0E 80\r>"));
    eq("1.00 g/s",ResponseParser.live("0110","41 10 00 64\r>"));
    eq("100.0 %",ResponseParser.live("0111","41 11 FF\r>"));
    eq("Unrecognized response",ResponseParser.live("0105","41 05\r>"));
    eq(true,ResponseParser.needsExhaustiveRetry(-1,1,true,false));
    LivePollScheduler scheduler=new LivePollScheduler(); boolean[] active=new boolean[LivePollScheduler.PIDS.length]; active[0]=active[1]=true;
    eq("010C",scheduler.mostOverdue(active,1000));
    scheduler.sampled("010C",1000);
    eq("010D",scheduler.mostOverdue(active,1000));
    scheduler.sampled("010D",1000);
    eq(null,scheduler.mostOverdue(active,1100));
    eq(Long.valueOf(150),Long.valueOf(scheduler.delayUntilNext(active,1100)));
    eq("010C",scheduler.mostOverdue(active,1250));
    scheduler.setTier("010C",0); eq(125L,scheduler.configuredPeriod("010C"));
    scheduler.setTier("010C",1); eq(250L,scheduler.configuredPeriod("010C"));
    scheduler.setTier("010C",2); eq(500L,scheduler.configuredPeriod("010C"));
    scheduler.reset(); eq(500L,scheduler.configuredPeriod("010C"));
    scheduler.sampled("010C",2000); active[1]=false;
    eq(null,scheduler.mostOverdue(active,2499)); eq("010C",scheduler.mostOverdue(active,2500));
    active[0]=false; eq(null,scheduler.mostOverdue(active,9999)); eq(-1L,scheduler.delayUntilNext(active,9999));
    try { scheduler.setTier("04",0); throw new AssertionError("Unsafe scheduler config"); } catch(IllegalArgumentException expected) {}
    try { scheduler.setTier("010C",3); throw new AssertionError("Invalid tier"); } catch(IllegalArgumentException expected) {}
    SampleRateTracker rates=new SampleRateTracker(); rates.sample("010C",1000); eq("rate —",rates.display("010C")); rates.sample("010C",1500); eq("2.0 Hz",rates.display("010C")); rates.sample("010C",2500); eq("1.6 Hz",rates.display("010C")); rates.reset(); eq("rate —",rates.display("010C"));
    eq("100.0 %",ResponseParser.live("0104","41 04 FF\r>"));
    eq("101.0 kPa",ResponseParser.live("010B","65\r>"));
    eq("0.0 %",ResponseParser.live("012F","41 2F 00\r>"));
    eq("12.60 V",ResponseParser.live("0142","41 42 31 38\r>"));
    eq("Unrecognized response",ResponseParser.live("0142","41 42 31\r>"));
    eq("Unrecognized response",ResponseParser.live("0110","41 10 00 64 FF\r>"));
    eq("Unrecognized response",ResponseParser.rpm("7F 01 12\r>"));
    eq("Unrecognized response",ResponseParser.live("0104","garbage FF\r>"));
    eq("Not available",ResponseParser.live("0142","NO DATA\r>"));
    String first="00 00 00 01", second="00 02 00 01", third="40 00 00 00";
    eq(true,ResponseParser.advertisedLive(first,second,third,0x2F));
    eq(true,ResponseParser.advertisedLive(first,second,third,0x40));
    eq(true,ResponseParser.advertisedLive(first,second,third,0x42));
    eq(false,ResponseParser.advertisedLive("00 00 00 00",second,third,0x42));
    eq(false,ResponseParser.advertisedLive(first,"00 02 00 00",third,0x42));
    eq(false,ResponseParser.advertisedLive(first,second,"NO DATA",0x42));
    eq(false,ResponseParser.mode01Advertises("garbage 00 00 00 01",0,0x20));
    ResponseParser.MonitorStatus milOn=ResponseParser.monitorStatus("41 01 83 00 00 00\r>");
    eq(true,milOn.milOn); eq(3,milOn.storedDtcCount); eq("ECU MIL ON; stored generic DTC count reported: 3",milOn.summary);
    ResponseParser.MonitorStatus milOff=ResponseParser.monitorStatus("02 00 00 00\r>");
    eq(false,milOff.milOn); eq(2,milOff.storedDtcCount);
    for(String value:new String[]{null,"NO DATA\r>","41 01 80 00 00\r>","41 02 80 00 00 00\r>","80 00 00","garbage"}) eq(null,ResponseParser.monitorStatus(value));
    eq("P0001, P0133, C0123, B2123, U3123",ResponseParser.dtcs("43 00 01 01 33 41 23 A1 23 F1 23\r>"));
    eq("P0001",ResponseParser.dtcs("00 01 00 00\r>"));
    // BlueDriver headers-off replies make 43 00 a valid stripped C0300 word.
    eq("C0300",ResponseParser.dtcs("43 00\r>"));
    eq("P0133, U0100",ResponseParser.dtcs("43 01 33 00 00\r43 C1 00 00 00\r>"));
    eq("No stored DTCs reported",ResponseParser.dtcs("43 00 00\r>"));
    for(String value:new String[]{"43","01 33 00","garbage 01 33","OK",">"}) eq("Malformed DTC response",ResponseParser.dtcs(value));
    eq("ECU/adapter returned NO DATA — no stored generic DTC payload exposed",ResponseParser.dtcs("NO DATA\r>"));
    eq("DTC request unavailable",ResponseParser.dtcs("BUS ERROR\r>"));
    eq("DTC request rejected by ECU",ResponseParser.dtcs("7F 03 11\r>"));
    String[] modes={"03","07","0A"}, headers={"43","47","4A"}, categories={"stored","pending","permanent"};
    for(int i=0;i<modes.length;i++) {
      String mode=modes[i],header=headers[i],empty="No "+categories[i]+" DTCs reported";
      eq(empty,ResponseParser.dtcs(mode,header+" 00 00 00 00\r>"));
      eq(empty,ResponseParser.dtcs(mode,"00 00\r>"));
      eq("ECU/adapter returned NO DATA — no "+categories[i]+" generic DTC payload exposed",ResponseParser.dtcs(mode,"no data\r>"));
      eq("P0001, P0133, C0123, B2123, U3123",ResponseParser.dtcs(mode,header+" 00 01 01 33 41 23 A1 23 F1 23\r>"));
      eq("P0133, U0100",ResponseParser.dtcs(mode,header+" 01 33 00 00\r"+header+" C1 00 00 00\r>"));
      eq("P0133, U0100",ResponseParser.dtcs(mode,"01 33 00 00\rC1 00 00 00\r>"));
      eq("C0301, C0702, C0A03, C3F04",ResponseParser.dtcs(mode,"43 01 47 02 4A 03 7F 04\r>"));
      eq("DTC request rejected by ECU",ResponseParser.dtcs(mode,"7F "+mode+" 11\r>"));
      eq("DTC request timed out/partial",ResponseParser.dtcs(mode,"TIMEOUT\r>"));
      for(String value:new String[]{null,"","BUS ERROR\r>","STOPPED\r>","?\r>"}) eq("DTC request unavailable",ResponseParser.dtcs(mode,value));
      for(String value:new String[]{header,"01 33 00","garbage 01 33","OK",">","7F "+mode+" 1","43 01 33\rgarbage"}) eq("Malformed DTC response",ResponseParser.dtcs(mode,value));
      eq("Malformed DTC response",ResponseParser.dtcs(mode,headers[(i+1)%3]+" 01 33\r>"));
    }
    try { ResponseParser.dtcs("04","00 00"); throw new AssertionError("Unsafe DTC mode"); } catch(IllegalArgumentException expected) {}
    eq(java.util.Arrays.asList("P0301","P0302"),ResponseParser.dtcCodes("03","43 03 01 03 02\r>"));
    eq(java.util.Collections.emptyList(),ResponseParser.dtcCodes("03","NO DATA\r>"));
    eq("Random/multiple cylinder misfire detected",DtcDescriptions.describe("P0300"));
    eq("Cylinder 6 misfire detected",DtcDescriptions.describe("P0306"));
    eq("System too lean (bank 2)",DtcDescriptions.describe("P0174"));
    eq("Catalyst system efficiency below threshold (bank 1)",DtcDescriptions.describe("P0420"));
    eq("O2 sensor circuit slow response (bank 1, sensor 1)",DtcDescriptions.describe("P0133"));
    eq("Evaporative emission system leak detected (small leak)",DtcDescriptions.describe("P0442"));
    eq("Evaporative emission system purge control valve circuit shorted",DtcDescriptions.describe("P0445"));
    eq("Evaporative emission system leak detected (gross leak)",DtcDescriptions.describe("P0455"));
    eq("Transmission control system malfunction",DtcDescriptions.describe("P0700"));
    eq("Left front wheel speed sensor circuit",DtcDescriptions.describe("C0035"));
    eq("Driver frontal stage 1 deployment control",DtcDescriptions.describe("B0001"));
    eq("Lost communication with ECM/PCM",DtcDescriptions.describe("U0100"));
    eq("Description not bundled",DtcDescriptions.describe("P1000"));
    eq("Description not bundled",DtcDescriptions.describe("C1123"));
    eq("Description not bundled",DtcDescriptions.describe("P03A0"));
    eq("Description not bundled",DtcDescriptions.describe(null));
    eq("Description not bundled",DtcDescriptions.describe("P030"));
    eq(true,FreezeFrame.validSupport("00 FF FF FF FF\r>"));
    eq(true,FreezeFrame.validSupport("42 00 00 FF FF FF FF\r>"));
    eq(true,FreezeFrame.advertises("00 FF FF FF FF\r>","020C00"));
    eq(false,FreezeFrame.advertises("00 00 00 00 00\r>","020C00"));
    eq("P0301 — Cylinder 1 misfire detected",FreezeFrame.value("020200","00 03 01\r>"));
    eq("100.0 %",FreezeFrame.value("020400","42 04 00 FF\r>"));
    eq("800 RPM",FreezeFrame.value("020C00","00 0C 80\r>"));
    eq("NO DATA — frame unavailable through this path",FreezeFrame.value("020500","NO DATA\r>"));
    eq("Unavailable or malformed frame-00 response",FreezeFrame.value("020400","01 FF\r>"));
    eq("Unavailable or malformed frame-00 response",FreezeFrame.value("020400","FF\r>"));
    eq("\"a,b\"",CsvExport.quote("a,b"));
    eq("\"a\"\"b\nline\"",CsvExport.quote("a\"b\nline"));
    eq("\"\"",CsvExport.quote(null));
    java.util.TimeZone oldZone=java.util.TimeZone.getDefault();
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("GMT+09:00"));
    eq("1970-01-01T00:00:00.000Z",CsvExport.utcTimestamp(0));
    java.util.TimeZone.setDefault(oldZone);
    eq(true,ResponseParser.needsExhaustiveRetry(8,1,true,false));
    eq(false,ResponseParser.needsExhaustiveRetry(8,8,true,false));
    if (!BlueProtocol.allowedPid("0904") || !BlueProtocol.allowedPid("0903")) throw new AssertionError("Mode 09 request blocked");
    eq(true, ResponseParser.mode09Advertises("01 FF 00 00 00\r>", 6));
    eq(false, ResponseParser.mode09Advertises("01 FF 00 00 00\r>", 9));
    eq(1, ResponseParser.calidRecordCount("01 31 43 46 30\r>"));
    eq(2, ResponseParser.calidRecordCount("01 31 43 46 30\r02 31 30 20 20\r>"));
    ResponseParser.CalidAnalysis partial = ResponseParser.analyzeCalid(
            "01 31 43 46 30\r>", "01 31 43 46 30\r>", "08\r>");
    eq(8, partial.expectedCount);
    eq(1, partial.observedCount);
    eq("1CF0 (partial CALID; full ROM not verified)", partial.displayedId);
    eq("2, 3, 4, 5, 6, 7, 8", partial.missing);
    ResponseParser.CalidAnalysis full = ResponseParser.analyzeCalid(
            "01 31 43 46 30\r02 31 30 20 20\r>",
            "49 04 03 20 20 20 20\r49 04 04 20 20 20 20\r>", "04\r>");
    eq(4, full.observedCount);
    eq("1CF010", full.fullIds);
    ResponseParser.CalidAnalysis oneOfTwo = ResponseParser.analyzeCalid(
            "01 31 43 46 30\r02 31 30 20 20\r03 20 20 20 20\r04 20 20 20 20\r>",
            "NO DATA\r>", "08\r>");
    eq("1CF010 (complete observed ID; other CALID records missing)", oneOfTwo.displayedId);
    eq("5, 6, 7, 8", oneOfTwo.missing);
    eq("1CF010 (complete messages; ROM not independently verified)", full.displayedId);
    eq("", full.missing);
    ResponseParser.CalidAnalysis conflict = ResponseParser.analyzeCalid(
            "01 31 43 46 30\r>", "01 31 43 46 31\r>", "08\r>");
    eq(true, conflict.conflicts);
    eq(1, conflict.observedCount);
    eq(true, conflict.detail.contains("conflicting sequence"));
    eq(0, ResponseParser.calidRecordCount("NO DATA\r>"));

    // Actual 2005 Z33 messages obtained under LMX1.
    String calidFull="01 31 43 46 30\r02 31 30 00 00\r03 00 00 00 00\r04 00 00 00 00\r"
            +"05 52 58 31 31\r06 31 35 00 00\r07 00 00 00 00\r08 00 00 00 00\r>";
    ResponseParser.CalidAnalysis actual=ResponseParser.analyzeCalid("01 31 43 46 30\r>",calidFull,"08\r>");
    eq("1CF010 | RX1115",actual.fullIds);
    eq(8,actual.observedCount);
    eq(true,ResponseParser.romDefinitionMatch(actual).contains("1CF010 matches CF010"));
    eq(-1,ResponseParser.mode09MessageCount("NO DATA\r>",5));
    eq(2,ResponseParser.mode09MessageCount("02\r>",5));
    eq(5,ResponseParser.mode09MessageCount("49 01 05\r>",1));
    eq(-1,ResponseParser.mode09MessageCount("02 03\r>",5));
    String cvns="01 0E 76 95 09\r02 00 00 61 69\r>";
    ResponseParser.CvnAnalysis actualCvn=ResponseParser.analyzeCvn("NO DATA\r>",cvns,"02\r>");
    eq("#1: 0E769509 | #2: 00006169",actualCvn.values);
    eq(true,actualCvn.complete);
    eq(2,actualCvn.observed);
    eq(false,ResponseParser.analyzeCvn("NO DATA\r>",cvns,"03\r>").complete);
    eq(true,ResponseParser.analyzeCvn("01 0E 76 95 09\r>","01 0F 76 95 09\r>","01\r>").conflicts);
    eq(false,ResponseParser.analyzeCvn("NO DATA\r>","01 0E 76 95 09\r>","02\r>").complete);
    eq(false,ResponseParser.analyzeCvn("NO DATA\r>","01 0E 76 95 09\r03 00 00 61 69\r>","02\r>").complete);
    // VIN in SAE J1979 legacy ISO 9141-2: 17 ASCII chars left-padded with 3 nulls.
    String sampleVin="1HGCM82633A004352";
    byte[] vinRaw=new byte[20];
    byte[] ascii=sampleVin.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    System.arraycopy(ascii,0,vinRaw,3,17);
    StringBuilder vinMessages=new StringBuilder();
    for(int record=1;record<=5;record++) {
        vinMessages.append(String.format("%02X",record));
        for(int i=0;i<4;i++) vinMessages.append(String.format(" %02X",vinRaw[4*(record-1)+i]&0xFF));
        vinMessages.append('\r');
    }
    vinMessages.append('>');
    ResponseParser.VinAnalysis validVin=ResponseParser.analyzeVin("NO DATA\r>",vinMessages.toString(),"05\r>");
    eq(true,validVin.complete);
    eq(sampleVin,validVin.vin);
    eq("*************4352",ResponseParser.maskedVin(validVin.vin));
    eq(false,ResponseParser.analyzeVin("NO DATA\r>",vinMessages.toString(),"04\r>").complete);
    eq(false,ResponseParser.analyzeVin("NO DATA\r>","01 00 00 00 31\r>","05\r>").complete);
    eq("Unavailable or unconfirmed",ResponseParser.maskedVin("bad"));
    if(!BlueProtocol.allowedPid("0901") || !BlueProtocol.allowedPid("0902") || !BlueProtocol.allowedPid("0905"))
      throw new AssertionError("Safe Mode 09 info PIDs blocked");
    if(BlueProtocol.allowedPid("2701") || BlueProtocol.allowedPid("0907") || BlueProtocol.allowedPid("1003"))
      throw new AssertionError("Unsafe or unimplemented command permitted");
    // Synthetic surrogate VIN: same final-byte phenomenon, no actual vehicle VIN in source.
    byte[] damagedVinRaw=vinRaw.clone();
    damagedVinRaw[19]=0x0D;
    StringBuilder damagedMessages=new StringBuilder();
    for(int record=1; record<=5;record++) {
        damagedMessages.append(String.format("%02X",record));
        for(int i=0;i<4;i++)
            damagedMessages.append(String.format(" %02X",damagedVinRaw[4*(record-1)+i]&0xFF));
        damagedMessages.append('\r');
    }
    damagedMessages.append('>');
    String donorVin=damagedMessages.toString();
    ResponseParser.VinByteAnalysis donorCheck=ResponseParser.analyzeVinFinalByte("NO DATA\r>",donorVin,"05\r>","5");
    eq(5,donorCheck.expectedRecords);
    eq(5,donorCheck.observedRecords);
    eq(16,donorCheck.validVinCharacters);
    eq(0x0D,donorCheck.lastByte);
    eq(true,donorCheck.summary.contains("0x0D (CR/control"));
    eq(true,donorCheck.summary.contains("0x35"));
    eq(true,donorCheck.summary.contains("does not match"));
    eq(false,donorCheck.summary.contains("1HGCM826")); // No VIN in diagnostic summary
    ResponseParser.VinByteAnalysis validByte=ResponseParser.analyzeVinFinalByte("NO DATA\r>",vinMessages.toString(),"05\r>","2");
    eq(17,validByte.validVinCharacters);
    eq(0x32,validByte.lastByte);
    eq(true,validByte.summary.contains("matches recorded terminal byte"));
    eq(-1,ResponseParser.analyzeVinFinalByte("NO DATA\r>","NO DATA\r>","05\r>","").lastByte);
    eq(true,ResponseParser.analyzeVinFinalByte("NO DATA\r>",donorVin,"05\r>","abc").summary.contains("Expected character must"));
    System.out.println("All v0.7.0 read-only protocol checks passed.");
  }
}
