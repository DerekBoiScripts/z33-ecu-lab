package com.z33eculab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import java.util.ArrayDeque;

/** Bounded display-only trace: x is elapsedRealtime over the most recent 30 seconds. */
public final class MiniGraphView extends View {
    private static final int CAP=120; private static final long WINDOW_MS=30000;
    private static final class Sample { final long time; final float value; Sample(long t,float v){time=t;value=v;} }
    private final ArrayDeque<Sample> samples=new ArrayDeque<>(); private final Paint paint=new Paint(1);
    public MiniGraphView(Context c){super(c);paint.setColor(0xFF4BE6C7);paint.setStrokeWidth(3);setMinimumHeight((int)(72*c.getResources().getDisplayMetrics().density));setContentDescription("Live graph, last 30 seconds. Tap to clear.");}
    public void add(float value,long elapsedRealtime){if(!Float.isFinite(value))return;while(samples.size()>=CAP)samples.removeFirst();samples.addLast(new Sample(elapsedRealtime,value));invalidate();}
    public void clear(){samples.clear();invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(samples.size()<2)return;long end=samples.peekLast().time,start=end-WINDOW_MS;float lo=Float.MAX_VALUE,hi=-Float.MAX_VALUE;int visible=0;for(Sample s:samples)if(s.time>=start){lo=Math.min(lo,s.value);hi=Math.max(hi,s.value);visible++;}if(visible<2)return;if(!(hi>lo))hi=lo+1;float lastX=0,lastY=0;boolean have=false;for(Sample s:samples)if(s.time>=start){float x=getWidth()*(s.time-start)/(float)WINDOW_MS;float y=getHeight()-(s.value-lo)*getHeight()/(hi-lo);if(have)c.drawLine(lastX,lastY,x,y,paint);lastX=x;lastY=y;have=true;}}
}
