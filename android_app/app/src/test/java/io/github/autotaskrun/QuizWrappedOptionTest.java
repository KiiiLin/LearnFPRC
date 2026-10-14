package io.github.autotaskrun;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import io.github.autotaskrun.PointsEntryLocator.Label;
import static org.junit.Assert.*;

public class QuizWrappedOptionTest {
    private static final String FIRST="先关闭室内电源和燃气，向高层或楼顶转移";
    private static final String SECOND="先抢救贵重物品，再做断电处理";
    private Label n(String text,int left,int top,int right,int bottom){return new Label(text,left,top,right,bottom,true);}
    private List<Label> page(){return new ArrayList<>(Arrays.asList(
            n("单选题",141,398,321,473),n("2 /5",975,362,1113,509),
            n("城市遭遇极端强降雨，低层住户家中若发生积水倒灌，正确处置方式是         。",108,593,1113,773),
            n("A.",138,893,213,968),n(FIRST,231,890,1083,1049),
            n("B.",138,1172,213,1247),n(SECOND,231,1169,1083,1250),
            n("出题：“学习强国”学习平台",108,1367,690,1460),n("查看提示",942,1550,1113,1610)));}
    private QuizDocument scan(List<Label> labels){QuizDocument d=new QuizDocument();for(int i=0;i<3;i++)d.add(1220,2712,labels);d.atBottom();return d;}
    @Test public void actualSecondQuestionKeepsBothLinesUnderA(){
        QuizDocument d=scan(page());assertTrue(d.complete());assertEquals(QuizDocument.clean(FIRST),d.options.get("A"));assertEquals(QuizDocument.clean(SECOND),d.options.get("B"));
    }
    @Test public void threeLineIndependentTextNodeIsNotDropped(){
        List<Label> labels=page();labels.set(4,n(FIRST+"，保持镇定等待救援",231,890,1083,1140));
        QuizDocument d=scan(labels);assertTrue(d.complete());assertEquals(QuizDocument.clean(FIRST+"，保持镇定等待救援"),d.options.get("A"));
    }
    @Test public void paragraphNodeAndOcrLinesDoNotDuplicateAnswer(){
        List<Label> labels=page();labels.add(new Label("A. 先关闭室内电源和燃气，",138,893,1083,968,false));
        labels.add(new Label("向高层或楼顶转移",231,975,1083,1049,false));
        QuizDocument d=scan(labels);assertTrue(d.complete());assertEquals(QuizDocument.clean(FIRST),d.options.get("A"));
    }
    @Test public void selectedWrappedOptionUsesCurrentScreenBounds(){
        QuizDocument d=scan(page());QuizDocument.Selection selection=new QuizDocument.Selection(d,Arrays.asList("A"));
        List<Label> moved=new ArrayList<>();for(Label l:page())moved.add(new Label(l.text,l.left,l.top+120,l.right,l.bottom+120,l.node));
        Label target=selection.next(1220,2712,moved);assertNotNull(target);assertTrue(target.y()>=1010&&target.y()<=1169);
        assertEquals(QuizDocument.clean(FIRST),QuizDocument.value(target.text));
    }
    @Test public void doesNotAttachTextCrossingNextOption(){
        List<Label> labels=page();labels.set(4,n(FIRST,231,890,1083,1260));
        assertFalse(scan(labels).complete());
    }
}
