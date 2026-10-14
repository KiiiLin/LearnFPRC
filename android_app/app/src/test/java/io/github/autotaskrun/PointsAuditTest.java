package io.github.autotaskrun;

import org.junit.Test;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class PointsAuditTest {
    @Test public void existingPointsAreNotCountedAsEarnings(){
        PointsAudit.Ledger ledger=new PointsAudit.Ledger(Arrays.asList("articles","quiz","supplement"));
        ledger.begin("articles",8);ledger.finish("articles",10);ledger.begin("quiz",5);ledger.finish("quiz",5);
        assertEquals(2,ledger.gained());assertTrue(ledger.complete());
        ledger.begin("articles",10);ledger.finish("articles",12);
        assertEquals(4,ledger.gained());assertEquals(Integer.valueOf(8),ledger.before.get("articles"));
    }
    @Test public void missingVerificationIsNeverZeroOrFullSuccess(){
        PointsAudit.Ledger ledger=new PointsAudit.Ledger(Arrays.asList("articles","videos"));
        ledger.begin("articles",0);ledger.finish("articles",8);ledger.begin("videos",0);
        assertEquals(8,ledger.gained());assertFalse(ledger.complete());
        ledger.unknown("articles");assertEquals(0,ledger.gained());
    }
    @Test public void scoreGoingBackwardsIsNotCounted(){
        PointsAudit.Ledger ledger=new PointsAudit.Ledger(Arrays.asList("quiz"));ledger.begin("quiz",3);ledger.finish("quiz",2);
        assertFalse(ledger.complete());assertEquals(0,ledger.gained());
    }
    @Test public void lowerScoreOnRecheckDoesNotAuthorizeMoreActions(){
        PointsAudit.Ledger ledger=new PointsAudit.Ledger(Arrays.asList("articles"));ledger.begin("articles",10);ledger.finish("articles",12);
        assertThrows(IllegalArgumentException.class,()->ledger.begin("articles",2));assertEquals(2,ledger.gained());
    }
    @Test public void supplementationOnlyUsesSelectedTasks(){
        assertEquals(Arrays.asList("articles","quiz"),PointsAudit.scope(Arrays.asList("articles","supplement","quiz","articles")));
        assertTrue(PointsAudit.scope(Arrays.asList("supplement")).isEmpty());
    }
    @Test public void tenOfTwelveStopsAfterOneSuccessfulMinute(){
        PointsAudit.Budget budget=new PointsAudit.Budget();assertTrue(budget.canRun(10,12));budget.record(10,12);
        assertFalse(budget.canRun(12,12));assertEquals(1,budget.actions);
    }
    @Test public void onePointIncrementRequiresRecheckAndAnotherAction(){
        PointsAudit.Budget budget=new PointsAudit.Budget();budget.record(10,11);assertTrue(budget.canRun(11,12));budget.record(11,12);assertFalse(budget.canRun(12,12));
    }
    @Test public void twoNoGainAttemptsStopAndDoNotPretendSuccess(){
        PointsAudit.Budget budget=new PointsAudit.Budget();budget.record(10,10);assertTrue(budget.canRun(10,12));budget.record(10,10);assertFalse(budget.canRun(10,12));
    }
    @Test public void repeatedPartialGainsRemainBounded(){
        PointsAudit.Budget budget=new PointsAudit.Budget();for(int i=0;i<12;i++)budget.record(0,1);assertFalse(budget.canRun(1,12));
    }
    @Test public void progressParsingRequiresTheRightTaskMaximum(){
        assertEquals(Integer.valueOf(10),PointsAudit.parse("10 分 / 12 分",12));assertEquals(Integer.valueOf(5),PointsAudit.parse("5/5",5));
        assertNull(PointsAudit.parse("0/55",5));assertNull(PointsAudit.parse("5/12",5));assertNull(PointsAudit.parse("13/12",12));assertNull(PointsAudit.parse("每日上限12分",12));
    }
    @Test public void supplementCannotBeMovedOrPushedAwayFromEnd(){
        List<String> tasks=new ArrayList<>(Arrays.asList("supplement","articles","quiz"));TaskOrder.pinSupplement(tasks);
        assertEquals(Arrays.asList("articles","quiz","supplement"),tasks);assertFalse(TaskOrder.move(tasks,"supplement","articles"));
        TaskOrder.move(tasks,"articles","supplement");assertEquals(Arrays.asList("quiz","articles","supplement"),tasks);
    }
}
