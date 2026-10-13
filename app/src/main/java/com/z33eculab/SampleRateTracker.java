package com.z33eculab;

import java.util.HashMap;
import java.util.Map;

/** Bounded per-sensor valid-sample interval EWMA; timestamps are elapsedRealtime milliseconds. */
public final class SampleRateTracker {
    private static final int CAP=16;
    private static final class State { long last, interval; }
    private final Map<String,State> states=new HashMap<>();
    public void reset(){states.clear();}
    public void sample(String pid,long now){State s=states.get(pid);if(s==null){if(states.size()>=CAP)return;s=new State();states.put(pid,s);}if(s.last>0&&now>s.last){long d=now-s.last;s.interval=s.interval==0?d:(s.interval*3+d)/4;}if(now>=s.last)s.last=now;}
    public String display(String pid){State s=states.get(pid);if(s==null||s.interval<=0)return "rate —";if(s.interval>=1000)return "every "+s.interval+" ms";return String.format(java.util.Locale.US,"%.1f Hz",1000.0/s.interval);}
}
