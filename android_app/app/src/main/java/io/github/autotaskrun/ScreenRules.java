package io.github.autotaskrun;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.autotaskrun.PointsEntryLocator.Label;

/** Screen-relative decisions kept independent of Android for regression tests. */
final class ScreenRules {
    static String norm(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).replaceAll("\\s+","");}
    static String kind(String value){
        String text=norm(value);
        for(String kind:new String[]{"单选题","多选题","点选填空题","点击填空题","填空题"})
            if(text.contains(kind))return kind.equals("点击填空题")?"点选填空题":kind;
        return null;
    }
    static int questionNumber(int w,int h,List<Label> labels){
        for(Label type:labels){
            if(kind(type.text)==null||type.y()<h*.07||type.y()>h*.42)continue;
            for(Label l:labels){
                if(l.x()<w*.60||l.x()>w*.97||Math.abs(l.y()-type.y())>h*.065)continue;
                String text=norm(l.text);Matcher m=Pattern.compile("^([1-5])[/|]5$").matcher(text);
                if(m.matches())return Integer.parseInt(m.group(1));
                if(!text.matches("[/|]5"))continue;
                for(Label number:labels){
                    String n=norm(number.text);
                    if(n.matches("[1-5]")&&number.x()>w*.60&&number.right<=l.right
                            &&l.left-number.right>=-w*.015&&l.left-number.right<w*.12
                            &&Math.abs(number.y()-l.y())<h*.04)return Integer.parseInt(n);
                }
            }
        }
        // New/unknown question types still expose a type label beside the fraction.
        for(Label l:labels){
            if(l.x()<w*.60||l.x()>w*.97||l.y()<h*.07||l.y()>h*.42)continue;
            Matcher m=Pattern.compile("^([1-5])[/|]5$").matcher(norm(l.text));if(!m.matches())continue;
            for(Label type:labels)if(type.x()<w*.55&&Math.abs(type.y()-l.y())<h*.065
                    &&norm(type.text).matches(".{1,10}题"))return Integer.parseInt(m.group(1));
        }
        return 0;
    }
    static Label hintButton(int w,int h,List<Label> labels){
        Label best=null;
        for(Label l:labels){
            String text=norm(l.text);
            if(l.x()<w*.55||l.y()<h*.25||l.y()>h*.95||l.right-l.left>w*.38||l.bottom-l.top>h*.07)continue;
            if(text.endsWith("查看提示")&&text.length()<=7||text.equals("查看提示按钮")){
                if(best==null||l.node&&!best.node)best=l;
            }
            if(text.equals("查看"))for(Label other:labels)
                if(norm(other.text).equals("提示")&&other.left>=l.right-w*.01&&other.left-l.right<w*.08
                        &&Math.abs(other.y()-l.y())<h*.018){
                    if(best==null)best=new Label("查看提示",l.left,Math.min(l.top,other.top),other.right,Math.max(l.bottom,other.bottom),false);
                }
        }
        return best;
    }
    static boolean home(int w,int h,List<Label> labels){
        Set<String> found=new HashSet<>();
        for(Label l:labels)if(l.y()>h*.80&&l.y()<h*.995&&l.bottom-l.top<h*.06)
            for(String text:new String[]{"强国通","百灵","电视台","电台"})if(norm(l.text).equals(text))found.add(text);
        return found.size()>=2;
    }
    static boolean points(List<Label> labels){
        Set<String> rows=new HashSet<>();boolean rules=false,progress=false;
        for(Label l:labels){String t=norm(l.text);
            if(t.matches("[积枳]分中心"))return true;
            if(t.equals("积分规则"))rules=true;
            if(t.matches(".*[0-9]+分[/／][0-9]+分.*"))progress=true;
            for(String row:new String[]{"我要选读文章","我要视听学习","每日答题","趣味答题","订阅","本地频道","强国运动"})
                if(t.equals(row))rows.add(row);
        }
        return progress&&(rows.size()>=2||rules&&!rows.isEmpty());
    }
    static boolean pointsBottom(List<Label> labels){
        for(Label l:labels)if(norm(l.text).matches("[强強]国运动|运动|已经到底了|没有更多了"))return true;
        return false;
    }
    static boolean pointsTop(List<Label> labels){
        boolean rules=false,login=false;for(Label l:labels){String t=norm(l.text);rules|=t.equals("积分规则");login|=t.equals("登录");}
        return rules&&login;
    }
    static String signature(int h,List<Label> labels){
        java.util.TreeSet<String> visible=new java.util.TreeSet<>();
        for(Label l:labels)if(l.y()>h*.15&&l.y()<h*.95)visible.add(norm(l.text)+"@"+Math.round(l.y()*60.0/h));
        return String.join("|",visible);
    }
    static final class Scan {
        private String previous="";private int unchanged,steps;private boolean toTop;
        // Once the bottom is seen, all following gestures go up. Never oscillate forever.
        String next(boolean bottom,boolean top,String signature){
            if(++steps>20)return "stop";
            unchanged=signature.equals(previous)?unchanged+1:0;previous=signature;
            if(!toTop&&(bottom||unchanged>=2||steps>=9)){toTop=true;unchanged=0;previous="";}
            if(toTop&&(top||unchanged>=2))return "stop";
            return toTop?"up":"down";
        }
    }
}
