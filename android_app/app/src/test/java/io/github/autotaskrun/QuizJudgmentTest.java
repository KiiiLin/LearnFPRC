package io.github.autotaskrun;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import io.github.autotaskrun.PointsEntryLocator.Label;
import static org.junit.Assert.*;

public class QuizJudgmentTest {
    private static final String STATEMENT="一国的金融管理部门为达到稳定货币、维护金融业正常秩序等目的，依照国家法律、行政法规的规定，对金融机构及其经营活动实施外部监督、稽核、检查和对其违法违规行为进行处罚等的一系列行为称为金融监管。";
    private Label n(String text,int left,int top,int right,int bottom){return new Label(text,left,top,right,bottom,true);}
    private List<Label> page(boolean reverse){return Arrays.asList(
            n("单选题",141,398,321,473),n("1 /5",990,362,1113,509),
            n(STATEMENT+"         ",108,593,1113,1127),
            n("来源：参见《中国大百科全书》（第三版网络版） （中国大百科全书出版社）",108,1175,1113,1355),
            n("A.",138,1475,213,1550),n(reverse?"错误":"正确",231,1472,1083,1553),
            n("B.",138,1676,213,1751),n(reverse?"正确":"错误",231,1673,1083,1754),
            n("出题：“学习强国”学习平台",108,1871,690,1964),n("查看提示",942,2054,1113,2114));}
    private QuizDocument doc(boolean reverse){QuizDocument d=new QuizDocument();d.add(1220,2712,page(reverse));d.atBottom();assertTrue(d.complete());return d;}
    @Test public void actualFinanceLogSelectsCorrectWithoutColor(){
        List<Label> before=page(false),after=new ArrayList<>(before);after.add(n("提示",558,929,663,1001));after.add(n(STATEMENT,57,1124,1164,1487));
        QuizHintText.Result result=QuizHintText.resolve(QuizHintText.stem(2712,before),doc(false),false,2712,1001,before,after);
        assertEquals(Arrays.asList("A"),result.answers);assertTrue(result.method.startsWith("无障碍弹窗正文"));
    }
    @Test public void attributionIsNotPartOfWrongOption(){assertEquals("错误",doc(false).options.get("B"));}
    @Test public void detectsJudgmentFromBothExactOptionValues(){assertTrue(QuizHintText.isJudgment(doc(false)));}
    @Test public void reversedOptionOrderUsesCorrectLabel(){
        assertEquals(Arrays.asList("B"),QuizHintText.solve(QuizHintText.template(STATEMENT+"    "),STATEMENT,doc(true),false));
    }
    @Test public void explicitWrongVerdictSelectsWrongOption(){
        assertEquals(Arrays.asList("B"),QuizHintText.solve("题干不同陈述","该说法是错误。事实与题目不同。",doc(false),false));
        assertEquals(Arrays.asList("A"),QuizHintText.solve("题干不同陈述","参考答案：错误",doc(true),false));
    }
    @Test public void explicitCorrectVerdictWorks(){
        assertEquals(Arrays.asList("A"),QuizHintText.solve("题干不同陈述","本题判断：正确。",doc(false),false));
    }
    @Test public void unrelatedHintDoesNotImplyFalse(){assertTrue(QuizHintText.solve(QuizHintText.template(STATEMENT),"本题涉及金融市场的许多不同概念。",doc(false),false).isEmpty());}
    @Test public void changedNegationDoesNotGuess(){assertTrue(QuizHintText.solve(QuizHintText.template(STATEMENT),STATEMENT.replace("称为","不能称为"),doc(false),false).isEmpty());}
    @Test public void quotedStatementFollowedByRejectionDoesNotSelectCorrect(){
        assertTrue(QuizHintText.solve(QuizHintText.template(STATEMENT),STATEMENT+"这种表述不成立。",doc(false),false).isEmpty());
    }
    @Test public void conflictingVerdictsRefreshInsteadOfGuessing(){
        assertTrue(QuizHintText.solve(QuizHintText.template(STATEMENT),"参考答案：正确。答案：错误。",doc(false),false).isEmpty());
    }
    @Test public void decimalDifferenceIsNotErased(){
        assertTrue(QuizHintText.solve(QuizHintText.template("本次测量的长度结果为1.5厘米"),"本次测量的长度结果为15厘米",doc(false),false).isEmpty());
        assertEquals(Arrays.asList("A"),QuizHintText.solve(QuizHintText.template("本次测量的长度结果为1.5厘米"),"本次测量的长度结果为1.5厘米",doc(false),false));
    }
    @Test public void punctuationAndTrailingAnswerSlotAreTolerated(){
        assertEquals(Arrays.asList("A"),QuizHintText.solve(QuizHintText.template(STATEMENT+"（ ）"),STATEMENT.replace('，',','),doc(false),false));
    }
    @Test public void standaloneVerdictIsAccepted(){assertEquals(Arrays.asList("B"),QuizHintText.solve("题干的陈述","错误。",doc(false),false));}
    @Test public void shortCompleteStatementIsAccepted(){assertEquals(Arrays.asList("A"),QuizHintText.solve("地球绕太阳转","地球绕太阳转。",doc(false),false));}
}
