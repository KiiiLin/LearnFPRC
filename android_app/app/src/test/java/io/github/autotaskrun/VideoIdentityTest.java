package io.github.autotaskrun;
import org.junit.Test;
import static org.junit.Assert.*;
public class VideoIdentityTest {
    @Test public void toleratesPunctuationAndSmallOcrChanges(){
        assertTrue(VideoIdentity.same("|四位“临时妈妈”找到了!","四位临时妈妈找到了"));
        assertTrue(VideoIdentity.same("长时间卧床玩手机年轻人也容易形成血栓","长时闻卧床玩手机年轻人也容客易形成血栓"));
    }
    @Test public void oldOneLineHistoryMatchesNewWrappedTitle(){
        assertTrue(VideoIdentity.same("长时间卧床玩手机年轻人","长时间卧床玩手机年轻人也容易形成血栓"));
    }
    @Test public void unrelatedAndDifferentNumberedContentStaysDistinct(){
        assertFalse(VideoIdentity.same("天麻排骨汤制作过程","陆航直升机海上实弹射击"));
        assertFalse(VideoIdentity.same("中华历史故事第1集","中华历史故事第2集"));
    }
    @Test public void lightGrayTitleWithMinorDarkNoiseIsSkipped(){
        VideoIdentity.Ink ink=new VideoIdentity.Ink();for(int i=0;i<1000;i++)ink.add(0xFFFFFFFF);
        for(int i=0;i<100;i++)ink.add(0xFFAAAAAA);for(int i=0;i<4;i++)ink.add(0xFF333333);
        assertFalse(ink.unread());
    }
    @Test public void darkTitleAndAntialiasingAreUnread(){
        VideoIdentity.Ink ink=new VideoIdentity.Ink();for(int i=0;i<1000;i++)ink.add(0xFFFFFFFF);
        for(int i=0;i<80;i++)ink.add(0xFF444444);for(int i=0;i<40;i++)ink.add(0xFFBBBBBB);
        assertTrue(ink.unread());
    }
    @Test public void noTextOrColoredThumbnailDoesNotCountAsUnread(){
        VideoIdentity.Ink ink=new VideoIdentity.Ink();for(int i=0;i<100;i++){ink.add(0xFFFFFFFF);ink.add(0xFF0044CC);}assertFalse(ink.unread());
    }
}
