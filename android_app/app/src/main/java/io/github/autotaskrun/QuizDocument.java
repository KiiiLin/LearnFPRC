package io.github.autotaskrun;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.autotaskrun.PointsEntryLocator.Label;

/** Text-only scroll document. Screen coordinates are deliberately never retained here. */
final class QuizDocument {
    private static final Pattern MARKER=Pattern.compile("^([A-H])(?:[.、:：．)）]\\s*|\\s+|$)(.*)",Pattern.DOTALL);
    final Map<String,String> options=new LinkedHashMap<>();
    private String tail="";
    private boolean uncertain=false;
    private boolean bottom=false;
    private boolean ended=false;

    static String clean(String text){return ScreenRules.norm(text).replaceAll("[，。；、：,!！?？;:“”\"‘’'（）()《》]","").replaceAll("(?<![0-9])\\.|\\.(?![0-9])","").toLowerCase(java.util.Locale.ROOT);}
    static String letter(String text){Matcher m=MARKER.matcher(java.text.Normalizer.normalize(text.trim(),java.text.Normalizer.Form.NFKC));return m.matches()?m.group(1):null;}
    static String value(String text){Matcher m=MARKER.matcher(java.text.Normalizer.normalize(text.trim(),java.text.Normalizer.Form.NFKC));return m.matches()?clean(m.group(2)):clean(text);}
    static boolean footer(String text){String n=ScreenRules.norm(text);return n.contains("查看提示")||n.startsWith("出题单位")||n.startsWith("出题:")||n.startsWith("题目来源")||n.startsWith("推荐单位")||n.startsWith("来源:");}
    static boolean chrome(String text){String n=ScreenRules.norm(text);return ScreenRules.kind(n)!=null||n.matches("[1-5][/|]5|每日答题|确定|完成|返回|提示|查看|关闭|退出答题|多选|单选");}

    private static List<Label> bindOptionParagraphs(int w,int h,List<Label> labels){
        List<Label> result=new ArrayList<>();java.util.Set<Label> used=new java.util.HashSet<>();
        for(Label marker:labels){
            String key=letter(marker.text);
            if(!marker.node||key==null||!value(marker.text).isEmpty()||marker.top<h*.14||marker.bottom>h*.945)continue;
            int nextTop=(int)(h*.945);
            for(Label next:labels)if(next.node&&letter(next.text)!=null&&!key.equals(letter(next.text))&&next.top>marker.top)
                nextTop=Math.min(nextTop,next.top);
            Label paragraph=null;
            for(Label candidate:labels){
                if(!candidate.node||used.contains(candidate)||letter(candidate.text)!=null||chrome(candidate.text)||footer(candidate.text))continue;
                if(candidate.left<marker.right-w*.02||candidate.left>w*.45||candidate.right>w+2||candidate.right-candidate.left<w*.08
                        ||candidate.top<h*.14||candidate.bottom>nextTop||candidate.bottom-candidate.top>h*.55)continue;
                boolean firstLine=Math.abs(candidate.top-marker.top)<=Math.max(h*.012,(marker.bottom-marker.top)*.5);
                boolean centered=marker.y()>=candidate.top&&marker.y()<=candidate.bottom;
                if(!firstLine&&!centered)continue;
                if(paragraph==null||candidate.text.length()>paragraph.text.length())paragraph=candidate;
            }
            if(paragraph!=null){
                result.add(new Label(key+". "+paragraph.text,marker.left,Math.min(marker.top,paragraph.top),paragraph.right,
                        Math.max(marker.bottom,paragraph.bottom),true));
                used.add(marker);used.add(paragraph);
            }
        }
        for(Label l:labels)if(!used.contains(l))result.add(l);
        return result;
    }

    static List<Label> rows(int w,int h,List<Label> labels){
        labels=bindOptionParagraphs(w,h,labels);
        List<Label> candidates=new ArrayList<>();
        for(Label l:labels){
            if(l.left<0||l.right>w+2||l.top<h*.14||l.bottom>h*.945||l.bottom<=l.top||l.right<=l.left||chrome(l.text))continue;
            // Ignore whole WebView/container labels. A single full option node is useful for wrapped text.
            if(l.bottom-l.top>h*.55||l.node&&letter(l.text)==null&&l.bottom-l.top>h*.065)continue;
            if(l.text.matches("(?s).*\\n\\s*[A-H][.、:：．)].*"))continue;
            boolean ocrCoversNode=false;
            if(l.node)for(Label other:labels){
                if(other.node||other.top<h*.14||other.bottom>h*.945)continue;
                boolean inside=l.left>=other.left-6&&l.right<=other.right+6&&l.top>=other.top-6&&l.bottom<=other.bottom+6;
                if(inside&&clean(other.text).contains(clean(l.text))&&clean(other.text).length()>clean(l.text).length()){
                    ocrCoversNode=true;break;
                }
            }
            if(ocrCoversNode)continue;
            candidates.add(l);
        }
        candidates.sort(Comparator.comparing((Label l)->!l.node).thenComparingInt(l->l.top));
        List<Label> out=new ArrayList<>();
        for(Label l:candidates){boolean duplicate=false;
            for(Label old:out){
                boolean inside=l.left>=old.left-6&&l.right<=old.right+6&&l.top>=old.top-6&&l.bottom<=old.bottom+6;
                boolean sameRow=Math.abs(l.y()-old.y())<Math.max(l.bottom-l.top,old.bottom-old.top)*.55;
                if(inside&&clean(old.text).contains(clean(l.text))||sameRow&&clean(l.text).equals(clean(old.text))){duplicate=true;break;}
            }
            if(!duplicate)out.add(l);
        }
        out.sort(Comparator.comparingInt((Label l)->l.top).thenComparingInt(l->l.left));
        // Separate accessibility nodes for "A." and its value have slightly different font baselines.
        // Order a visual row left-to-right, otherwise the value can be assigned to the preceding letter.
        for(int start=0;start<out.size();){
            Label first=out.get(start);int end=start+1;
            while(end<out.size()){
                Label next=out.get(end);
                if(Math.abs(first.y()-next.y())>Math.min(first.bottom-first.top,next.bottom-next.top)*.5)break;
                end++;
            }
            out.subList(start,end).sort(Comparator.comparingInt(l->l.left));start=end;
        }
        return out;
    }
    static String signature(int w,int h,List<Label> labels){
        TreeSet<String> parts=new TreeSet<>();
        for(Label l:rows(w,h,labels))parts.add(clean(l.text)+"@"+Math.round(l.y()*50.0/h));
        return String.join("|",parts);
    }
    static String merge(String old,String fresh){
        if(old.isEmpty())return fresh;if(fresh.isEmpty()||old.contains(fresh))return old;
        if(fresh.contains(old))return fresh;
        for(int n=Math.min(old.length(),fresh.length());n>=3;n--)if(old.endsWith(fresh.substring(0,n)))return old+fresh.substring(n);
        return null; // A missing overlap is not evidence that two unrelated fragments belong together.
    }
    void add(int w,int h,List<Label> labels){
        String current="",first="";StringBuilder chunk=new StringBuilder(),leading=new StringBuilder();
        boolean hitFooter=ScreenRules.hintButton(w,h,labels)!=null;
        for(Label l:rows(w,h,labels)){
            if(footer(l.text)){hitFooter=true;break;}
            String key=letter(l.text);
            if(key!=null){
                if(first.isEmpty())first=key;
                if(!current.isEmpty())put(current,chunk.toString());
                current=key;chunk.setLength(0);chunk.append(value(l.text));
            }else if(!current.isEmpty())chunk.append(clean(l.text));else leading.append(clean(l.text));
        }
        if(!tail.isEmpty()&&!ended&&leading.length()>0&&(first.isEmpty()||first.compareTo(tail)>0)){
            String joined=merge(options.get(tail),leading.toString());
            if(joined==null)uncertain=true;else options.put(tail,joined);
        }
        if(!current.isEmpty()){put(current,chunk.toString());tail=current;}
        ended=hitFooter;
    }
    private void put(String key,String fragment){
        String joined=merge(options.getOrDefault(key,""),fragment);
        if(joined==null)uncertain=true;else options.put(key,joined);
    }
    void atBottom(){bottom=true;}
    boolean complete(){
        return problem().isEmpty();
    }
    String problem(){
        if(!bottom)return "尚未确认题目底部";
        if(!ended)return "未确认选项末尾标识";
        if(uncertain)return "跨屏文字无法可靠拼接";
        if(options.size()<2)return "识别到的选项不足两项";
        char expected='A';for(Map.Entry<String,String> item:options.entrySet()){
            if(!item.getKey().equals(String.valueOf(expected++)))return "选项字母不连续";
            if(item.getValue().isEmpty())return "选项 "+item.getKey()+" 缺少正文";
        }
        return "";
    }
    List<String> choose(List<String> hints,boolean multiple){
        List<String> chosen=new ArrayList<>();if(!complete())return chosen;
        StringBuilder all=new StringBuilder();for(String hint:hints)all.append(clean(hint));String answer=all.toString();
        boolean letters=answer.matches("[a-h]{1,8}");
        for(Map.Entry<String,String> item:options.entrySet())if(letters?answer.contains(item.getKey().toLowerCase(java.util.Locale.ROOT)):answer.contains(item.getValue()))chosen.add(item.getKey());
        if(multiple?chosen.size()<2:chosen.size()!=1)chosen.clear();
        return chosen;
    }
    static final class Selection {
        private final Map<String,String> expected;
        private final java.util.Set<String> remaining;
        Selection(QuizDocument document,List<String> chosen){expected=document.options;remaining=new java.util.LinkedHashSet<>(chosen);}
        Label next(int w,int h,List<Label> current){
            for(Label row:rows(w,h,current)){
                String key=letter(row.text);if(key==null||!remaining.contains(key))continue;
                String visible=value(row.text);
                if(!expected.containsKey(key)||!expected.get(key).startsWith(visible))throw new IllegalStateException("选项 "+key+" 与扫描内容不一致");
                return row;
            }
            return null;
        }
        void clicked(String key){remaining.remove(key);}
        boolean complete(){return remaining.isEmpty();}
        int left(){return remaining.size();}
    }
    /** Two unchanged screens end a scan; a changing page still has an absolute limit. */
    static final class Scan {
        private String previous=null;private int same=0,steps=0;
        boolean observe(String signature){same=signature.equals(previous)?same+1:0;previous=signature;steps++;return same>=2;}
        boolean exhausted(){return steps>=18;}
    }
}
