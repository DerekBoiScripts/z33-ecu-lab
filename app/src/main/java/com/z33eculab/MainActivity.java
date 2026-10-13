package com.z33eculab;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.util.Locale;

/** Simple phone-first diagnostic UI with no third-party dependencies or network usage. */
public class MainActivity extends Activity implements BlueDriverBle.Listener {
    private static final int BG = 0xFF0B1220, PANEL = 0xFF172337, SUBPANEL = 0xFF111D30;
    private static final int TEXT = 0xFFF2F6FB, MUTED = 0xFF97A9C0, ACCENT = 0xFF4BE6C7;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private BlueDriverBle ble;
    private Button connect, read, disconnect, share, save, saveJson, vinCompare, startLive, stopLive;
    private EditText expectedVinLast;
    private TextView status, scanState, adapterSummary, lmxStatus, rpm, coolant, stft, ltft, speed, timing, intake, maf, throttle, calid, calidCount, calidCoverage, timingComparison, cvn, cvnCoverage, cvnCount, vin, vinCoverage, vinCount, vinByte, protocol, romMatch, mode09, raw, log, liveStatus, liveStats;
    private ScrollView logScroller;
    private final StringBuilder trace = new StringBuilder();
    private final StringBuilder report = new StringBuilder();
    private boolean scanBusy;
    private boolean liveRunning, liveInFlight;
    private final LivePollScheduler liveScheduler=new LivePollScheduler();
    private final Map<String,String> liveSamples=new HashMap<>();
    private final Map<String,Long> liveSampleTimes=new HashMap<>();
    private final ArrayDeque<Long> liveRtts=new ArrayDeque<>();
    private long liveStarted, liveCount;
    private String liveLast="None";
    private final Runnable liveAgeTicker=new Runnable() { @Override public void run() { if(liveRunning) { refreshLiveAges(); ui.postDelayed(this,250); } } };
    private final Runnable livePollTicker=this::nextLive;
    private String fastCalidRaw = "", slowCalidRaw = "", countRaw = "", calidCountExhaustiveRaw = "", supportRaw = "";
    private String mode01_00="", mode01_20="";
    private final StringBuilder transportReport = new StringBuilder();
    private final ArrayList<BlueDriverBle.CommandResult> commandResults = new ArrayList<>();
    private String fastCvnRaw = "", slowCvnRaw = "";
    private String vinCountRaw="", vinCountExhaustiveRaw="", fastVinRaw="", slowVinRaw="", cvnCountRaw="", cvnCountExhaustiveRaw="";
    private String detectedProtocol="Not detected";
    private String adapterFirmware="Not detected", lmx0Restore="not attempted";
    private static final int REQUEST_SAVE_REPORT=504;
    private static final int REQUEST_SAVE_JSON=505;
    private boolean fastCalidComplete, slowCalidComplete, fastCvnComplete, slowCvnComplete;
    private boolean comparisonAttempted;
    private boolean retryCalidCount, retryCvnCount, retryCalid, retryCvn, retryVin;
    private FrameLayout tabContent;
    private Button[] tabs;

    private int scanIndex;
    private static final String[] PIDS = {"0100","0120","0140","0105","0106","0107","010C","010D","010E","010F","0110","0111","0900","0901","0902","0903","0904","0905","0906"};
    private static final String[] PID_LABELS = {"Supported live PIDs 01-20","Supported live PIDs 21-40","Supported live PIDs 41-60","Coolant","STFT bank 1","LTFT bank 1","Engine RPM","Speed","Timing advance","Intake temperature","MAF","Throttle","Supported Mode 09","VIN message count","VIN","CALID message count","Calibration ID","CVN message count","Calibration verification"};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        buildScreen();
        ble = new BlueDriverBle(this, this);
        addLog("Launch. Explicit read-only Mode 01 live-data and Mode 09 identification allowlist enabled.");
        addLog("ECU candidate: 2005 350Z 5AT / MEC61-530 D1. Installed CALID unverified.");
    }
    private int dp(int value) { return (int)(getResources().getDisplayMetrics().density * value + 0.5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLineSpacing(dp(3), 1.0f); return t;
    }
    private void space(LinearLayout parent, int d) { View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(1, dp(d))); }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout p = vertical(); p.setPadding(dp(18), dp(16), dp(18), dp(17));
        p.setBackground(shape(PANEL, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(13); parent.addView(p, lp); return p;
    }
    private Button button(String title, int color) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(color == ACCENT ? BG : TEXT); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackgroundTintList(ColorStateList.valueOf(color)); b.setMinHeight(dp(52));
        return b;
    }
    private TextView valueLine(LinearLayout parent, String hint) {
        TextView v = text(hint, 19, TEXT, true); v.setPadding(0, dp(4), 0, dp(10)); parent.addView(v);
        return v;
    }
    private void caption(LinearLayout parent, String name) {
        TextView v = text(name.toUpperCase(Locale.US), 11, MUTED, true);
        v.setLetterSpacing(0.08f); parent.addView(v);
    }
    private ScrollView page() { ScrollView s=new ScrollView(this); s.setFillViewport(true); LinearLayout p=vertical(); p.setPadding(dp(16),dp(16),dp(16),dp(16)); s.addView(p); return s; }
    private LinearLayout pageBody(ScrollView s) { return (LinearLayout)s.getChildAt(0); }
    private TextView liveValue(LinearLayout p, String label) { LinearLayout c=card(p); caption(c,label); return valueLine(c,"Waiting for snapshot"); }
    private void applySystemInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left=safe.left; top=safe.top; right=safe.right; bottom=safe.bottom;
            } else {
                left=insets.getSystemWindowInsetLeft(); top=insets.getSystemWindowInsetTop();
                right=insets.getSystemWindowInsetRight(); bottom=insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout()!=null) {
                    top=Math.max(top,insets.getDisplayCutout().getSafeInsetTop());
                    left=Math.max(left,insets.getDisplayCutout().getSafeInsetLeft());
                    right=Math.max(right,insets.getDisplayCutout().getSafeInsetRight());
                }
            }
            view.setPadding(left,top,right,bottom);
            return insets;
        });
        root.requestApplyInsets();
    }
    private void buildScreen() {
        LinearLayout root=vertical(); root.setBackgroundColor(BG);
        applySystemInsets(root);
        LinearLayout header=vertical(); header.setPadding(dp(20),dp(16),dp(20),dp(12)); header.setBackgroundColor(SUBPANEL); root.addView(header);
        TextView brand=text("Z33 ECU LAB  •  READ-ONLY",12,ACCENT,true); brand.setLetterSpacing(.1f); header.addView(brand);
        status=text("Not connected",17,TEXT,true); status.setPadding(0,dp(5),0,0); header.addView(status);
        tabContent=new FrameLayout(this); root.addView(tabContent,new LinearLayout.LayoutParams(-1,0,1));

        ScrollView dashboard=page(); LinearLayout dash=pageBody(dashboard);
        LinearLayout connection=card(dash); caption(connection,"Scanner connection");
        scanState=valueLine(connection,"Ready for a read-only scan");
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        connect=button("Connect",ACCENT); read=button("Scan",0xFF355A76);
        actions.addView(connect,new LinearLayout.LayoutParams(0,dp(54),1)); actions.addView(read,new LinearLayout.LayoutParams(0,dp(54),1)); connection.addView(actions);
        space(connection,8); disconnect=button("Disconnect",0xFF293C50); connection.addView(disconnect,new LinearLayout.LayoutParams(-1,dp(48))); read.setEnabled(false);
        LinearLayout adapter=card(dash); caption(adapter,"Adapter / protocol"); adapterSummary=valueLine(adapter,"Adapter not detected"); protocol=valueLine(adapter,"Protocol not detected");
        LinearLayout completion=card(dash); caption(completion,"Last scan"); timingComparison=valueLine(completion,"No scan completed"); lmxStatus=text("LMX0 restore: not attempted",13,MUTED,false); completion.addView(lmxStatus);
        LinearLayout safety=card(dash); caption(safety,"Safety"); safety.addView(text("Generic read-only Mode 01 and Mode 09 requests only. No programming, memory access, or arbitrary commands.",13,MUTED,false));

        ScrollView live=page(); LinearLayout liveBody=pageBody(live); caption(liveBody,"Live data • read-only • Park / stationary safety");
        LinearLayout liveControls=new LinearLayout(this); liveControls.setOrientation(LinearLayout.HORIZONTAL); startLive=button("Start Live",ACCENT); stopLive=button("Stop Live",0xFF293C50); liveControls.addView(startLive,new LinearLayout.LayoutParams(0,dp(52),1)); liveControls.addView(stopLive,new LinearLayout.LayoutParams(0,dp(52),1)); liveBody.addView(liveControls); stopLive.setEnabled(false);
        liveStatus=valueLine(liveBody,"Run Scan first to load Mode 01 support bitmaps"); liveStats=text("Samples: 0/s • RTT: — • Active PIDs: 0 • Last: None",12,MUTED,false); liveBody.addView(liveStats); space(liveBody,10);
        coolant=liveValue(liveBody,"Coolant"); stft=liveValue(liveBody,"STFT B1"); ltft=liveValue(liveBody,"LTFT B1"); rpm=liveValue(liveBody,"RPM"); speed=liveValue(liveBody,"Speed"); timing=liveValue(liveBody,"Timing"); intake=liveValue(liveBody,"Intake temperature"); maf=liveValue(liveBody,"MAF"); throttle=liveValue(liveBody,"Throttle");

        ScrollView identity=page(); LinearLayout id=pageBody(identity);
        LinearLayout ids=card(id); caption(ids,"ECU identity"); caption(ids,"Calibration ID (09/04)"); calid=valueLine(ids,"Not read yet"); caption(ids,"CALID count / coverage"); calidCount=valueLine(ids,"Not read yet"); calidCoverage=valueLine(ids,"Not read yet"); caption(ids,"ROM definition"); romMatch=valueLine(ids,"Not checked yet");
        LinearLayout checks=card(id); caption(checks,"Verification"); caption(checks,"CVN (09/06)"); cvn=valueLine(checks,"Not read yet"); caption(checks,"CVN count / coverage"); cvnCount=valueLine(checks,"Not read yet"); cvnCoverage=valueLine(checks,"Not read yet"); caption(checks,"Mode 09 support"); mode09=valueLine(checks,"Not read yet");
        LinearLayout vinCard=card(id); caption(vinCard,"VIN privacy / final-byte inspection"); vin=valueLine(vinCard,"Not read yet"); vinCount=valueLine(vinCard,"Count unavailable"); vinCoverage=valueLine(vinCard,"Not read yet"); vinByte=valueLine(vinCard,"Awaiting VIN diagnostic response"); vinCard.addView(text("Optional: compare only one final VIN character. Full VIN is not exported.",12,MUTED,false)); expectedVinLast=new EditText(this); expectedVinLast.setSingleLine(true); expectedVinLast.setTextColor(TEXT); expectedVinLast.setHintTextColor(MUTED); expectedVinLast.setHint("Expected final character"); expectedVinLast.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)}); expectedVinLast.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS); vinCard.addView(expectedVinLast,new LinearLayout.LayoutParams(-1,dp(52))); vinCompare=button("Compare final VIN byte",0xFF355A76); vinCard.addView(vinCompare,new LinearLayout.LayoutParams(-1,dp(48)));

        ScrollView research=page(); LinearLayout researchBody=pageBody(research);
        LinearLayout exports=card(researchBody); caption(exports,"Sanitized exports"); share=button("Share report (VIN masked)",0xFF2A4259); exports.addView(share); space(exports,8); save=button("Save report .txt",0xFF2A4259); exports.addView(save); space(exports,8); saveJson=button("Save report .json",0xFF2A4259); exports.addView(saveJson);
        LinearLayout researchSafety=card(researchBody); caption(researchSafety,"Project / ROM-access safety"); researchSafety.addView(text("BlueDriver identification is not a ROM backup. No ROM extraction, kernel upload, security access, flash, fuel, timing, limiter, or VIN-write functions are present.",13,MUTED,false));
        LinearLayout rawCard=card(researchBody); caption(rawCard,"Raw responses"); raw=text("Waiting for a diagnostic session.",12,0xFFC4EDE3,false); raw.setTypeface(Typeface.MONOSPACE); raw.setTextIsSelectable(true); rawCard.addView(raw);
        LinearLayout console=card(researchBody); caption(console,"BLE log"); logScroller=new ScrollView(this); logScroller.setBackground(shape(SUBPANEL,10)); log=text("",11,0xFFC5D5E7,false); log.setTypeface(Typeface.MONOSPACE); log.setTextIsSelectable(true); log.setPadding(dp(12),dp(10),dp(12),dp(10)); logScroller.addView(log); console.addView(logScroller,new LinearLayout.LayoutParams(-1,dp(240)));
        tabContent.addView(dashboard); tabContent.addView(live); tabContent.addView(identity); tabContent.addView(research);
        LinearLayout nav=new LinearLayout(this); nav.setPadding(dp(6),dp(7),dp(6),dp(8)); nav.setBackgroundColor(SUBPANEL); String[] names={"Dashboard","Live Data","ECU Identity","Research / Logs"}; tabs=new Button[4]; for(int i=0;i<4;i++){ final int index=i; tabs[i]=button(names[i],i==0?ACCENT:0xFF293C50); tabs[i].setTextSize(11); nav.addView(tabs[i],new LinearLayout.LayoutParams(0,dp(52),1)); tabs[i].setOnClickListener(v->showTab(index)); } root.addView(nav);
        setContentView(root); showTab(0);
        connect.setOnClickListener(v -> requestThenConnect());
        disconnect.setOnClickListener(v -> { stopLive("Stopped before disconnect"); if (ble != null) ble.close(); connect.setEnabled(true); status.setText("Disconnected"); scanState.setText("Disconnected"); addLog("User disconnected."); });
        read.setOnClickListener(v -> runIdentification());
        startLive.setOnClickListener(v -> startLive());
        stopLive.setOnClickListener(v -> stopLive("Stopped by user"));
        vinCompare.setOnClickListener(v -> updateVin());
        share.setOnClickListener(v -> shareLog());
        save.setOnClickListener(v -> saveReport());
        saveJson.setOnClickListener(v -> saveJsonReport());
    }
    private void showTab(int selected) {
        for (int i=0;i<tabContent.getChildCount();i++) tabContent.getChildAt(i).setVisibility(i==selected?View.VISIBLE:View.GONE);
        for (int i=0;i<tabs.length;i++) { boolean active=i==selected; tabs[i].setBackgroundTintList(ColorStateList.valueOf(active?ACCENT:0xFF293C50)); tabs[i].setTextColor(active?BG:TEXT); }
    }
    private void requestThenConnect() {
        BluetoothManager bm = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null) { toast("Bluetooth LE unavailable"); return; }
        if (!adapter.isEnabled()) { toast("Turn Bluetooth on and retry"); return; }
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 21);
                return;
            }
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 21);
            return;
        }
        connect.setEnabled(false); read.setEnabled(false); ble.connect();
    }
    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req != 21) return;
        boolean all = grants.length > 0;
        for (int grant : grants) if (grant != PackageManager.PERMISSION_GRANTED) all = false;
        if (all) requestThenConnect(); else toast("Bluetooth permissions are required");
    }
    private void runIdentification() {
        if (scanBusy || ble == null || !ble.isReady()) return;
        stopLive("Stopped for identity scan");
        scanBusy = true; scanIndex = 0; report.setLength(0);
        fastCalidRaw = slowCalidRaw = countRaw = calidCountExhaustiveRaw = supportRaw = fastCvnRaw = slowCvnRaw = mode01_00=mode01_20="";
        transportReport.setLength(0);
        commandResults.clear();
        vinCountRaw=vinCountExhaustiveRaw=fastVinRaw=slowVinRaw=cvnCountRaw=cvnCountExhaustiveRaw="";
        fastCalidComplete = slowCalidComplete = fastCvnComplete = slowCvnComplete = false;
        comparisonAttempted = false;
        lmx0Restore="not attempted";
        raw.setText("Starting identification…");
        setLiveValues("Reading…"); rpm.setText("Reading…"); calid.setText("Reading…"); calidCount.setText("Reading…");
        calidCoverage.setText("Reading…"); timingComparison.setText("Waiting for normal-mode read…");
        cvn.setText("Reading…"); cvnCount.setText("Reading…"); cvnCoverage.setText("Reading…");
        vin.setText("Reading…"); vinCount.setText("Reading…"); vinCoverage.setText("Reading…"); vinByte.setText("Reading…");
        romMatch.setText("Reading…"); mode09.setText("Reading…");
        read.setEnabled(false); status.setText("Reading ECU information…"); scanState.setText("Snapshot scan in progress"); lmxStatus.setText("LMX0 restore: not attempted");
        requestNext();
    }
    private void requestNext() {
        if (ble == null || !ble.isReady()) { scanBusy = false; return; }
        if (scanIndex >= PIDS.length) {
            beginExhaustiveTiming();
            return;
        }
        final int position = scanIndex++;
        final String pid = PIDS[position];
        if (!shouldRead(pid)) { setLivePid(pid,"Not advertised"); requestNext(); return; }
        status.setText("Reading " + PID_LABELS[position] + "…");
        scanState.setText("Snapshot " + (position + 1) + "/" + PIDS.length + " • " + PID_LABELS[position]);
        ble.requestPidResult(pid, facts -> {
            String response=facts.response; boolean complete=facts.complete;
            String value = response.trim();
            if (!complete) value = value + " [INCOMPLETE/TIMEOUT]";
            recordTransport(facts);
            report.append(PID_LABELS[position]).append(" [").append(pid).append("]:\n")
                    .append(value.isEmpty() ? "(empty response)" : value).append("\n\n");
            raw.setText(report.toString());
            if (pid.equals("0100")) mode01_00=response;
            if (pid.equals("0120")) mode01_20=response;
            if (pid.equals("010C")) rpm.setText(complete ? ResponseParser.rpm(response) : "Timed out");
            if (pid.startsWith("01") && !pid.equals("0100") && !pid.equals("0120") && !pid.equals("0140") && !pid.equals("010C")) { String live=complete?ResponseParser.live(pid,response):"Timed out"; setLivePid(pid,live); addLog(PID_LABELS[position]+": "+live); }
            if (pid.equals("0900")) {
                supportRaw = response;
                mode09.setText(complete ? compact(response) : "Timed out");
            }
            if (pid.equals("0901")) {
                vinCountRaw=response; updateVin();
            }
            if (pid.equals("0902")) { fastVinRaw=response; updateVin(); }
            if (pid.equals("0903")) {
                countRaw = response;
                calidCount.setText(complete ? ResponseParser.calibrationMessageCount(response) : "Timed out");
            }
            if (pid.equals("0904")) {
                fastCalidRaw = response; fastCalidComplete = complete;
                updateCalidCoverage();
            }
            if (pid.equals("0905")) {cvnCountRaw=response;updateCvn();}
            if (pid.equals("0906")) { fastCvnRaw = response; fastCvnComplete = complete; updateCvn(); }
            ui.postDelayed(this::requestNext, 180);
        });
    }
    private boolean shouldRead(String pid) {
        if (pid.equals("0100") || pid.startsWith("09")) return true;
        if (pid.equals("0120")) return ResponseParser.mode01Advertises(mode01_00,0,0x20);
        if (pid.equals("0140")) return ResponseParser.mode01Advertises(mode01_20,0x20,0x40);
        if (pid.startsWith("01")) {
            int p=Integer.parseInt(pid.substring(2),16);
            return p<=0x20 ? ResponseParser.mode01Advertises(mode01_00,0,p) :
                    p<=0x40 && ResponseParser.mode01Advertises(mode01_20,0x20,p);
        }
        return false;
    }
    private void setLiveValues(String value) { coolant.setText(value); stft.setText(value); ltft.setText(value); speed.setText(value); timing.setText(value); intake.setText(value); maf.setText(value); throttle.setText(value); }
    private void setLivePid(String pid, String value) {
        if (pid.equals("0105")) coolant.setText(value); else if (pid.equals("0106")) stft.setText(value);
        else if (pid.equals("0107")) ltft.setText(value); else if (pid.equals("010D")) speed.setText(value);
        else if (pid.equals("010E")) timing.setText(value); else if (pid.equals("010F")) intake.setText(value);
        else if (pid.equals("0110")) maf.setText(value); else if (pid.equals("0111")) throttle.setText(value); else if (pid.equals("010C")) rpm.setText(value);
    }
    private boolean[] activeLivePids() {
        boolean[] active=new boolean[LivePollScheduler.PIDS.length];
        for(int i=0;i<active.length;i++) {
            int pid=Integer.parseInt(LivePollScheduler.PIDS[i].substring(2),16);
            active[i]=BlueProtocol.allowedPid(LivePollScheduler.PIDS[i]) && ResponseParser.mode01Advertises(mode01_00,0,pid);
        }
        return active;
    }
    private boolean supportBitmapsReady() {
        return !ResponseParser.unavailable(mode01_00)
                && (!ResponseParser.mode01Advertises(mode01_00,0,0x20) || !ResponseParser.unavailable(mode01_20));
    }
    private void startLive() {
        if (ble==null || !ble.isReady() || scanBusy || liveRunning) return;
        if (!supportBitmapsReady()) { liveStatus.setText("Run Scan first to load Mode 01 support bitmaps."); return; }
        boolean any=false; for(boolean active:activeLivePids()) any|=active;
        if(!any) { liveStatus.setText("Scan found no advertised allowlisted live PIDs."); return; }
        liveRunning=true; liveInFlight=false; liveCount=0; liveStarted=android.os.SystemClock.elapsedRealtime(); liveLast="Verifying LMX0";
        liveSamples.clear(); liveSampleTimes.clear(); liveRtts.clear(); liveScheduler.reset();
        startLive.setEnabled(false); stopLive.setEnabled(true); liveStatus.setText("Verifying scanner normal timing (LMX0)…");
        ble.requestTimingMode("LMX0",(response,complete)->{
            recordLatestTransport();
            if(!liveRunning) return;
            if(!complete || !adapterOk(response)) { liveLast="LMX0 unverified"; stopLive("Live not started: LMX0 must return OK"); return; }
            liveLast="LMX0 OK"; liveStatus.setText("Live polling • Park/read-only • one request at a time");
            refreshLiveStats(); ui.post(liveAgeTicker); nextLive();
        });
    }
    private void nextLive() {
        if(!liveRunning || liveInFlight || ble==null || !ble.isReady()) return;
        boolean[] active=activeLivePids(); long now=android.os.SystemClock.elapsedRealtime();
        String pid=liveScheduler.mostOverdue(active,now);
        if(pid==null) {
            long delay=liveScheduler.delayUntilNext(active,now);
            if(delay<0) { stopLive("No advertised allowlisted live PIDs"); return; }
            ui.postDelayed(livePollTicker,Math.max(1,delay)); return;
        }
        liveInFlight=true;
        ble.requestPidResult(pid,facts->{
            liveInFlight=false;
            long sampleNow=android.os.SystemClock.elapsedRealtime();
            liveScheduler.sampled(pid,sampleNow);
            liveCount++; liveRtts.addLast(facts.roundTripMs); if(liveRtts.size()>20) liveRtts.removeFirst();
            liveLast=facts.classification;
            if(facts.complete) {
                String parsed=pid.equals("010C")?ResponseParser.rpm(facts.response):ResponseParser.live(pid,facts.response);
                liveSamples.put(pid,parsed); liveSampleTimes.put(pid,sampleNow); renderLivePid(pid,sampleNow);
            }
            refreshLiveStats();
            if(liveRunning) ui.post(livePollTicker);
        });
    }
    private void renderLivePid(String pid,long now) {
        String value=liveSamples.get(pid); Long then=liveSampleTimes.get(pid); if(value==null || then==null) return;
        long age=Math.max(0,now-then), period=LivePollScheduler.period(pid);
        setLivePid(pid,value+" • "+age+" ms"+(age>period*2?" STALE":""));
    }
    private void refreshLiveAges() { long now=android.os.SystemClock.elapsedRealtime(); for(String pid:liveSamples.keySet()) renderLivePid(pid,now); refreshLiveStats(); }
    private void refreshLiveStats() {
        int active=0; for(boolean value:activeLivePids()) if(value) active++;
        long elapsed=Math.max(1,android.os.SystemClock.elapsedRealtime()-liveStarted), total=0; for(long rtt:liveRtts) total+=rtt;
        String rate=String.format(Locale.US,"%.1f",liveCount*1000.0/elapsed);
        liveStats.setText("Samples: "+rate+"/s • RTT: "+(liveRtts.isEmpty()?"—":(total/liveRtts.size())+" ms")+" • Active PIDs: "+active+" • Last: "+liveLast);
    }
    private void stopLive(String reason) {
        boolean wasRunning=liveRunning; liveRunning=false; ui.removeCallbacks(liveAgeTicker); ui.removeCallbacks(livePollTicker);
        if(startLive!=null) startLive.setEnabled(ble!=null && ble.isReady() && !scanBusy && supportBitmapsReady());
        if(stopLive!=null) stopLive.setEnabled(false);
        if(wasRunning && liveStatus!=null) { liveStatus.setText(reason+"; in-flight response, if any, will not schedule another request."); refreshLiveStats(); }
    }

    /** Compare identical read-only PIDs using the documented BlueDriver timing modes.
     * Once LMX1 has been used, always attempt to restore LMX0 before finishing.
     * The vehicle ECU itself is never reconfigured or written to.
     */
    private void beginExhaustiveTiming() {
        if (!ble.isReady()) { finishIdentification("BLE connection lost during scan"); return; }
        ResponseParser.CalidAnalysis ids=ResponseParser.analyzeCalid(fastCalidRaw,"",countRaw);
        ResponseParser.CvnAnalysis cvns=ResponseParser.analyzeCvn(fastCvnRaw,"",cvnCountRaw);
        ResponseParser.VinAnalysis vins=ResponseParser.analyzeVin(fastVinRaw,"",vinCountRaw);
        retryCalidCount=ids.expectedCount<1 && ids.observedCount>0;
        retryCvnCount=cvns.expected<1 && cvns.observed>0;
        retryCalid=ResponseParser.needsExhaustiveRetry(ids.expectedCount,ids.observedCount,fastCalidComplete,ids.conflicts);
        retryCvn=ResponseParser.needsExhaustiveRetry(cvns.expected,cvns.observed,fastCvnComplete,cvns.conflicts);
        retryVin=vins.expected>0 && !vins.complete;
        if (!retryCalidCount && !retryCvnCount && !retryCalid && !retryCvn && !retryVin) {
            finishIdentification("Identification complete; LMX1 not needed"); return;
        }
        comparisonAttempted = true;
        lmx0Restore="pending";
        lmxStatus.setText("LMX0 restore: pending");
        status.setText("Switching BlueDriver to exhaustive timing (LMX1)…");
        ble.requestTimingMode("LMX1", (response, complete) -> {
            recordLatestTransport();
            addDiagnosticRecord("Scanner timing LMX1", response, complete);
            if (!complete || !adapterOk(response)) {
                timingComparison.setText("LMX1 response uncertain; compare aborted, restoring LMX0");
                restoreNormalTiming();
                return;
            }
            requestExhaustiveCalidCount();
        });
    }
    private String selectedCalidCount() {
        return ResponseParser.mode09MessageCount(calidCountExhaustiveRaw,3)>0 ? calidCountExhaustiveRaw:countRaw;
    }
    private void requestExhaustiveCalidCount() {
        if (!retryCalidCount) { requestExhaustiveCalid(); return; }
        status.setText("Exhaustive timing: retrying CALID count [0903]…");
        ble.requestPid("0903", (response, complete) -> {
            recordLatestTransport(); calidCountExhaustiveRaw=response;
            addDiagnosticRecord("CALID count exhaustive timing [0903]",response,complete);
            ResponseParser.CalidAnalysis ids=ResponseParser.analyzeCalid(fastCalidRaw,slowCalidRaw,selectedCalidCount());
            retryCalid=ResponseParser.needsExhaustiveRetry(ids.expectedCount,ids.observedCount,fastCalidComplete,ids.conflicts);
            calidCount.setText(ids.expectedCount>0?Integer.toString(ids.expectedCount):"Unavailable; count not confirmed");
            requestExhaustiveCalid();
        });
    }
    private void requestExhaustiveCalid() {
        if (!retryCalid) { requestExhaustiveCvnCount(); return; }
        status.setText("Exhaustive timing: retrying incomplete 0904…");
        ble.requestPid("0904", (slow, slowComplete) -> {
                recordLatestTransport();
                slowCalidRaw = slow; slowCalidComplete = slowComplete;
                addDiagnosticRecord("Calibration ID exhaustive timing [0904]", slow, slowComplete);
                updateCalidCoverage();
                requestExhaustiveCvnCount();
        });
    }
    private void requestExhaustiveCvnCount() {
        if (!retryCvnCount) { requestExhaustiveCvn(); return; }
        status.setText("Exhaustive timing: retrying CVN count [0905]…");
        ble.requestPid("0905", (response, complete) -> {
            recordLatestTransport(); cvnCountExhaustiveRaw=response;
            addDiagnosticRecord("CVN count exhaustive timing [0905]",response,complete);
            int expected=ResponseParser.mode09MessageCount(response,5);
            ResponseParser.CvnAnalysis cvns=ResponseParser.analyzeCvn(fastCvnRaw,slowCvnRaw,response);
            retryCvn=ResponseParser.needsExhaustiveRetry(expected,cvns.observed,fastCvnComplete,cvns.conflicts);
            updateCvn(); requestExhaustiveCvn();
        });
    }
    private void requestExhaustiveCvn() {
        if (!retryCvn) { requestExhaustiveVin(); return; }
        status.setText("Exhaustive timing: retrying incomplete 0906…");
        ble.requestPid("0906", (response, complete) -> { slowCvnRaw=response; slowCvnComplete=complete;
            recordLatestTransport();
            addDiagnosticRecord("Calibration verification exhaustive timing [0906]",response,complete); updateCvn(); requestExhaustiveVin(); });
    }
    private void requestExhaustiveVin() {
        if (!retryVin) { restoreNormalTiming(); return; }
        status.setText("Exhaustive timing: retrying incomplete 0902…");
        ble.requestPid("0902", (response, complete) -> { slowVinRaw=response;
            recordLatestTransport();
            addDiagnosticRecord("VIN exhaustive timing [0902]",response,complete); updateVin(); restoreNormalTiming(); });
    }
    private boolean adapterOk(String raw) {
        return raw != null && raw.toUpperCase(Locale.US).contains("OK") && !ResponseParser.unavailable(raw);
    }
    private void restoreNormalTiming() {
        if (ble == null || !ble.isReady()) {
            lmx0Restore="not attempted: BLE lost";
            finishIdentification("BLE lost; scanner may need reconnection to reset timing"); return;
        }
        status.setText("Restoring BlueDriver normal timing (LMX0)…");
        ble.requestTimingMode("LMX0", (response, complete) -> {
            recordLatestTransport();
            addDiagnosticRecord("Scanner timing LMX0 restore", response, complete);
            if (!complete || !adapterOk(response)) {
                lmx0Restore="failed/unverified";
                finishIdentification("LMX0 restoration unverified — reconnect before next scan");
                return;
            }
            lmx0Restore="succeeded";
            finishIdentification("Identification and timing comparison complete");
        });
    }
    private void addDiagnosticRecord(String label, String response, boolean complete) {
        report.append(label).append(":\n").append(response == null || response.trim().isEmpty() ?
                "(empty)" : response.trim());
        if (!complete) report.append(" [TRANSPORT TIMEOUT / PARTIAL]");
        report.append("\n\n");
        raw.setText(report.toString());
    }
    private void updateCalidCoverage() {
        ResponseParser.CalidAnalysis analysis = ResponseParser.analyzeCalid(fastCalidRaw, slowCalidRaw, selectedCalidCount());
        calid.setText(analysis.displayedId);
        calidCoverage.setText(analysis.detail);
        romMatch.setText(ResponseParser.romDefinitionMatch(analysis));
        if (!comparisonAttempted) {
            timingComparison.setText("Normal timing captured " + ResponseParser.calidRecordCount(fastCalidRaw)
                    + " indexed record(s); waiting for LMX1");
        } else {
            int fast = ResponseParser.calidRecordCount(fastCalidRaw);
            int slow = ResponseParser.calidRecordCount(slowCalidRaw);
            timingComparison.setText("LMX0: " + fast + ", LMX1: " + slow + " indexed record(s). "
                    + (slow > fast ? "Exhaustive timing returned more records." :
                    "No additional records proven by exhaustive timing.")
                    + (!fastCalidComplete || !slowCalidComplete ? " Some transport responses incomplete." : ""));
        }
    }
    private void updateCvn() {
        String count = ResponseParser.mode09MessageCount(cvnCountExhaustiveRaw,5)>0 ? cvnCountExhaustiveRaw:cvnCountRaw;
        ResponseParser.CvnAnalysis a=ResponseParser.analyzeCvn(fastCvnRaw,slowCvnRaw,count);
        cvn.setText(a.values + (a.complete ? " (count validated)" : " (unverified completeness)"));
        cvnCoverage.setText(a.detail);
        cvnCount.setText(a.expected>0 ? Integer.toString(a.expected):"Unavailable; count not confirmed");
        boolean advertises=ResponseParser.mode09Advertises(supportRaw,6);
        if(a.observed==0 && advertises) cvn.setText("0906 advertised by 0900, but no indexed CVNs received");
    }
    private void updateVin() {
        String count = ResponseParser.mode09MessageCount(vinCountExhaustiveRaw,1)>0 ? vinCountExhaustiveRaw:vinCountRaw;
        ResponseParser.VinAnalysis a=ResponseParser.analyzeVin(fastVinRaw,slowVinRaw,count);
        vin.setText(a.complete?a.vin:"Unconfirmed (inspect raw result)");
        vinCount.setText(a.expected>0?Integer.toString(a.expected):"Unavailable");
        vinCoverage.setText(a.detail);
        String userLast=expectedVinLast==null?"":expectedVinLast.getText().toString();
        ResponseParser.VinByteAnalysis byteCheck=ResponseParser.analyzeVinFinalByte(fastVinRaw,slowVinRaw,count,userLast);
        vinByte.setText(byteCheck.summary);
    }
    private void finishIdentification(String message) {
        scanBusy=false; status.setText(message); read.setEnabled(ble!=null && ble.isReady());
        if(startLive!=null) startLive.setEnabled(ble!=null && ble.isReady() && supportBitmapsReady());
        scanState.setText(message); lmxStatus.setText("LMX0 restore: " + lmx0Restore);
        updateCalidCoverage(); updateCvn(); updateVin();
        addLog("SCAN   " + message);
        addLog("CALID  " + calidCoverage.getText());
    }

    private String compact(String s) {
        if (ResponseParser.unavailable(s)) return "Not reported";
        return s.replace("\r", " ").replace(">", " ").trim();
    }
    private String safeReport() {
        ResponseParser.CalidAnalysis ids=ResponseParser.analyzeCalid(fastCalidRaw,slowCalidRaw,selectedCalidCount());
        String cvnCount=ResponseParser.mode09MessageCount(cvnCountExhaustiveRaw,5)>0?cvnCountExhaustiveRaw:cvnCountRaw;
        ResponseParser.CvnAnalysis checks=ResponseParser.analyzeCvn(fastCvnRaw,slowCvnRaw,cvnCount);
        String vinCount=ResponseParser.mode09MessageCount(vinCountExhaustiveRaw,1)>0?vinCountExhaustiveRaw:vinCountRaw;
        ResponseParser.VinAnalysis vehicle=ResponseParser.analyzeVin(fastVinRaw,slowVinRaw,vinCount);
        return "Z33 ECU Lab v0.2.0 — read-only identification\n"
              + "Device: 2005 Nissan 350Z 5AT (candidate MEC61-530 D1)\n"
               + "Vehicle diagnostic protocol: "+detectedProtocol+"\n"
               + "Adapter firmware: "+adapterFirmware+"\n"
               + "LMX0 restore after exhaustive sequence: "+lmx0Restore+"\n"
                + "Command transport (sanitized):\n"+transportReport
               + "Live session (compact, no sample log): "+liveSessionSummary()+"\n"
              + "Engine RPM at scan: "+rpm.getText()+"\n"
              + "CALID: "+ids.displayedId+"\n"
              + "CALID indexed records: "+ids.detail+"\n"
              + "ROM definition: "+ResponseParser.romDefinitionMatch(ids)+"\n"
              + "CVN: "+checks.values+"\n"
              + "CVN records: "+checks.detail+"\n"
              + "VIN (masked for privacy): "+ResponseParser.maskedVin(vehicle.vin)+"\n"
              + "VIN record validation: "+vehicle.detail+"\n"
              + "VIN terminal-field analysis (no VIN bytes exported): "+
                   ResponseParser.analyzeVinFinalByte(fastVinRaw,slowVinRaw,vinCount,
                         expectedVinLast==null?"":expectedVinLast.getText().toString()).summary+"\n"
              + "Fast CALID record count: "+ResponseParser.calidRecordCount(fastCalidRaw)+"\n"
              + "Exhaustive CALID record count: "+ResponseParser.calidRecordCount(slowCalidRaw)+"\n"
              + "Mode 09 CVN advertised: "+ResponseParser.mode09Advertises(supportRaw,6)+"\n"
              + "ECU tuning project: reported 1CF010 matches an UpRev-listed 2005 350Z VQ35DE AT CF010 ROM.\n"
              + "Firmware access: not supported by this BlueDriver app; full ROM backup NOT obtained.\n"
              + "2-step / launch control: not implemented; specific CF010 feature support not verified.\n"
              + "Read-only diagnostic identification, NOT a dump or verification of ECU ROM bytes.\n"
              + "Raw VIN response bytes and BLE trace intentionally omitted from exported report.\n";
    }
    private void shareLog() {
        Intent intent=new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT,safeReport());
        startActivity(Intent.createChooser(intent,"Share redacted ECU identification report"));
    }
    private void saveReport(){
        Intent saveIntent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        saveIntent.addCategory(Intent.CATEGORY_OPENABLE);
        saveIntent.setType("text/plain");
        saveIntent.putExtra(Intent.EXTRA_TITLE,"Z33-ECU-Lab-Report-" + new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date()) + ".txt");
        startActivityForResult(saveIntent,REQUEST_SAVE_REPORT);
    }
    private String safeJsonReport() {
        ResponseParser.CalidAnalysis ids=ResponseParser.analyzeCalid(fastCalidRaw,slowCalidRaw,selectedCalidCount());
        String cvnCount=ResponseParser.mode09MessageCount(cvnCountExhaustiveRaw,5)>0?cvnCountExhaustiveRaw:cvnCountRaw;
        ResponseParser.CvnAnalysis cvns=ResponseParser.analyzeCvn(fastCvnRaw,slowCvnRaw,cvnCount);
        StringBuilder commands=new StringBuilder("[");
        for(BlueDriverBle.CommandResult result:commandResults) {
            if(commands.length()>1)commands.append(',');
            commands.append("{\"command\":\"").append(json(result.command)).append("\",\"classification\":\"")
                    .append(json(result.classification)).append("\",\"complete\":").append(result.complete)
                    .append(",\"roundTripMs\":").append(result.roundTripMs)
                    .append(",\"dataFragments\":").append(result.dataFragments)
                    .append(",\"decodedBytes\":").append(result.decodedBytes).append('}');
        }
        commands.append(']');
        return "{\n  \"app\":\"Z33 ECU Lab 0.2.0\",\n  \"protocol\":\""+json(detectedProtocol)+"\",\n"
                +"  \"adapterFirmware\":\""+json(adapterFirmware)+"\",\n  \"lmx0Restore\":\""+json(lmx0Restore)+"\",\n"
                +"  \"liveSession\":\""+json(liveSessionSummary())+"\",\n"
                +"  \"commands\":"+commands+",\n"
                +"  \"calidCoverage\":\""+json(ids.detail)+"\",\n  \"cvnCoverage\":\""+json(cvns.detail)+"\"\n}";
    }
    private void recordTransport(BlueDriverBle.CommandResult result) {
        if(result==null)return;
        commandResults.add(result);
        transportReport.append(result.command).append(": ").append(result.classification).append(", ")
                .append(result.roundTripMs).append(" ms, ").append(result.dataFragments).append(" fragments, ")
                .append(result.decodedBytes).append(" decoded bytes\n");
    }
    private void recordLatestTransport() { if(ble!=null)recordTransport(ble.latestResult()); }
    private String liveSessionSummary() {
        long total=0; for(long rtt:liveRtts) total+=rtt;
        int active=0; for(boolean value:activeLivePids()) if(value) active++;
        long elapsed=liveStarted==0?0:Math.max(1,android.os.SystemClock.elapsedRealtime()-liveStarted);
        return "samples="+liveCount+", rate="+(elapsed==0?"0.0":String.format(Locale.US,"%.1f",liveCount*1000.0/elapsed))
                +"/s, avgRtt="+(liveRtts.isEmpty()?"—":total/liveRtts.size()+"ms")+", active="+active+", last="+liveLast;
    }
    private static String json(String s) { return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r",""); }
    private void saveJsonReport(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"Z33-ECU-Lab-Report-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date())+".json"); startActivityForResult(i,REQUEST_SAVE_JSON);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if((request!=REQUEST_SAVE_REPORT && request!=REQUEST_SAVE_JSON) || result!=RESULT_OK || data==null || data.getData()==null)return;
        try (java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())) {
            if(out==null) throw new java.io.IOException("Storage provider did not open destination");
            out.write((request==REQUEST_SAVE_JSON?safeJsonReport():safeReport()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            toast("Report saved (VIN masked)");
        }catch(Exception error){toast("Cannot save report: "+error.getMessage());}
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private void addLog(String line) {
        String now = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        trace.append(now).append("  ").append(line).append('\n');
        if (trace.length() > 25000) trace.delete(0, trace.length() - 22000);
        log.setText(trace.toString());
        logScroller.post(() -> logScroller.fullScroll(View.FOCUS_DOWN));
    }
    @Override public void onStatus(String message) {
        ui.post(() -> { status.setText(message); addLog(message); });
    }
    @Override public void onLog(String message) { ui.post(() -> {
        if (message.startsWith("INIT   LMDP =>")) {
            if (message.matches("(?s).*=>\\s*3(?:\\s|$).*")) detectedProtocol="ISO 9141-2 • K-Line • protocol 3";
            else detectedProtocol="Adapter reported: " + message.substring("INIT   LMDP =>".length()).trim();
            protocol.setText(detectedProtocol);
        }
        if (message.startsWith("INIT   LMI =>")) { adapterFirmware=message.substring("INIT   LMI =>".length()).trim(); adapterSummary.setText(adapterFirmware); }
        addLog(message);
    }); }
    @Override public void onReady(boolean ready) {
        ui.post(() -> {
            read.setEnabled(ready && !scanBusy);
            if (ready) connect.setEnabled(false);
            if (!ready) { scanBusy = false; stopLive("Stopped: BLE not ready"); }
            else if(startLive!=null) startLive.setEnabled(!scanBusy && !liveRunning && supportBitmapsReady());
        });
    }
    @Override public void onError(String message) {
        ui.post(() -> { stopLive("Stopped: "+message); status.setText(message); scanState.setText(message); addLog("ERROR  " + message); connect.setEnabled(true); read.setEnabled(false); scanBusy = false; });
    }
    @Override protected void onStop() { stopLive("Stopped with app lifecycle"); super.onStop(); }
    @Override protected void onDestroy() {
        stopLive("Stopped on destroy");
        if (ble != null) ble.close();
        super.onDestroy();
    }
}
