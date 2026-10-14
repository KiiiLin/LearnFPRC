package io.github.autotaskrun;

import java.text.Normalizer;
import java.util.*;
import io.github.autotaskrun.PointsEntryLocator.Label;

final class ContentRules {
    static String clean(String s){return Normalizer.normalize(s,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fff]","");}
    static boolean titleMatches(String expected,String observed){
        String a=clean(expected),b=clean(observed);
        if(a.length()<7||b.length()<7||Math.min(a.length(),b.length())/(double)Math.max(a.length(),b.length())<.62)return false;
        if(a.contains(b)||b.contains(a))return true;
        int[] previous=new int[b.length()+1];for(int j=0;j<=b.length();j++)previous[j]=j;
        for(int i=1;i<=a.length();i++){
            int[] next=new int[b.length()+1];next[0]=i;
            for(int j=1;j<=b.length();j++)next[j]=Math.min(Math.min(next[j-1]+1,previous[j]+1),previous[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
            previous=next;
        }
        return previous[b.length()]<=Math.max(1,Math.floor(Math.max(a.length(),b.length())*.22));
    }
    static boolean article(int w,int h,List<Label> labels,String title){
        if(ScreenRules.home(w,h,labels)||recents(labels))return false;
        boolean heading=false,metadata=false;int body=0;
        List<Label> pieces=new ArrayList<>();Set<String> bodyTexts=new HashSet<>();
        for(Label l:labels){
            String text=clean(l.text);int height=l.bottom-l.top;
            if(l.top>h*.055&&l.bottom<h*.27&&height<h*.16){
                heading|=titleMatches(title,l.text);
                if(!l.node&&text.length()>=4)pieces.add(l);
            }
            if(l.y()>h*.16&&l.y()<h*.40&&height<h*.09
                    &&l.text.matches(".*(\\d{4}[-年/.]\\d{1,2}|作者|来源|学习平台|发布内容).*"))metadata=true;
            if(l.top>h*.25&&l.bottom<h*.92&&height<h*.28&&text.length()>=14&&bodyTexts.add(text))body+=text.length();
        }
        pieces.sort(Comparator.comparingInt(l->l.top));
        for(int i=0;i<pieces.size();i++){
            String combined=pieces.get(i).text;int bottom=pieces.get(i).bottom;
            for(int j=i+1;j<Math.min(i+4,pieces.size());j++){
                Label l=pieces.get(j);if(l.top<bottom||l.top-bottom>h*.035)break;
                combined+=l.text;bottom=l.bottom;heading|=titleMatches(title,combined);
            }
        }
        return heading&&metadata&&body>=60;
    }
    static boolean player(int w,int h,List<Label> labels,String title,int expected){
        if(ScreenRules.home(w,h,labels)||recents(labels))return false;
        boolean heading=false,timer=false,control=false;Set<String> social=new HashSet<>();
        for(Label l:labels){
            String text=ScreenRules.norm(l.text);
            if(l.y()<h*.07||l.y()>h*.98||l.bottom-l.top>h*.15)continue;
            heading|=titleMatches(title,l.text);
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\d{1,2}:\\d{2}[/／](\\d{1,2}):(\\d{2})").matcher(text);
            if(m.find()){int total=Integer.parseInt(m.group(1))*60+Integer.parseInt(m.group(2));timer|=Math.abs(total-expected)<=5;}
            control|=text.matches(".*(暂停|继续播放|倍速|全屏|播放进度|播放按钮|暂停按钮).*");
            for(String item:new String[]{"点赞","评论","分享","收藏"})if(text.contains(item))social.add(item);
        }
        return heading&&(timer||control||social.size()>=2);
    }
    static boolean recents(List<Label> labels){
        for(Label l:labels)if(ScreenRules.norm(l.text).equals("清理全部任务")||l.text.contains("打开每日任务助手应用信息")||l.text.contains("打开LearnFPRC应用信息")||l.text.contains("打开每日学习助手应用信息")
                ||((l.text.startsWith("学习强国")||l.text.startsWith("每日任务助手")||l.text.startsWith("LearnFPRC")||l.text.startsWith("每日学习助手"))&&l.text.contains("未加锁"))
                ||l.text.contains("打开学习强国应用信息"))return true;
        return false;
    }
    static boolean splash(List<Label> labels){
        for(Label l:labels){String t=clean(l.text);
            if(t.contains("中共中央宣传部学习强国学习平台")||t.equals("加载中")
                    ||(labels.size()<12&&(t.equals("论语")||t.startsWith("学而时")||t.equals("不亦说乎"))))return true;
        }
        return false;
    }
    static boolean loadedDetail(List<Label> labels){
        boolean date=false,source=false,timer=false,share=false;
        for(Label l:labels){String t=ScreenRules.norm(l.text);
            if(t.matches("[0-9]{4}[-年/.][0-9]{1,2}[-月/.][0-9]{1,2}.*"))date=true;
            source|=t.contains("学习平台")||t.startsWith("作者")||t.startsWith("来源");
            timer|=t.matches(".*[0-9]{1,2}:[0-9]{2}[/／][0-9]{1,2}:[0-9]{2}.*");
            share|=t.equals("分享");
        }
        return !splash(labels)&&((date&&source)||(timer&&share));
    }
    static Label skipAd(int w,int h,List<Label> labels){
        for(Label l:labels){String text=ScreenRules.norm(l.text);
            if(l.x()>w*.65&&(l.y()<h*.25||l.y()>h*.72)&&l.right-l.left<w*.30&&l.bottom-l.top<h*.06
                    &&text.matches("(?:[0-9]{1,2}[sS秒]?\\|?)?跳过(?:广告)?(?:[0-9]{1,2}[sS秒]?)?"))return l;
        }
        return null;
    }
    static boolean sameWindow(String before,int beforeId,String after,int afterId){
        return "cn.xuexi.android".equals(before)&&before.equals(after)&&beforeId==afterId;
    }
}
