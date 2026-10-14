package io.github.autotaskrun;

final class StudyTimer {
    private long start;
    StudyTimer(long now){start=now;}
    void reset(long now){start=now;}
    int remaining(long now){return (int)Math.max(0,(60000-Math.max(0,now-start)+999)/1000);}
    boolean complete(long now){return now-start>=60000;}
}
