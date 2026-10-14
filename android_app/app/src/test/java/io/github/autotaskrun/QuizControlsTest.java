package io.github.autotaskrun;

import org.junit.Test;
import java.util.Arrays;
import io.github.autotaskrun.PointsEntryLocator.Label;
import static org.junit.Assert.*;

public class QuizControlsTest {
    private Label n(String t,int x,int y,int r,int b){return new Label(t,x,y,r,b,true);}
    @Test public void fallbackAWorksEvenWhenWholeDocumentIsIncomplete(){
        assertNotNull(QuizControls.optionA(1220,2712,Arrays.asList(n("A.",138,893,213,968),n("两行选项第一行\n第二行",231,890,1083,1049))));
    }
    @Test public void noOptionAMustNotUseAnUnrelatedLetter(){
        assertNull(QuizControls.optionA(1220,2712,Arrays.asList(n("来源A",108,800,600,870),n("B.其他",138,1172,1083,1247))));
    }
    @Test public void feedbackIsNotRoundCompletion(){
        assertFalse(QuizControls.result(Arrays.asList(n("回答错误",100,1500,600,1600),n("查看解析",100,1800,600,1900),n("下一题",900,160,1150,230))));
    }
    @Test public void nextButtonMustBeAControlNotAParagraphMention(){
        assertNull(QuizControls.button(1220,2712,Arrays.asList(n("请点击下一题继续",100,400,1000,800)),"下一题"));
        assertNotNull(QuizControls.button(1220,2712,Arrays.asList(n("下一题",900,160,1150,230)),"下一题"));
    }
    @Test public void unknownTypeNeedsAQuestionHeader(){
        assertEquals(2,ScreenRules.questionNumber(1220,2712,Arrays.asList(n("排序题",141,398,321,473),n("2 /5",975,362,1113,509))));
        assertEquals(0,ScreenRules.questionNumber(1220,2712,Arrays.asList(n("积分",141,398,321,473),n("2 /5",975,362,1113,509))));
    }
}
