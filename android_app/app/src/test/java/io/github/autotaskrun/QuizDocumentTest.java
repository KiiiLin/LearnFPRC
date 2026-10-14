package io.github.autotaskrun;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import io.github.autotaskrun.PointsEntryLocator.Label;
import static org.junit.Assert.*;

public class QuizDocumentTest {
    private Label l(String s,int y){return new Label(s,80,y,850,y+40,false);}
    private Label hint(){return new Label("查看提示",750,880,970,920,false);}
    private void add(QuizDocument d,Label...labels){d.add(1000,1000,Arrays.asList(labels));}
    @Test public void singleScreenWrappedOptions(){
        QuizDocument d=new QuizDocument();add(d,l("题目内容",200),l("A. 第一行",350),l("第二行",410),l("B. 其他答案",550),hint());d.atBottom();
        assertTrue(d.complete());assertEquals("第一行第二行",d.options.get("A"));assertEquals(Arrays.asList("A"),d.choose(Arrays.asList("第一行第二行"),false));
    }
    @Test public void longQuestionCollectsAllSixOptionsAcrossThreeScreens(){
        QuizDocument d=new QuizDocument();
        add(d,l("很长的题目",250),l("A. 选项甲",650),l("B. 选项乙",810));
        add(d,l("A. 选项甲",180),l("B. 选项乙",340),l("C. 选项丙",570),l("D. 选项丁",800));
        add(d,l("C. 选项丙",180),l("D. 选项丁",410),l("E. 选项戊",560),l("F. 选项己",720),hint());d.atBottom();
        assertTrue(d.complete());assertEquals(6,d.options.size());
        assertEquals(Arrays.asList("A","D","F"),d.choose(Arrays.asList("选项甲","选项丁","选项己"),true));
    }
    @Test public void optionContinuationWithoutItsLetterIsJoinedByOverlap(){
        QuizDocument d=new QuizDocument();add(d,l("A. 前半部分文字",700),l("跨屏重叠文字",800));
        add(d,l("跨屏重叠文字",200),l("后半部分文字",270),l("B. 其他选项",500),hint());d.atBottom();
        assertTrue(d.complete());assertEquals("前半部分文字跨屏重叠文字后半部分文字",d.options.get("A"));
    }
    @Test public void missingOverlapStopsInsteadOfGuessing(){
        QuizDocument d=new QuizDocument();add(d,l("A. 原文开头",700));
        add(d,l("完全不同的尾部",200),l("B. 其他选项",500),hint());d.atBottom();assertFalse(d.complete());
    }
    @Test public void repeatedTopScreenDoesNotAppendStemToLastOption(){
        QuizDocument d=new QuizDocument();Label[] page={l("很长的题目说明",200),l("A. 正确",350),l("B. 错误",500),hint()};
        add(d,page);add(d,page);add(d,page);d.atBottom();assertTrue(d.complete());assertEquals("错误",d.options.get("B"));
    }
    @Test public void refusesMissingOptionLetterEvenIfHintsMatchVisibleOnes(){
        QuizDocument d=new QuizDocument();add(d,l("A. 第一项",400),l("C. 第三项",600),hint());d.atBottom();
        assertFalse(d.complete());assertTrue(d.choose(Arrays.asList("第一项","第三项"),true).isEmpty());
    }
    @Test public void pinnedHintDoesNotProveBottom(){
        QuizDocument d=new QuizDocument();add(d,l("A. 第一项",400),l("B. 第二项",600),hint());assertFalse(d.complete());
    }
    @Test public void stationaryScreenWithoutEndMarkerIsIncomplete(){
        QuizDocument d=new QuizDocument();add(d,l("A. 第一项",400),l("B. 第二项",600));d.atBottom();assertFalse(d.complete());
    }
    @Test public void optionsBeyondOldScreenBandAndSeparateLetter(){
        QuizDocument d=new QuizDocument();add(d,l("A.",190),l("第一项",240),l("B. 第二项",820),hint());d.atBottom();
        assertTrue(d.complete());assertEquals("第一项",d.options.get("A"));
    }
    @Test public void accessibilityAndOcrDuplicatesNotConcatenated(){
        QuizDocument d=new QuizDocument();
        add(d,new Label("A. 第一行第二行",80,340,850,450,true),l("A. 第一行",350),l("第二行",410),l("B. 其他选项",600),hint());d.atBottom();
        assertTrue(d.complete());assertEquals("第一行第二行",d.options.get("A"));
    }
    @Test public void splitAccessibilityWordsDoNotDuplicateWholeOcrLine(){
        QuizDocument d=new QuizDocument();
        add(d,new Label("A.",80,350,120,390,true),new Label("第一项",150,350,850,390,true),l("A. 第一项",350),l("B. 第二项",600),hint());d.atBottom();
        assertTrue(d.complete());assertEquals("第一项",d.options.get("A"));
    }
    @Test public void splitHintLabelsStillMarkOptionEnd(){
        QuizDocument d=new QuizDocument();add(d,l("A. 第一项",350),l("B. 第二项",600),
                new Label("查看",750,880,830,920,false),new Label("提示",840,880,920,920,false));d.atBottom();assertTrue(d.complete());
    }
    @Test public void rejectsOffscreenNodesAndWholeContainers(){
        List<Label> rows=QuizDocument.rows(1000,1000,Arrays.asList(l("A. 屏外内容",970),new Label("A. 一\nB. 二",0,150,1000,900,true),l("A. 有效",400)));
        assertEquals(1,rows.size());assertEquals("A. 有效",rows.get(0).text);
    }
    @Test public void rejectsConflictingRescans(){
        QuizDocument d=new QuizDocument();add(d,l("A. 原始选项",400),l("B. 第二选项",600));
        add(d,l("A. 不同文字",400),l("B. 第二选项",600),hint());d.atBottom();assertFalse(d.complete());
    }
    @Test public void letterHintsAndSingleChoiceAmbiguity(){
        QuizDocument d=new QuizDocument();add(d,l("A. 甲答案",350),l("B. 乙答案",500),l("C. 丙答案",700),hint());d.atBottom();
        assertEquals(Arrays.asList("A","C"),d.choose(Arrays.asList("A、C"),true));
        assertTrue(d.choose(Arrays.asList("A、C"),false).isEmpty());
    }
    @Test public void scanHasStableAndAbsoluteLimits(){
        QuizDocument.Scan scan=new QuizDocument.Scan();assertFalse(scan.observe("same"));assertFalse(scan.observe("same"));assertTrue(scan.observe("same"));
        scan=new QuizDocument.Scan();for(int i=0;i<18;i++)assertFalse(scan.observe("frame"+i));assertTrue(scan.exhausted());
    }
    @Test public void selectionUsesFreshCoordinatesAndDoesNotToggleEarlierChoices(){
        QuizDocument d=new QuizDocument();add(d,l("A. 第一项",350),l("B. 第二项",600),hint());d.atBottom();
        QuizDocument.Selection selection=new QuizDocument.Selection(d,Arrays.asList("A","B"));
        Label newA=l("A. 第一项",200),newB=l("B. 第二项",450);
        assertSame(newA,selection.next(1000,1000,Arrays.asList(newA)));selection.clicked("A");
        assertFalse(selection.complete());assertNull(selection.next(1000,1000,Arrays.asList(newA)));
        assertSame(newB,selection.next(1000,1000,Arrays.asList(newA,newB)));selection.clicked("B");assertTrue(selection.complete());
    }
    @Test public void missingOffscreenChoiceCannotCompleteSelection(){
        QuizDocument d=new QuizDocument();add(d,l("A. 第一项",350),l("B. 第二项",600),hint());d.atBottom();
        QuizDocument.Selection selection=new QuizDocument.Selection(d,Arrays.asList("A","B"));selection.clicked("A");
        assertNull(selection.next(1000,1000,Arrays.asList(l("B. 第二项",980))));assertFalse(selection.complete());
    }
    @Test(expected=IllegalStateException.class) public void changedOptionContentStopsSelection(){
        QuizDocument d=new QuizDocument();add(d,l("A. 第一项",350),l("B. 第二项",600),hint());d.atBottom();
        new QuizDocument.Selection(d,Arrays.asList("A")).next(1000,1000,Arrays.asList(l("A. 不同内容",350)));
    }
}
