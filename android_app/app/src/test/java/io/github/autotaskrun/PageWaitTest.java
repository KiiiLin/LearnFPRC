package io.github.autotaskrun;
import org.junit.Test;
import static org.junit.Assert.*;
public class PageWaitTest {
    @Test public void loadingDoesNotResetFifteenSecondDeadline(){
        PageWait wait=new PageWait(1000);assertFalse(wait.ready(false,5000));assertFalse(wait.ready(false,14000));
        assertFalse(wait.ready(true,15999));assertFalse(wait.ready(true,16000));assertEquals(16000,wait.deadline);
    }
    @Test public void requiresTwoConsecutiveReadyFrames(){
        PageWait wait=new PageWait(0);assertFalse(wait.ready(true,1000));assertFalse(wait.ready(false,2000));
        assertFalse(wait.ready(true,3000));assertTrue(wait.ready(true,4000));
    }
}
