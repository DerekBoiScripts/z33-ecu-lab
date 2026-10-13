package com.z33eculab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.util.TimeZone;

/** Simple phone-first diagnostic UI with no third-party dependencies or network usage. */
public class MainActivity extends Activity implements BlueDriverBle.Listener {
    private static final int BG = 0xFF0B1220, PANEL = 0xFF172337, SUBPANEL = 0xFF111D30;
    private static final int TEXT = 0xFFF2F6FB, MUTED = 0xFF97A9C0, ACCENT = 0xFF4BE6C7;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private BlueDriverBle ble;
    private Button connect, read, disconnect, share, save, saveJson, vinCompare, startLive, stopLive, recordLive, exportCsv, readDtcs;
    private EditText expectedVinLast;
    private TextView status, scanState, adapterSummary, lmxStatus, rpm, coolant, stft, ltft, speed, timing, intake, maf, throttle, load, map, fuel, voltage, calid, calidCount, calidCoverage, timingComparison, cvn, cvnCoverage, cvnCount, vin, vinCoverage, vinCount, vinByte, protocol, romMatch, mode09, raw, log, liveStatus, liveStats, dtcStatus, dtcMonitor;
    private ScrollView logScroller;
    private final StringBuilder trace = new StringBuilder();
    private final StringBuilder report = new StringBuilder();
    private boolean scanBusy;
    private boolean liveRunning, liveInFlight;
    private final LivePollScheduler liveScheduler=new LivePollScheduler();
    private final Map<String,String> liveSamples=new HashMap<>();
    private final Map<String,Long> liveSampleTimes=new HashMap<>();
    private final Map<String,Boolean> liveEnabled=new HashMap<>();
    private final Map<String,Integer> liveTiers=new HashMap<>();
    private final SampleRateTracker liveRates=new SampleRateTracker();
    private SharedPreferences livePreferences;
    private final ArrayDeque<Long> liveRtts=new ArrayDeque<>();
    private long liveStarted, liveCount, liveStopped, liveFrozenCount, liveStartedWall;
    private int liveSelectedCount;
    private String liveLast="None";
    private boolean recording, dtcPending, dtcBusy, foreground;
    private boolean dtcInFlight;
    private boolean dtcMayNeedRestore, dtcRestoring;
    private String dtcDeferredFinish;
    private int dtcEpoch;
    private static final String[] DTC_MODES={"03","07","0A"};
    private static final String[] DTC_LABELS={"Stored","Pending","Permanent"};
    private final String[] dtcResults={"Not read","Not read","Not read"};
    private String dtcMonitorResult="Not read";
    private ResponseParser.MonitorStatus dtcMonitorStatus;
    private final TextView[] dtcViews=new TextView[3];
    private LinearLayout dtcRows;
    private Button readFreeze;
    private TextView freezeStatus, freezeValues;
    private boolean freezeScan;
    private String freezeSupport="Not read";
    private final String[] freezeResults=new String[FreezeFrame.REQUESTS.length];
    private static final int CSV_ROW_CAP=10000;
    private final ArrayList<String> csvRows=new ArrayList<>();
    private final ArrayList<String> recordingPids=new ArrayList<>();
    private long recordingStartedElapsed, recordingStartedWall;
    private final Map<String,MiniGraphView> graphs=new HashMap<>();
    private final Runnable liveAgeTicker=new Runnable() { @Override public void run() { if(liveRunning) { refreshLiveAges(); ui.postDelayed(this,250); } } };
    private final Runnable livePollTicker=this::nextLive;
    private String fastCalidRaw = "", slowCalidRaw = "", countRaw = "", calidCountExhaustiveRaw = "", supportRaw = "";
    private String mode01_00="", mode01_20="", mode01_40="";
    private final StringBuilder transportReport = new StringBuilder();
    private final ArrayList<BlueDriverBle.CommandResult> commandResults = new ArrayList<>();
    private String fastCvnRaw = "", slowCvnRaw = "";
    private String vinCountRaw="", vinCountExhaustiveRaw="", fastVinRaw="", slowVinRaw="", cvnCountRaw="", cvnCountExhaustiveRaw="";
    private String detectedProtocol="Not detected";
    private String adapterFirmware="Not detected", lmx0Restore="not attempted";
    private static final int REQUEST_SAVE_REPORT=504;
    private static final int REQUEST_SAVE_JSON=505, REQUEST_SAVE_CSV=506;
    private boolean fastCalidComplete, slowCalidComplete, fastCvnComplete, slowCvnComplete;
    private boolean comparisonAttempted;
    private boolean retryCalidCount, retryCvnCount, retryCalid, retryCvn, retryVin;
    private FrameLayout tabContent;
    private Button[] tabs;
    private SharedPreferences historyPreferences;
    private TextView history;
    private String pendingExportName, pendingExportType;

    private int scanIndex;
    private static final String[] PIDS = {"0100","0120","0140","0104","0105","0106","0107","010B","010C","010D","010E","010F","0110","0111","012F","0142","0900","0901","0902","0903","0904","0905","0906"};
    private static final String[] PID_LABELS = {"Supported live PIDs 01-20","Supported live PIDs 21-40","Supported live PIDs 41-60","Calculated load","Coolant","STFT bank 1","LTFT bank 1","MAP","Engine RPM","Speed","Timing advance","Intake temperature","MAF","Throttle","Fuel level","Control-module voltage","Supported Mode 09","VIN message count","VIN","CALID message count","Calibration ID","CVN message count","Calibration verification"};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        livePreferences=getSharedPreferences("live_sensor_settings",MODE_PRIVATE);
        historyPreferences=getSharedPreferences("local_history",MODE_PRIVATE);
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
        b.setContentDescription(title);
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
        LinearLayout recControls=new LinearLayout(this); recControls.setOrientation(LinearLayout.HORIZONTAL); recordLive=button("Start Recording",0xFF355A76); exportCsv=button("Export CSV",0xFF293C50); recControls.addView(recordLive,new LinearLayout.LayoutParams(0,dp(48),1)); recControls.addView(exportCsv,new LinearLayout.LayoutParams(0,dp(48),1)); liveBody.addView(recControls);
        liveStatus=valueLine(liveBody,"Run Scan first to load Mode 01 support bitmaps"); liveStats=text("Samples: 0/s • RTT: — • Active PIDs: 0 • Last: None",12,MUTED,false); liveBody.addView(liveStats); space(liveBody,10);
        liveBody.addView(text("Tap an advertised sensor to enable/disable it; long press cycles fast/default/slow priority. Selection and priority persist. Rates are measured valid samples only.",12,MUTED,false));
        liveBody.addView(text("CSV is explicit only: one valid selected sample per elapsed_ms,timestamp_utc,pid,sensor,value row (10,000-row limit).",12,MUTED,false));
        load=liveValue(liveBody,"Calculated load"); sensor(load,"0104"); coolant=liveValue(liveBody,"Coolant"); sensor(coolant,"0105"); stft=liveValue(liveBody,"STFT B1"); sensor(stft,"0106"); ltft=liveValue(liveBody,"LTFT B1"); sensor(ltft,"0107"); map=liveValue(liveBody,"MAP"); sensor(map,"010B"); rpm=liveValue(liveBody,"RPM"); sensor(rpm,"010C"); speed=liveValue(liveBody,"Speed"); sensor(speed,"010D"); timing=liveValue(liveBody,"Timing"); sensor(timing,"010E"); intake=liveValue(liveBody,"Intake temperature"); sensor(intake,"010F"); maf=liveValue(liveBody,"MAF"); sensor(maf,"0110"); throttle=liveValue(liveBody,"Throttle"); sensor(throttle,"0111"); fuel=liveValue(liveBody,"Fuel level"); sensor(fuel,"012F"); voltage=liveValue(liveBody,"Control-module voltage"); sensor(voltage,"0142");
        addGraph(liveBody,"010C","RPM trace"); addGraph(liveBody,"0110","MAF trace"); addGraph(liveBody,"0111","Throttle trace"); addGraph(liveBody,"0106","STFT trace"); addGraph(liveBody,"0107","LTFT trace");

        ScrollView identity=page(); LinearLayout id=pageBody(identity);
        LinearLayout ids=card(id); caption(ids,"ECU identity"); caption(ids,"Calibration ID (09/04)"); calid=valueLine(ids,"Not read yet"); caption(ids,"CALID count / coverage"); calidCount=valueLine(ids,"Not read yet"); calidCoverage=valueLine(ids,"Not read yet"); caption(ids,"ROM definition"); romMatch=valueLine(ids,"Not checked yet");
        LinearLayout checks=card(id); caption(checks,"Verification"); caption(checks,"CVN (09/06)"); cvn=valueLine(checks,"Not read yet"); caption(checks,"CVN count / coverage"); cvnCount=valueLine(checks,"Not read yet"); cvnCoverage=valueLine(checks,"Not read yet"); caption(checks,"Mode 09 support"); mode09=valueLine(checks,"Not read yet");
        LinearLayout vinCard=card(id); caption(vinCard,"VIN privacy / final-byte inspection"); vin=valueLine(vinCard,"Not read yet"); vinCount=valueLine(vinCard,"Count unavailable"); vinCoverage=valueLine(vinCard,"Not read yet"); vinByte=valueLine(vinCard,"Awaiting VIN diagnostic response"); vinCard.addView(text("Optional: compare only one final VIN character. Full VIN is not exported.",12,MUTED,false)); expectedVinLast=new EditText(this); expectedVinLast.setSingleLine(true); expectedVinLast.setTextColor(TEXT); expectedVinLast.setHintTextColor(MUTED); expectedVinLast.setHint("Expected final character"); expectedVinLast.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)}); expectedVinLast.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS); vinCard.addView(expectedVinLast,new LinearLayout.LayoutParams(-1,dp(52))); vinCompare=button("Compare final VIN byte",0xFF355A76); vinCard.addView(vinCompare,new LinearLayout.LayoutParams(-1,dp(48)));

        ScrollView research=page(); LinearLayout researchBody=pageBody(research);
        LinearLayout exports=card(researchBody); caption(exports,"Sanitized exports"); share=button("Share report (VIN masked)",0xFF2A4259); exports.addView(share); space(exports,8); save=button("Save report .txt",0xFF2A4259); exports.addView(save); space(exports,8); saveJson=button("Save report .json",0xFF2A4259); exports.addView(saveJson);
        LinearLayout historyCard=card(researchBody); caption(historyCard,"Local session / export history"); history=text("",12,MUTED,false); historyCard.addView(history); Button clearHistory=button("Clear local history",0xFF293C50); historyCard.addView(clearHistory); clearHistory.setOnClickListener(v->confirmClearHistory()); refreshHistory();
        LinearLayout researchSafety=card(researchBody); caption(researchSafety,"Project / ROM-access safety"); researchSafety.addView(text("BlueDriver identification is not a ROM backup. No ROM extraction, kernel upload, security access, flash, fuel, timing, limiter, or VIN-write functions are present.",13,MUTED,false));
        LinearLayout dtcCard=card(researchBody); caption(dtcCard,"Generic emissions DTCs (read-only)");
        dtcCard.addView(text("Generic emissions OBD only. Nissan-enhanced ECM/TCM/ABS/SRS/BCM codes are not included unless independently verified. A 2005 ECU may not support Mode 0A. NO DATA does not prove support or absence of faults.",12,MUTED,false));
        caption(dtcCard,"ECU MIL / stored-DTC count • Mode 01 PID 01"); dtcMonitor=valueLine(dtcCard,dtcMonitorResult);
        for(int i=0;i<3;i++) { caption(dtcCard,DTC_LABELS[i]+" • Mode "+DTC_MODES[i]); dtcViews[i]=valueLine(dtcCard,dtcResults[i]); }
        dtcStatus=valueLine(dtcCard,"Not read"); readDtcs=button("Read Generic DTCs",0xFF355A76); dtcCard.addView(readDtcs);
        dtcCard.addView(text("Tap a code to copy code + description. Long-press to share only that code + description. Descriptions are a limited offline SAE subset, not a diagnosis.",12,MUTED,false));
        dtcRows=new LinearLayout(this); dtcRows.setOrientation(LinearLayout.VERTICAL); dtcCard.addView(dtcRows);
        LinearLayout freezeCard=card(researchBody); caption(freezeCard,"Generic freeze frame • Mode 02 / frame 00");
        freezeCard.addView(text("Separate read-only request; stops live polling. Only advertised PIDs are read. A 2005 K-Line ECU may not expose a freeze frame through this path. NO DATA is not proof of no faults.",12,MUTED,false));
        freezeStatus=valueLine(freezeCard,"Not read"); freezeValues=valueLine(freezeCard,"No freeze frame read");
        readFreeze=button("Read Freeze Frame",0xFF355A76); freezeCard.addView(readFreeze);
        readFreeze.setOnClickListener(v -> beginDiagnostic(true));
        LinearLayout rawCard=card(researchBody); caption(rawCard,"Raw responses"); raw=text("Waiting for a diagnostic session.",12,0xFFC4EDE3,false); raw.setTypeface(Typeface.MONOSPACE); raw.setTextIsSelectable(true); rawCard.addView(raw);
        LinearLayout console=card(researchBody); caption(console,"BLE log"); logScroller=new ScrollView(this); logScroller.setBackground(shape(SUBPANEL,10)); log=text("",11,0xFFC5D5E7,false); log.setTypeface(Typeface.MONOSPACE); log.setTextIsSelectable(true); log.setPadding(dp(12),dp(10),dp(12),dp(10)); logScroller.addView(log); console.addView(logScroller,new LinearLayout.LayoutParams(-1,dp(240)));
        tabContent.addView(dashboard); tabContent.addView(live); tabContent.addView(identity); tabContent.addView(research);
        LinearLayout nav=new LinearLayout(this); nav.setPadding(dp(6),dp(7),dp(6),dp(8)); nav.setBackgroundColor(SUBPANEL); String[] names={"Dashboard","Live Data","ECU Identity","Research / Logs"}; tabs=new Button[4]; for(int i=0;i<4;i++){ final int index=i; tabs[i]=button(names[i],i==0?ACCENT:0xFF293C50); tabs[i].setTextSize(11); nav.addView(tabs[i],new LinearLayout.LayoutParams(0,dp(52),1)); tabs[i].setOnClickListener(v->showTab(index)); } root.addView(nav);
        setContentView(root); showTab(0);
        connect.setOnClickListener(v -> requestThenConnect());
        disconnect.setOnClickListener(v -> { if(dtcBusy){finishDtcs("Diagnostic cancelled by user");toast("Restoring scanner timing; tap Disconnect again when finished");return;} stopLive("Stopped before disconnect"); if (ble != null) ble.close(); connect.setEnabled(true); status.setText("Disconnected"); scanState.setText("Disconnected"); addLog("User disconnected."); });
        read.setOnClickListener(v -> runIdentification());
        startLive.setOnClickListener(v -> startLive());
        stopLive.setOnClickListener(v -> stopLive("Stopped by user"));
        recordLive.setOnClickListener(v -> toggleRecording()); exportCsv.setOnClickListener(v -> saveCsv()); readDtcs.setOnClickListener(v -> readDtcs());
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
        if (scanBusy || dtcBusy || dtcInFlight || liveInFlight || ble == null || !ble.isReady()) { toast("Wait for the current request to finish"); return; }
        stopLive("Stopped for identity scan");
        scanBusy = true; scanIndex = 0; report.setLength(0);
        fastCalidRaw = slowCalidRaw = countRaw = calidCountExhaustiveRaw = supportRaw = fastCvnRaw = slowCvnRaw = mode01_00=mode01_20=mode01_40="";
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
            if((pid.equals("0100") || pid.equals("0120") || pid.equals("0140"))
                    && (!complete || ResponseParser.unavailable(response))) {
                retryMode01Support(pid,position); return;
            }
            if (pid.equals("0100")) mode01_00=complete?response:"";
            if (pid.equals("0120")) mode01_20=complete?response:"";
            if (pid.equals("0140")) mode01_40=complete?response:"";
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
    private void retryMode01Support(String pid,int position) {
        status.setText("Retrying "+PID_LABELS[position]+" with exhaustive timing…");
        ble.requestTimingMode("LMX1",(modeResponse,modeComplete)->{
            recordLatestTransport();
            if(!modeComplete || !adapterOk(modeResponse)) { restoreAfterMode01Retry(pid,position,null); return; }
            ble.requestPidResult(pid,facts->{
                recordTransport(facts);
                report.append(PID_LABELS[position]).append(" exhaustive retry [").append(pid).append("]:\n")
                        .append(facts.response.trim().isEmpty()?"(empty response)":facts.response.trim()).append("\n\n");
                raw.setText(report.toString());
                restoreAfterMode01Retry(pid,position,facts.complete&&!ResponseParser.unavailable(facts.response)?facts.response:null);
            });
        });
    }
    private void restoreAfterMode01Retry(String pid,int position,String response) {
        if(response!=null) {
            if(pid.equals("0100"))mode01_00=response; else if(pid.equals("0120"))mode01_20=response; else mode01_40=response;
        }
        ble.requestTimingMode("LMX0",(restore,complete)->{
            recordLatestTransport();
            addLog(PID_LABELS[position]+" exhaustive retry "+(response==null?"did not recover a bitmap":"recovered a bitmap")
                    +"; LMX0 restore "+(complete&&adapterOk(restore)?"succeeded":"failed"));
            ui.postDelayed(this::requestNext,180);
        });
    }
    private boolean shouldRead(String pid) {
        if (pid.equals("0100") || pid.startsWith("09")) return true;
        if (pid.equals("0120")) return ResponseParser.mode01Advertises(mode01_00,0,0x20);
        if (pid.equals("0140")) return ResponseParser.mode01Advertises(mode01_20,0x20,0x40);
        if (pid.startsWith("01")) {
            int p=Integer.parseInt(pid.substring(2),16);
            return p<=0x20 ? ResponseParser.mode01Advertises(mode01_00,0,p) :
                    (p<=0x40 ? ResponseParser.mode01Advertises(mode01_20,0x20,p) :
                    ResponseParser.mode01Advertises(mode01_40,0x40,p));
        }
        return false;
    }
    private void setLiveValues(String value) { load.setText(value); coolant.setText(value); stft.setText(value); ltft.setText(value); map.setText(value); speed.setText(value); timing.setText(value); intake.setText(value); maf.setText(value); throttle.setText(value); fuel.setText(value); voltage.setText(value); }
    private void setLivePid(String pid, String value) {
        if (pid.equals("0104")) load.setText(value); else if (pid.equals("0105")) coolant.setText(value); else if (pid.equals("0106")) stft.setText(value);
        else if (pid.equals("0107")) ltft.setText(value); else if (pid.equals("010D")) speed.setText(value);
        else if (pid.equals("010E")) timing.setText(value); else if (pid.equals("010F")) intake.setText(value);
        else if (pid.equals("010B")) map.setText(value); else if (pid.equals("0110")) maf.setText(value); else if (pid.equals("0111")) throttle.setText(value); else if (pid.equals("012F")) fuel.setText(value); else if (pid.equals("0142")) voltage.setText(value); else if (pid.equals("010C")) rpm.setText(value);
    }
    private boolean[] activeLivePids() {
        boolean[] active=new boolean[LivePollScheduler.PIDS.length];
        for(int i=0;i<active.length;i++) {
            active[i]=Boolean.TRUE.equals(liveEnabled.get(LivePollScheduler.PIDS[i])) && BlueProtocol.allowedPid(LivePollScheduler.PIDS[i]) && advertised(LivePollScheduler.PIDS[i]);
        }
        return active;
    }
    private boolean advertised(String pid) { return ResponseParser.advertisedLive(mode01_00,mode01_20,mode01_40,Integer.parseInt(pid.substring(2),16)); }
    private void sensor(TextView view,String pid) {
        boolean savedEnabled=livePreferences.getBoolean(pid+".enabled",true); int savedTier=livePreferences.getInt(pid+".tier",1); if(savedTier<0||savedTier>2)savedTier=1;
        liveEnabled.put(pid,savedEnabled); liveTiers.put(pid,savedTier); liveScheduler.setTier(pid,savedTier);
        view.setOnClickListener(v->{
            if(recording){toast("Stop recording before changing sensors");return;}
            if(!advertised(pid)){toast("Not advertised by ECU");return;}
            boolean enabled=!Boolean.TRUE.equals(liveEnabled.get(pid)); liveEnabled.put(pid,enabled); persistSensor(pid);
            if(!enabled) view.setText("Disabled • tap to enable");
            else if(liveRunning) { view.setText("Enabled • waiting for sample"); liveStatus.setText("Live polling • Park/read-only • one request at a time"); }
            else view.setText("Enabled • tap Start Live");
            if(liveRunning && !liveInFlight) { ui.removeCallbacks(livePollTicker); ui.post(livePollTicker); }
        });
        view.setOnLongClickListener(v->{if(recording){toast("Stop recording before changing sensors");return true;}if(!advertised(pid)){toast("Not advertised by ECU");return true;} int tier=(liveTiers.get(pid)+1)%3;liveTiers.put(pid,tier);liveScheduler.setTier(pid,tier);persistSensor(pid);if(liveRunning&&!liveInFlight){ui.removeCallbacks(livePollTicker);ui.post(livePollTicker);}toast("Priority: "+tierName(pid));return true;});
    }
    private void persistSensor(String pid){livePreferences.edit().putBoolean(pid+".enabled",Boolean.TRUE.equals(liveEnabled.get(pid))).putInt(pid+".tier",liveTiers.get(pid)).apply();}
    private String tierName(String pid) { Integer tier=liveTiers.get(pid); return tier!=null&&tier==0?"fast":tier!=null&&tier==1?"default":"slow"; }
    private void addGraph(LinearLayout parent,String pid,String label){ caption(parent,label+" • value over time • -30s … now • tap to clear");MiniGraphView v=new MiniGraphView(this);v.setContentDescription(label+", value over the last 30 seconds; tap to clear");v.setOnClickListener(w->v.clear());graphs.put(pid,v);parent.addView(v,new LinearLayout.LayoutParams(-1,dp(72))); }
    private void graph(String pid,String value,long now){ MiniGraphView v=graphs.get(pid);if(v==null)return; try{v.add(Float.parseFloat(value.replaceAll("[^0-9+\\-.]","")),now);}catch(Exception ignored){} }
    private boolean supportBitmapsReady() {
        return !ResponseParser.unavailable(mode01_00)
                && (!ResponseParser.mode01Advertises(mode01_00,0,0x20) || (!ResponseParser.unavailable(mode01_20)
                && (!ResponseParser.mode01Advertises(mode01_20,0x20,0x40) || !ResponseParser.unavailable(mode01_40))));
    }
    private void startLive() {
        if (ble==null || !ble.isReady() || scanBusy || dtcBusy || dtcInFlight || liveRunning || liveInFlight || !foreground) return;
        if (!supportBitmapsReady()) { liveStatus.setText("Run Scan first to load Mode 01 support bitmaps."); return; }
        boolean any=false; for(boolean active:activeLivePids()) any|=active;
        if(!any) { liveStatus.setText("Scan found no advertised allowlisted live PIDs."); return; }
        liveRunning=true; liveInFlight=true; liveCount=0; liveStarted=android.os.SystemClock.elapsedRealtime(); liveStartedWall=System.currentTimeMillis(); liveSelectedCount=0; for(boolean enabled:activeLivePids())if(enabled)liveSelectedCount++; liveLast="Verifying LMX0";
        liveSamples.clear(); liveSampleTimes.clear(); liveRtts.clear(); liveRates.reset(); liveScheduler.reset();
        for(MiniGraphView graph:graphs.values()) graph.clear();
        startLive.setEnabled(false); stopLive.setEnabled(true); liveStatus.setText("Verifying scanner normal timing (LMX0)…");
        ble.requestTimingMode("LMX0",(response,complete)->{
            recordLatestTransport();
            liveInFlight=false;
            if(!liveRunning) { continuePendingDtc(); return; }
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
            if(delay<0) { liveStatus.setText("Live paused • enable at least one advertised sensor"); return; }
            ui.postDelayed(livePollTicker,Math.max(1,delay)); return;
        }
        liveInFlight=true;
        ble.requestPidResult(pid,facts->{
            liveInFlight=false;
            if(!liveRunning) { continuePendingDtc(); return; }
            long sampleNow=android.os.SystemClock.elapsedRealtime();
            liveScheduler.sampled(pid,sampleNow);
            liveRtts.addLast(facts.roundTripMs); if(liveRtts.size()>20) liveRtts.removeFirst();
            liveLast=facts.classification;
            if(facts.complete) {
                String parsed=pid.equals("010C")?ResponseParser.rpm(facts.response):ResponseParser.live(pid,facts.response);
                if(!parsed.equals("Not available") && !parsed.equals("Unrecognized response")) { liveCount++; liveSamples.put(pid,parsed); liveSampleTimes.put(pid,sampleNow); liveRates.sample(pid,sampleNow); renderLivePid(pid,sampleNow); graph(pid,parsed,sampleNow); recordRow(pid,parsed,sampleNow); }
            }
            refreshLiveStats();
            if(liveRunning) ui.post(livePollTicker);
        });
    }
    private void renderLivePid(String pid,long now) {
        String value=liveSamples.get(pid); Long then=liveSampleTimes.get(pid); if(value==null || then==null) return;
        if(!Boolean.TRUE.equals(liveEnabled.get(pid))) { setLivePid(pid,"Disabled • tap to enable"); return; }
        long age=Math.max(0,now-then), period=liveScheduler.configuredPeriod(pid);
        setLivePid(pid,value+" • "+tierName(pid)+" • "+liveRates.display(pid)+" • "+age+" ms"+(age>period*2?" STALE":""));
    }
    private void refreshLiveAges() { long now=android.os.SystemClock.elapsedRealtime(); for(String pid:liveSamples.keySet()) renderLivePid(pid,now); refreshLiveStats(); }
    private void refreshLiveStats() {
        int active=0; for(boolean value:activeLivePids()) if(value) active++;
        long end=liveRunning?android.os.SystemClock.elapsedRealtime():liveStopped; long elapsed=Math.max(1,end-liveStarted), total=0; for(long rtt:liveRtts) total+=rtt;
        long count=liveRunning?liveCount:liveFrozenCount;
        String rate=String.format(Locale.US,"%.1f",count*1000.0/elapsed);
        liveStats.setText("Samples: "+rate+"/s • RTT: "+(liveRtts.isEmpty()?"—":(total/liveRtts.size())+" ms")+" • Active PIDs: "+active+" • Last: "+liveLast);
    }
    private void stopLive(String reason) {
        boolean wasRunning=liveRunning; liveRunning=false; if(wasRunning) { liveStopped=android.os.SystemClock.elapsedRealtime(); liveFrozenCount=liveCount; addSessionHistory(); } recording=false; if(recordLive!=null)recordLive.setText("Start Recording"); ui.removeCallbacks(liveAgeTicker); ui.removeCallbacks(livePollTicker);
        if(startLive!=null) startLive.setEnabled(ble!=null && ble.isReady() && !scanBusy && supportBitmapsReady());
        if(stopLive!=null) stopLive.setEnabled(false);
        if(wasRunning && liveStatus!=null) { liveStatus.setText(reason+"; in-flight response, if any, will not schedule another request."); refreshLiveStats(); }
    }
    private void toggleRecording() {
        if(!liveRunning) { toast("Start live polling before recording"); return; }
        recording=!recording;
        if(recording) { csvRows.clear(); recordingPids.clear(); boolean[] active=activeLivePids(); for(int i=0;i<active.length;i++)if(active[i])recordingPids.add(LivePollScheduler.PIDS[i]); recordingStartedElapsed=android.os.SystemClock.elapsedRealtime(); recordingStartedWall=System.currentTimeMillis(); csvRows.add("elapsed_ms,timestamp_utc,pid,sensor,value"); recordLive.setText("Stop Recording"); toast("Recording selected valid samples (max "+CSV_ROW_CAP+" rows)"); }
        else recordLive.setText("Start Recording");
    }
    private void recordRow(String pid,String value,long now) {
        if(!recording||!recordingPids.contains(pid))return;
        if(csvRows.size()-1>=CSV_ROW_CAP)return;
        long elapsed=Math.max(0,now-recordingStartedElapsed);
        csvRows.add(elapsed+","+CsvExport.quote(CsvExport.utcTimestamp(recordingStartedWall+elapsed))+","+CsvExport.quote(pid)+","+CsvExport.quote(liveLabel(pid))+","+CsvExport.quote(value));
        if(csvRows.size()-1==CSV_ROW_CAP) { recording=false; recordLive.setText("Start Recording"); toast("Recording stopped at "+CSV_ROW_CAP+" rows"); }
    }
    private String liveLabel(String pid){for(int i=3;i<PIDS.length&&i<16;i++)if(PIDS[i].equals(pid))return PID_LABELS[i];return pid;}
    private void saveCsv() { if(csvRows.size()<2){toast("No recorded rows to export");return;} pendingExportType="CSV"; pendingExportName="Z33-ECU-Lab-Live-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date())+".csv"; Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");i.putExtra(Intent.EXTRA_TITLE,pendingExportName);startActivityForResult(i,REQUEST_SAVE_CSV); }
    private void readDtcs() {
        beginDiagnostic(false);
    }
    // Both one-time diagnostic operations share the same serialization and restoration state.
    private TextView diagnosticStatus() { return freezeScan?freezeStatus:dtcStatus; }
    private void beginDiagnostic(boolean freeze) {
        if(ble==null||!ble.isReady()||scanBusy||dtcBusy||dtcInFlight||!foreground)return;
        freezeScan=freeze;
        if(liveRunning) stopLive("Stopped for diagnostic retrieval");
        dtcBusy=true; readDtcs.setEnabled(false); readFreeze.setEnabled(false);
        dtcMayNeedRestore=false; dtcRestoring=false; dtcDeferredFinish=null;
        dtcEpoch++;
        if(freeze) {
            freezeSupport="Not read (queued)";
            java.util.Arrays.fill(freezeResults,"Not read (queued)"); updateFreezeValues();
        } else {
            dtcRows.removeAllViews(); dtcMonitorStatus=null; setDtcMonitor("Not read (queued)");
            for(int i=0;i<3;i++) setDtcResult(i,"Not read (queued)");
        }
        if(liveInFlight) { dtcPending=true; diagnosticStatus().setText("Waiting for in-flight live request…"); return; }
        requestDtcs();
    }
    private void continuePendingDtc() { if(dtcPending && foreground) { dtcPending=false; requestDtcs(); } }
    private void requestDtcs() {
        final int epoch=dtcEpoch;
        diagnosticStatus().setText("Enabling exhaustive timing (LMX1) for diagnostic sequence…");
        dtcMayNeedRestore=true;
        dtcInFlight=true;
        ble.requestTimingMode("LMX1",(response,complete)->{
            dtcInFlight=false;
            if(dtcDeferredFinish!=null) { String message=dtcDeferredFinish; dtcDeferredFinish=null; finishDtcs(message); return; }
            if(epoch!=dtcEpoch || !dtcBusy) return;
            recordLatestTransport();
            if(!foreground || !complete || !adapterOk(response)) { finishDtcs("Diagnostic retrieval cancelled or LMX1 unverified"); return; }
            if(freezeScan) requestFreezeSupport(epoch); else requestDtcSupport(epoch);
        });
    }
    private void requestDtcSupport(int epoch) {
        if(epoch!=dtcEpoch || !dtcBusy) return;
        dtcStatus.setText("Checking Mode 01 PID 01 support…"); dtcInFlight=true;
        ble.requestPidResult("0100",facts->{
            dtcInFlight=false;
            if(dtcDeferredFinish!=null) { String message=dtcDeferredFinish; dtcDeferredFinish=null; finishDtcs(message); return; }
            if(epoch!=dtcEpoch || !dtcBusy) return;
            recordTransport(facts);
            if(!facts.complete) { setDtcMonitor("Mode 01 support check incomplete; PID 01 not read"); finishDtcs("DTC sequence stopped after support-check transport error"); return; }
            if(!ResponseParser.mode01Advertises(facts.response,0,1)) {
                setDtcMonitor("PID 01 monitor status unavailable or not advertised");
                requestDtcCategory(0,epoch); return;
            }
            requestDtcMonitor(epoch);
        });
    }
    private void requestDtcMonitor(int epoch) {
        if(epoch!=dtcEpoch || !dtcBusy) return;
        dtcStatus.setText("Reading ECU MIL / stored-DTC count…"); dtcInFlight=true;
        ble.requestPidResult("0101",facts->{
            dtcInFlight=false;
            if(dtcDeferredFinish!=null) { String message=dtcDeferredFinish; dtcDeferredFinish=null; finishDtcs(message); return; }
            if(epoch!=dtcEpoch || !dtcBusy) return;
            recordTransport(facts);
            if(!facts.complete) { setDtcMonitor("Mode 01 PID 01 transport incomplete"); finishDtcs("DTC sequence stopped after PID 01 transport error"); return; }
            dtcMonitorStatus=ResponseParser.monitorStatus(facts.response);
            setDtcMonitor(dtcMonitorStatus==null?"Mode 01 PID 01 unavailable or malformed":dtcMonitorStatus.summary);
            requestDtcCategory(0,epoch);
        });
    }
    private void requestDtcCategory(int index,int epoch) {
        if(epoch!=dtcEpoch || !dtcBusy) return;
        if(!foreground || ble==null || !ble.isReady()) { finishDtcs("DTC retrieval cancelled"); return; }
        if(index==3) { finishDtcs("Generic emissions DTC sequence finished"); return; }
        dtcStatus.setText("Reading Mode "+DTC_MODES[index]+"…");
        dtcInFlight=true;
        ble.requestPidResult(DTC_MODES[index],facts->{
            dtcInFlight=false;
            if(dtcDeferredFinish!=null) { String message=dtcDeferredFinish; dtcDeferredFinish=null; finishDtcs(message); return; }
            if(epoch!=dtcEpoch || !dtcBusy) return;
            recordTransport(facts);
            String result=facts.complete?ResponseParser.dtcs(DTC_MODES[index],facts.response):"DTC request timed out/partial";
            if(index==0 && dtcMonitorStatus!=null && dtcMonitorStatus.milOn && dtcMonitorStatus.storedDtcCount>0 && result.startsWith("ECU/adapter returned NO DATA"))
                result="INCONSISTENCY: "+dtcMonitorStatus.summary+"; "+result+". Do not conclude no codes.";
            setDtcResult(index,result);
            if(facts.complete) for(String code:ResponseParser.dtcCodes(DTC_MODES[index],facts.response)) addDtcRow(code,DTC_LABELS[index]);
            if(!facts.complete || result.equals("DTC request unavailable") || result.equals("Malformed DTC response") || result.equals("DTC request timed out/partial")) {
                finishDtcs("DTC sequence stopped after request error"); return;
            }
            requestDtcCategory(index+1,epoch);
        });
    }
    private void setDtcResult(int index,String result) { dtcResults[index]=result; dtcViews[index].setText(result); }
    private void addDtcRow(String code,String category) {
        final String content=code+" — "+DtcDescriptions.describe(code);
        Button row=button(content+"\n"+category,0xFF355A76); dtcRows.addView(row);
        row.setOnClickListener(v -> {
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Generic DTC",content)); toast("Code and description copied");
        });
        row.setOnLongClickListener(v -> {
            Intent intent=new Intent(Intent.ACTION_SEND); intent.setType("text/plain"); intent.putExtra(Intent.EXTRA_TEXT,content);
            startActivity(Intent.createChooser(intent,"Share generic DTC")); return true;
        });
    }
    private void updateFreezeValues() { freezeValues.setText(freezeSummary()); }
    private String freezeSummary() {
        StringBuilder s=new StringBuilder("Generic frame 00; support: ").append(freezeSupport);
        for(int i=0;i<freezeResults.length;i++) s.append("\n").append(FreezeFrame.LABELS[i]).append(": ").append(freezeResults[i]==null?"Not read":freezeResults[i]);
        return s.toString();
    }
    private void requestFreezeSupport(int epoch) {
        diagnosticStatus().setText("Reading frame-00 support bitmap…"); dtcInFlight=true;
        ble.requestPidResult("020000",facts -> {
            dtcInFlight=false;
            if(dtcDeferredFinish!=null) { String message=dtcDeferredFinish; dtcDeferredFinish=null; finishDtcs(message); return; }
            if(epoch!=dtcEpoch || !dtcBusy)return;
            recordTransport(facts);
            freezeSupport=facts.complete?FreezeFrame.value("020000",facts.response):"Transport incomplete";
            if(!facts.complete || !FreezeFrame.validSupport(facts.response)) {
                java.util.Arrays.fill(freezeResults,"Not read — support unavailable"); updateFreezeValues();
                finishDtcs("Freeze-frame support unavailable; no PID requests sent"); return;
            }
            boolean[] supported=new boolean[freezeResults.length];
            for(int i=0;i<supported.length;i++) {
                supported[i]=FreezeFrame.advertises(facts.response,FreezeFrame.REQUESTS[i]);
                freezeResults[i]=supported[i]?"Not read (queued)":"Not advertised";
            }
            updateFreezeValues(); requestFreezePid(0,epoch,supported);
        });
    }
    private void requestFreezePid(int index,int epoch,boolean[] supported) {
        if(epoch!=dtcEpoch || !dtcBusy)return;
        if(!foreground || ble==null || !ble.isReady()) { finishDtcs("Freeze-frame retrieval cancelled"); return; }
        while(index<supported.length && !supported[index])index++;
        if(index==supported.length) { finishDtcs("Freeze-frame sequence finished"); return; }
        final int i=index;
        diagnosticStatus().setText("Reading frame 00: "+FreezeFrame.LABELS[i]); dtcInFlight=true;
        ble.requestPidResult(FreezeFrame.REQUESTS[i],facts -> {
            dtcInFlight=false;
            if(dtcDeferredFinish!=null) { String message=dtcDeferredFinish; dtcDeferredFinish=null; finishDtcs(message); return; }
            if(epoch!=dtcEpoch || !dtcBusy)return;
            recordTransport(facts);
            freezeResults[i]=facts.complete?FreezeFrame.value(FreezeFrame.REQUESTS[i],facts.response):"Transport incomplete";
            updateFreezeValues();
            if(!facts.complete) { finishDtcs("Freeze-frame sequence stopped after transport error"); return; }
            requestFreezePid(i+1,epoch,supported);
        });
    }
    private void setDtcMonitor(String result) { dtcMonitorResult=result; if(dtcMonitor!=null) dtcMonitor.setText(result); }
    private void finishDtcs(String message) {
        if(dtcMayNeedRestore) {
            if(dtcInFlight || dtcRestoring) { dtcDeferredFinish=message; diagnosticStatus().setText(message+"; waiting to restore LMX0…"); return; }
            if(ble!=null && ble.isReady()) { restoreDtcTiming(message); return; }
            dtcMayNeedRestore=false;
        }
        finalizeDtcs(message);
    }
    private void restoreDtcTiming(String message) {
        dtcRestoring=true; dtcInFlight=true; diagnosticStatus().setText("Restoring normal timing (LMX0)…");
        ble.requestTimingMode("LMX0",(response,complete)->{
            dtcInFlight=false; dtcRestoring=false; dtcMayNeedRestore=false; recordLatestTransport();
            String finalMessage=dtcDeferredFinish==null?message:dtcDeferredFinish; dtcDeferredFinish=null;
            finalizeDtcs(finalMessage+"; LMX0 restore "+(complete&&adapterOk(response)?"succeeded":"failed"));
        });
    }
    private void finalizeDtcs(String message) {
        dtcEpoch++; dtcBusy=false; dtcPending=false; readDtcs.setEnabled(true); readFreeze.setEnabled(true); diagnosticStatus().setText(message);
        if(freezeScan) {
            if(freezeSupport.equals("Not read (queued)"))freezeSupport="Not read (sequence cancelled)";
            for(int i=0;i<freezeResults.length;i++) if("Not read (queued)".equals(freezeResults[i]))freezeResults[i]="Not read (sequence cancelled)";
            updateFreezeValues();
        }
        for(int i=0;i<3;i++) if(dtcResults[i].equals("Not read (queued)")) setDtcResult(i,"Not read (sequence cancelled)");
        if(dtcMonitorResult.equals("Not read (queued)")) setDtcMonitor("Not read (sequence cancelled)");
    }
    private String dtcSummary() {
        return "Generic emissions OBD only; enhanced module codes not included. NO DATA does not establish support or absence of faults. "
                +"ECU MIL / stored-DTC count (01/01): "+dtcMonitorResult+"; Stored (03): "+dtcResults[0]+"; Pending (07): "+dtcResults[1]+"; Permanent (0A): "+dtcResults[2];
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
        return "Z33 ECU Lab v0.7.0 — read-only identification\n"
              + "Device: 2005 Nissan 350Z 5AT (candidate MEC61-530 D1)\n"
               + "Vehicle diagnostic protocol: "+detectedProtocol+"\n"
               + "Adapter firmware: "+adapterFirmware+"\n"
               + "LMX0 restore after exhaustive sequence: "+lmx0Restore+"\n"
                + "Command transport (sanitized):\n"+transportReport
               + "Live session (compact, no sample log): "+liveSessionSummary()+"\n"
               + "DTC outcomes (no raw payloads): "+dtcSummary()+"\n"
               + "Freeze frame (no raw payloads): "+freezeSummary()+"\n"
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
        pendingExportType="Report text"; pendingExportName="Z33-ECU-Lab-Report-" + new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date()) + ".txt";
        Intent saveIntent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        saveIntent.addCategory(Intent.CATEGORY_OPENABLE);
        saveIntent.setType("text/plain");
        saveIntent.putExtra(Intent.EXTRA_TITLE,pendingExportName);
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
        return "{\n  \"app\":\"Z33 ECU Lab 0.7.0\",\n  \"protocol\":\""+json(detectedProtocol)+"\",\n"
                +"  \"adapterFirmware\":\""+json(adapterFirmware)+"\",\n  \"lmx0Restore\":\""+json(lmx0Restore)+"\",\n"
                +"  \"liveSession\":\""+json(liveSessionSummary())+"\",\n"
                +"  \"genericDtcSummary\":\""+json(dtcSummary())+"\",\n"
                +"  \"genericFreezeFrameSummary\":\""+json(freezeSummary())+"\",\n"
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
        long elapsed=liveStarted==0?0:Math.max(1,(liveRunning?android.os.SystemClock.elapsedRealtime():liveStopped)-liveStarted);
        long count=liveRunning?liveCount:liveFrozenCount;
        return "elapsedMs="+elapsed+", samples="+count+", rate="+(elapsed==0?"0.0":String.format(Locale.US,"%.1f",count*1000.0/elapsed))
                +"/s, avgRtt="+(liveRtts.isEmpty()?"—":total/liveRtts.size()+"ms")+", active="+active+", last="+liveLast;
    }
    private static String json(String s) { return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r",""); }
    private void saveJsonReport(){
        pendingExportType="Report JSON"; pendingExportName="Z33-ECU-Lab-Report-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date())+".json";
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,pendingExportName); startActivityForResult(i,REQUEST_SAVE_JSON);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if((request!=REQUEST_SAVE_REPORT && request!=REQUEST_SAVE_JSON && request!=REQUEST_SAVE_CSV) || result!=RESULT_OK || data==null || data.getData()==null)return;
        try (java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())) {
            if(out==null) throw new java.io.IOException("Storage provider did not open destination");
            String content=request==REQUEST_SAVE_JSON?safeJsonReport():request==REQUEST_SAVE_CSV?android.text.TextUtils.join("\n",csvRows):safeReport(); out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            addExportHistory(pendingExportType==null?(request==REQUEST_SAVE_CSV?"CSV":"Report"):pendingExportType,pendingExportName==null?"Exported file":pendingExportName,request==REQUEST_SAVE_CSV?csvRows.size()-1:-1);
            pendingExportName=pendingExportType=null; toast(request==REQUEST_SAVE_CSV?"CSV saved":"Report saved (VIN masked)");
        }catch(Exception error){toast("Cannot save report: "+error.getMessage());}
    }
    private static String historyField(String value) { return value==null?"":value.replace('|','/').replace('\n',' ').replace('\r',' '); }
    private void prependHistory(String key,String entry) {
        String old=historyPreferences.getString(key,""); String[] rows=old.isEmpty()?new String[0]:old.split("\\n");
        StringBuilder saved=new StringBuilder(entry); for(int i=0;i<rows.length&&i<19;i++) if(!rows[i].isEmpty()) saved.append('\n').append(rows[i]);
        historyPreferences.edit().putString(key,saved.toString()).apply(); refreshHistory();
    }
    private void addExportHistory(String type,String name,int rows) {
        // SAF destinations are user-owned; do not retain their URI or attempt a later re-share.
        prependHistory("exports",System.currentTimeMillis()+"|"+historyField(type)+"|"+historyField(name)+"|"+rows);
    }
    private void addSessionHistory() {
        if(liveStartedWall<=0)return;
        prependHistory("sessions",liveStartedWall+"|"+Math.max(0,liveStopped-liveStarted)+"|"+liveFrozenCount+"|"+liveSelectedCount);
        liveStartedWall=0;
    }
    private String utc(long time) { SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'",Locale.US); f.setTimeZone(TimeZone.getTimeZone("UTC")); return f.format(new Date(time)); }
    private void refreshHistory() {
        if(history==null)return; StringBuilder out=new StringBuilder();
        String exports=historyPreferences.getString("exports","");
        out.append("Exports (latest 20; re-share unavailable: saved destinations are not retained):");
        if(exports.isEmpty()) out.append("\nNone"); else for(String row:exports.split("\\n")) { String[] f=row.split("\\|",-1); if(f.length==4) { int count=parseHistoryCount(f[3]); out.append("\n").append(utc(parseHistoryTime(f[0]))).append(" • ").append(f[1]).append(" • ").append(f[2]).append(count>=0?" • "+count+" rows":""); } }
        String sessions=historyPreferences.getString("sessions","");
        out.append("\n\nLive sessions (latest 20; no values retained):");
        if(sessions.isEmpty()) out.append("\nNone"); else for(String row:sessions.split("\\n")) { String[] f=row.split("\\|",-1); if(f.length==4) out.append("\n").append(utc(parseHistoryTime(f[0]))).append(" • ").append(f[1]).append(" ms • ").append(f[2]).append(" valid samples • ").append(f[3]).append(" sensors"); }
        history.setText(out);
    }
    private long parseHistoryTime(String value) { try{return Long.parseLong(value);}catch(Exception ignored){return 0;} }
    private int parseHistoryCount(String value) { try{return Integer.parseInt(value);}catch(Exception ignored){return -1;} }
    private void confirmClearHistory() { new AlertDialog.Builder(this).setTitle("Clear local history?").setMessage("Deletes only this app's sanitized export metadata and live-session summaries. It cannot delete files saved through the system picker.").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{historyPreferences.edit().clear().apply();refreshHistory();}).show(); }
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
            if (!ready) { scanBusy = false; stopLive("Stopped: BLE not ready"); liveInFlight=false; dtcInFlight=false; dtcMayNeedRestore=false; dtcRestoring=false; dtcDeferredFinish=null; mode01_00=mode01_20=mode01_40=""; if(dtcBusy)finalizeDtcs("Disconnected; no DTC result"); }
            else if(startLive!=null) startLive.setEnabled(!scanBusy && !liveRunning && supportBitmapsReady());
        });
    }
    @Override public void onError(String message) {
        ui.post(() -> { stopLive("Stopped: "+message); dtcInFlight=false; if(dtcBusy && dtcMayNeedRestore && !dtcRestoring && ble!=null && ble.isReady()) finishDtcs("Diagnostic retrieval stopped: "+message); else { dtcMayNeedRestore=false; dtcRestoring=false; dtcDeferredFinish=null; if(dtcBusy)finalizeDtcs("Diagnostic retrieval stopped: "+message); } status.setText(message); scanState.setText(message); addLog("ERROR  " + message); connect.setEnabled(true); read.setEnabled(false); scanBusy = false; });
    }
    @Override protected void onStart() { super.onStart(); foreground=true; }
    @Override protected void onStop() { foreground=false; stopLive("Stopped with app lifecycle"); if(dtcBusy)finishDtcs("DTC retrieval cancelled with app lifecycle"); super.onStop(); }
    @Override protected void onDestroy() {
        stopLive("Stopped on destroy");
        if (ble != null) ble.close();
        super.onDestroy();
    }
}
