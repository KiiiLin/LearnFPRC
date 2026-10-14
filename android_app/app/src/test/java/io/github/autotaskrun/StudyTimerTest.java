package io.github.autotaskrun;
import org.junit.Test;
import static org.junit.Assert.*;
public class StudyTimerTest {
    @Test public void neverCompletesAtOldFifteenSecondGate(){
        StudyTimer timer=new StudyTimer(1000);assertFalse(timer.complete(16000));assertEquals(45,timer.remaining(16000));
        assertFalse(timer.complete(60999));assertTrue(timer.complete(61000));
    }
    @Test public void manualPauseOrBackgroundReturnRestartsMinute(){
        StudyTimer timer=new StudyTimer(0);timer.reset(45000);assertEquals(60,timer.remaining(45000));
        assertFalse(timer.complete(60000));assertTrue(timer.complete(105000));
    }
}
