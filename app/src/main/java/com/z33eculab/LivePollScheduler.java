package com.z33eculab;

import java.util.HashMap;
import java.util.Map;

/** Deadline scheduler; callers issue exactly one selected request at a time. */
public final class LivePollScheduler {
    public static final String[] PIDS={"010C","010D","0111","0110","010E","0106","0107","0105","010F","0104","010B","012F","0142"};
    private static final long[] PERIODS={250,500,500,500,1000,1000,1000,2000,2000,1000,1000,2000,2000};
    private final Map<String,Long> configuredPeriods=new HashMap<>();
    private final Map<String,Long> due=new HashMap<>();
    public void reset() { due.clear(); }
    /** Tier 0/1/2 maps to fast/default/slow; no request is ever overlapped by this scheduler. */
    public void setTier(String pid,int tier) {
        long base=period(pid);
        if(base==0 || tier<0 || tier>2) throw new IllegalArgumentException("Unknown PID or priority tier");
        configuredPeriods.put(pid,tier==0?base/2:tier==1?base:base*2);
        due.remove(pid);
    }
    public long configuredPeriod(String pid) { return configuredPeriods.containsKey(pid)?configuredPeriods.get(pid):period(pid); }
    public String mostOverdue(boolean[] active, long now) {
        String selected=null; long overdue=Long.MIN_VALUE;
        for(int i=0;i<PIDS.length;i++) if(active[i]) {
            long deadline=due.containsKey(PIDS[i])?due.get(PIDS[i]):0;
            long value=now-deadline;
            if(selected==null || value>overdue) { selected=PIDS[i]; overdue=value; }
        }
        return overdue>=0?selected:null;
    }
    /** Milliseconds until an active PID is due; -1 means no active PIDs. */
    public long delayUntilNext(boolean[] active, long now) {
        long delay=Long.MAX_VALUE;
        for(int i=0;i<PIDS.length;i++) if(active[i]) {
            if(!due.containsKey(PIDS[i])) return 0;
            delay=Math.min(delay,Math.max(0,due.get(PIDS[i])-now));
        }
        return delay==Long.MAX_VALUE?-1:delay;
    }
    public void sampled(String pid, long now) {
        if(period(pid)>0) due.put(pid,now+configuredPeriod(pid));
    }
    public static long period(String pid) {
        for(int i=0;i<PIDS.length;i++) if(PIDS[i].equals(pid)) return PERIODS[i];
        return 0;
    }
}
