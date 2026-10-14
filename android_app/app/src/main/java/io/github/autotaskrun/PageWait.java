package io.github.autotaskrun;

/** A single deadline shared by capture, OCR and polling; never restarted by a loading frame. */
final class PageWait {
    final long deadline;
    private int consecutive;
    PageWait(long now){deadline=now+15000;}
    boolean ready(boolean matched,long now){
        if(now>=deadline){consecutive=0;return false;}
        consecutive=matched?consecutive+1:0;return consecutive>=2;
    }
}
