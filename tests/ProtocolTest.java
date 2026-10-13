import com.z33eculab.BlueProtocol;
import com.z33eculab.ResponseParser;
import com.z33eculab.LivePollScheduler;

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
    if (!BlueProtocol.allowedPid("0100") || !BlueProtocol.allowedPid("0111") || BlueProtocol.allowedPid("0142"))
      throw new AssertionError("v0.1.7 safe Mode 01 allowlist incorrect");
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
    LivePollScheduler scheduler=new LivePollScheduler(); boolean[] active={true,true,false,false,false,false,false,false,false};
    eq("010C",scheduler.mostOverdue(active,1000));
    scheduler.sampled("010C",1000);
    eq("010D",scheduler.mostOverdue(active,1000));
    scheduler.sampled("010D",1000);
    eq(null,scheduler.mostOverdue(active,1100));
    eq(Long.valueOf(150),Long.valueOf(scheduler.delayUntilNext(active,1100)));
    eq("010C",scheduler.mostOverdue(active,1250));
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
    System.out.println("All v0.2.0 read-only protocol checks passed.");
  }
}
