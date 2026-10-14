package io.github.autotaskrun;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class TaskOrderTest {
    @Test public void movingDownOneActuallyChangesOrder(){
        List<String> order=new ArrayList<>(Arrays.asList("articles","videos","quiz"));
        assertTrue(TaskOrder.move(order,"articles","videos"));assertEquals(Arrays.asList("videos","articles","quiz"),order);
    }
    @Test public void movingUpAndToBottom(){
        List<String> order=new ArrayList<>(Arrays.asList("articles","videos","quiz"));
        TaskOrder.move(order,"quiz","articles");assertEquals(Arrays.asList("quiz","articles","videos"),order);
        TaskOrder.move(order,"quiz","videos");assertEquals(Arrays.asList("articles","videos","quiz"),order);
    }
    @Test public void unknownAndSameTaskAreNoOps(){
        List<String> order=new ArrayList<>(Arrays.asList("articles","videos"));
        assertFalse(TaskOrder.move(order,"articles","articles"));assertFalse(TaskOrder.move(order,"invalid","videos"));
        assertEquals(Arrays.asList("articles","videos"),order);
    }
}
