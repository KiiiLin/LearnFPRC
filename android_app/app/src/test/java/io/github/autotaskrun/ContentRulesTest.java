package io.github.autotaskrun;

import org.junit.Test;
import java.util.*;
import io.github.autotaskrun.PointsEntryLocator.Label;
import static org.junit.Assert.*;

public class ContentRulesTest {
    private Label l(String t,int x,int y,int r,int b){return new Label(t,x,y,r,b,false);}
    private List<Label> article(){return new ArrayList<>(Arrays.asList(
        new Label("【畅游上海】童年回归、山路更新，长风公园国庆节焕新开放",54,274,1167,562,true),
        l("普陀学习平台",187,718,432,766),l("2026-09-23",187,781,382,814),
        l("一张泛黄的老照片上，一群戴红领巾的孩子正走过一座简易的木桥。照片说明是长风公园勇敢者道路。许多上海人的童年相册里，都夹着这样一张照片。照片上的孩子现在早已为人父母，有些甚至成了祖父母。",54,1033,1167,1585)
    ));}
    @Test public void real1220PhoneArticleWithOcrOmissions(){
        assertTrue(ContentRules.article(1220,2712,article(),"【畅游上】童年回归、山路更新,长风公园国庆节焕新开"));
    }
    @Test public void ocrWrappedHeadingWithoutNode(){
        List<Label> labels=article();labels.remove(0);labels.add(l("【畅游上海】童年回归、山路更",86,324,1054,393));labels.add(l("新,长风公园国庆节焕新开放",56,435,989,502));
        assertTrue(ContentRules.article(1220,2712,labels,"【畅游上】童年回归、山路更新,长风公园国庆节焕新开"));
    }
    @Test public void giantWebViewLabelIsNotAHeading(){
        List<Label> labels=article();Label heading=labels.remove(0);labels.add(l(heading.text,0,274,1221,2500));
        assertFalse(ContentRules.article(1220,2712,labels,heading.text));
    }
    @Test public void unrelatedTitleAndMissingBodyRejected(){
        assertFalse(ContentRules.article(1220,2712,article(),"上海首家家政服务员之家揭牌30元一晚拎包安居"));
        List<Label> labels=article();labels.remove(labels.size()-1);assertFalse(ContentRules.article(1220,2712,labels,labels.get(0).text));
    }
    @Test public void titleIsNotComparedToWholePageBody(){
        assertFalse(ContentRules.titleMatches("上海一周把握规律识变应变求变","上海一周把握规律识变应变求变"+article().get(3).text));
    }
    @Test public void playerTimerCanMoveAboveBottom95Percent(){
        assertTrue(ContentRules.player(1220,2712,Arrays.asList(l("四位临时妈妈找到了",40,2240,700,2300),l("00:02 / 01:38",900,2400,1200,2460)),"四位“临时妈妈”找到了!",98));
    }
    @Test public void hiddenTimerUsesVisiblePlaybackControl(){
        assertTrue(ContentRules.player(1220,2712,Arrays.asList(l("四位临时妈妈找到了",40,2240,700,2300),l("暂停按钮",540,1400,640,1500)),"四位“临时妈妈”找到了!",98));
    }
    @Test public void titleAloneDoesNotConfirmPlayer(){
        assertFalse(ContentRules.player(1220,2712,Arrays.asList(l("四位临时妈妈找到了",40,2240,700,2300)),"四位“临时妈妈”找到了!",98));
    }
    @Test public void recentsScreenshotNeverConfirmsContent(){
        List<Label> labels=article();labels.add(l("清理全部任务",479,2317,740,2579));
        assertTrue(ContentRules.recents(labels));assertFalse(ContentRules.article(1220,2712,labels,labels.get(0).text));
    }
    @Test public void renamedAppInRecentsNeverConfirmsArticle(){
        for(String marker:Arrays.asList("每日学习助手,未加锁","打开每日学习助手应用信息。","LearnFPRC,未加锁","打开LearnFPRC应用信息。","每日任务助手,未加锁")){
            List<Label> labels=article();labels.add(l(marker,320,753,921,2220));
            assertTrue(ContentRules.recents(labels));
            assertFalse(ContentRules.article(1220,2712,labels,labels.get(0).text));
        }
        assertFalse(ContentRules.recents(Arrays.asList(l("LearnFPRC",30,100,500,180))));
    }
    @Test public void startupQuoteDetected(){assertTrue(ContentRules.splash(Arrays.asList(l("《论语》",814,1022,861,1164),l("中共中央宣传部“学习强国”学习平台",307,2616,911,2650))));}
    @Test public void adSkipMustBeCompactAndInCorner(){
        Label skip=l("跳过3秒",1000,150,1170,200);assertSame(skip,ContentRules.skipAd(1220,2712,Arrays.asList(skip)));
        assertNull(ContentRules.skipAd(1220,2712,Arrays.asList(l("跳过",500,1200,700,1300))));
        assertNull(ContentRules.skipAd(1220,2712,Arrays.asList(l("点击领取",1000,150,1170,200))));
    }
    @Test public void switchedWindowCannotMixScreenshotAndNodes(){
        assertTrue(ContentRules.sameWindow("cn.xuexi.android",7,"cn.xuexi.android",7));
        assertFalse(ContentRules.sameWindow("cn.xuexi.android",7,"com.miui.home",8));
        assertFalse(ContentRules.sameWindow("cn.xuexi.android",7,"cn.xuexi.android",8));
        assertFalse(ContentRules.sameWindow("",7,"cn.xuexi.android",7));
    }
    @Test public void alreadyOpenDetailIsReadyButLaunchSplashIsNot(){
        assertTrue(ContentRules.loadedDetail(article()));
        assertTrue(ContentRules.loadedDetail(Arrays.asList(l("分享",1000,2400,1190,2460),l("00:02 / 01:38",900,2500,1200,2560))));
        assertFalse(ContentRules.loadedDetail(Arrays.asList(l("中共中央宣传部“学习强国”学习平台",307,2616,911,2650))));
    }
}
