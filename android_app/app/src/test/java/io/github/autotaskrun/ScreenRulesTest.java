package io.github.autotaskrun;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import io.github.autotaskrun.PointsEntryLocator.Label;
import static org.junit.Assert.*;

public class ScreenRulesTest {
    private Label l(String text,int left,int top,int right,int bottom){return new Label(text,left,top,right,bottom,false);}
    private Label word(String text){return l(text,50,500,300,540);}
    @Test public void livePointsTitleOcrAndBottomVariantRemainRecognizable(){
        assertTrue(ScreenRules.points(Arrays.asList(word("枳分中心"))));
        assertTrue(ScreenRules.pointsBottom(Arrays.asList(word("強国运动"))));
        assertFalse(ScreenRules.points(Arrays.asList(word("未能进入积分中心"))));
    }
    @Test public void phoneQuestionBelow350Pixels(){
        assertEquals(1,ScreenRules.questionNumber(1080,2400,Arrays.asList(l("多选题",100,395,300,450),l("1/5",860,385,980,455))));
    }
    @Test public void splitQuestionWithDifferentFontBaselines(){
        assertEquals(2,ScreenRules.questionNumber(1440,3200,Arrays.asList(l("填空题",170,490,480,570),l("2",1150,460,1220,585),l("/5",1230,530,1310,592))));
    }
    @Test public void scaledOldEmulatorLayout(){
        assertEquals(1,ScreenRules.questionNumber(900,1600,Arrays.asList(l("单选题",110,245,237,285),l("1",726,235,765,292),l("/5",780,264,815,293))));
    }
    @Test public void fullWidthSlashAndWhitespace(){
        assertEquals(3,ScreenRules.questionNumber(1080,2400,Arrays.asList(l("单 选 题",100,395,300,450),l("３ ／ ５",860,385,980,455))));
    }
    @Test public void bodyFractionsAreNotQuestionNumbers(){
        assertEquals(0,ScreenRules.questionNumber(1080,2400,Arrays.asList(l("单选题",100,395,300,450),l("1/5",850,850,980,910))));
    }
    @Test public void noGuessWithoutType(){assertEquals(0,ScreenRules.questionNumber(1080,2400,Arrays.asList(l("1/5",860,385,980,455))));}
    @Test public void rejectInvalidQuestionNumber(){assertEquals(0,ScreenRules.questionNumber(1080,2400,Arrays.asList(l("单选题",100,395,300,450),l("8/5",860,385,980,455))));}
    @Test public void hintToleratesWhitespaceAndBulbOcr(){
        Label hint=l("Q 查 看 提 示",750,1700,970,1750);assertSame(hint,ScreenRules.hintButton(1080,2400,Arrays.asList(hint)));
    }
    @Test public void splitHintWordsAreCombined(){
        Label hint=ScreenRules.hintButton(1080,2400,Arrays.asList(l("查看",790,1700,860,1750),l("提示",865,1700,930,1750)));
        assertNotNull(hint);assertEquals(860,hint.x());
    }
    @Test public void ignoreTextInStemAndLargeNode(){
        assertNull(ScreenRules.hintButton(1080,2400,Arrays.asList(l("请查看提示后作答",500,900,980,950),l("查看提示",10,600,1000,1900))));
    }
    @Test public void pointsCanBeRecognizedWhenTitleOcrIsMissing(){
        assertTrue(ScreenRules.points(Arrays.asList(word("订阅"),word("本地频道"),word("0 分 / 2 分"))));
        assertFalse(ScreenRules.points(Arrays.asList(word("订阅"),word("本地频道"))));
    }
    @Test public void footerAbove90PercentIsHome(){
        assertTrue(ScreenRules.home(1080,2400,Arrays.asList(l("强国通",60,2050,200,2100),l("百灵",300,2050,400,2100))));
        assertFalse(ScreenRules.home(1080,2400,Arrays.asList(word("强国通"),word("百灵"))));
    }
    @Test public void bottomChangesDirectionImmediately(){
        ScreenRules.Scan scan=new ScreenRules.Scan();assertEquals("up",scan.next(true,false,"运动"));
        assertEquals("up",scan.next(false,false,"订阅"));assertEquals("stop",scan.next(false,true,"顶部"));
    }
    @Test public void unchangedPageDoesNotLoop(){
        ScreenRules.Scan scan=new ScreenRules.Scan();assertEquals("down",scan.next(false,false,"same"));
        assertEquals("down",scan.next(false,false,"same"));assertEquals("up",scan.next(false,false,"same"));
        assertEquals("up",scan.next(false,false,"same"));assertEquals("up",scan.next(false,false,"same"));
        assertEquals("stop",scan.next(false,false,"same"));
    }
    @Test public void absoluteScanLimitEvenIfContentKeepsChanging(){
        ScreenRules.Scan scan=new ScreenRules.Scan();for(int i=0;i<20;i++)assertNotEquals("stop",scan.next(false,false,"frame"+i));
        assertEquals("stop",scan.next(false,false,"last"));
    }
    @Test public void sportsAndTopMarkers(){
        assertTrue(ScreenRules.pointsBottom(Arrays.asList(word("强国运动"))));
        assertTrue(ScreenRules.pointsTop(Arrays.asList(word("积分规则"),word("登录"))));
        assertFalse(ScreenRules.pointsTop(Arrays.asList(word("积分中心"))));
    }
}
