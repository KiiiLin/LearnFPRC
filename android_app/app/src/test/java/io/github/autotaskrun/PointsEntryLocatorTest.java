package io.github.autotaskrun;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class PointsEntryLocatorTest {
    private PointsEntryLocator.Label label(String text,int x1,int y1,int x2,int y2){
        return new PointsEntryLocator.Label(text,x1,y1,x2,y2,false);
    }
    private PointsEntryLocator.Label find(PointsEntryLocator.Label... labels){return PointsEntryLocator.locate(864,1920,Arrays.asList(labels));}
    private final PointsEntryLocator.Label mine=label("我的",771,121,842,158);
    @Test public void newScreenshotScoreLeftOfOldSearchBand(){
        // OCR boxes from 47e2fe779d6d47acc6ba77a18ae0540a.jpg (864 x 1920).
        PointsEntryLocator.Label score=label("磐6833",526,107,652,168);
        assertSame(score,find(label("11:35",50,26,129,61),score,mine,label("强国辞典",214,122,319,150)));
        assertEquals(589,score.x());assertTrue(score.x()<864*.72);
    }
    @Test public void separateDigitsAndVerticalLabel(){
        PointsEntryLocator.Label score=label("6833",560,122,640,157);
        assertSame(score,find(label("积",535,117,550,132),label("分",535,134,550,151),score,mine));
    }
    @Test public void aiIsNeverAnEntry(){assertNull(find(label("AI",690,119,730,160),mine));}
    @Test public void mergedAiAndScoreRejected(){assertNull(find(label("6833 AI",560,119,730,160),mine));}
    @Test public void mergedWholeToolbarNodeRejected(){
        assertNull(find(label("积分6833我的",210,100,842,180),mine));
    }
    @Test public void noDigitsNoBlindCoordinate(){assertNull(find(mine,label("强国辞典",214,122,319,150)));}
    @Test public void statusBarAndPageNumbersRejected(){
        assertNull(find(mine,label("6833",580,22,650,53),label("2026",580,300,660,340)));
    }
    @Test public void unlabelledNumberNeedsToolbarAnchor(){assertNull(find(label("6833",560,122,640,157)));}
    @Test public void numberInAiZoneWithoutPointsLabelRejected(){assertNull(find(mine,label("6833",695,122,750,157)));}
    @Test public void labelledOldLayoutToRightStillWorks(){
        PointsEntryLocator.Label score=label("积分6700",625,119,742,160);
        assertSame(score,find(score,mine));
    }
    @Test public void explicitAiBoundaryWins(){
        assertNull(find(label("积分6700",625,119,742,160),label("AI",702,119,734,160),mine));
    }
    @Test public void layoutScalesWithDisplaySize(){
        PointsEntryLocator.Label score=label("6833",700,153,800,196);
        assertSame(score,PointsEntryLocator.locate(1080,2400,Arrays.asList(score,label("我的",964,151,1053,198))));
    }
    @Test public void numberBeatsTinyClickableLabel(){
        PointsEntryLocator.Label score=label("6833",560,122,640,157);
        PointsEntryLocator.Label small=new PointsEntryLocator.Label("积分",535,117,550,151,true);
        assertSame(score,find(mine,small,score));
    }
    @Test public void oldLayoutNumberBesideExplicitLabel(){
        PointsEntryLocator.Label score=label("6833",710,120,774,153);
        assertSame(score,find(label("我的",815,117,862,154),label("积分",680,117,701,154),score));
    }
}
