package io.github.autotaskrun;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.autotaskrun.PointsEntryLocator.Label;

/** Plain-text hint extraction and conservative, contextual answer matching. No color dependency. */
final class QuizHintText {
    static final class Result {
        final List<String> answers;final String method;final int characters;
        Result(List<String> answers,String method,int characters){this.answers=answers;this.method=method;this.characters=characters;}
    }
    static String template(String raw){
        String n=Normalizer.normalize(raw,Normalizer.Form.NFKC);
        n=n.replaceAll("[_＿]{2,}|[□▢]{1,}|[ \\t\\x{00a0}\\x{3000}]{2,}|\\([ \\t]*\\)|【[ \\t]*】","§");
        return QuizDocument.clean(n);
    }
    static String inputValue(String text){
        String value=ScreenRules.norm(text);
        for(String pair:new String[]{"《》","“”","\"\"","‘’"})
            if(value.length()>=2&&value.charAt(0)==pair.charAt(0)&&value.charAt(value.length()-1)==pair.charAt(1))value=value.substring(1,value.length()-1);
        return value;
    }
    static String stem(int h,List<Label> labels){
        String best="";int firstOption=h;
        for(Label l:labels)if(QuizDocument.letter(l.text)!=null)firstOption=Math.min(firstOption,l.top);
        for(Label l:labels){
            if(!l.node||l.top<h*.19||l.top>=firstOption||ScreenRules.kind(l.text)!=null||QuizDocument.footer(l.text)||QuizDocument.letter(l.text)!=null||l.text.length()>6000)continue;
            if(l.text.matches("(?s).*\\n\\s*[A-H][.、:：．)].*"))continue;
            String candidate=template(l.text);
            if(candidate.contains("§")&&(!best.contains("§")||candidate.length()>best.length()))best=candidate;
            else if(!best.contains("§")&&candidate.length()>best.length())best=candidate;
        }
        if(best.contains("§"))return best;
        boolean fill=false;int footer=h;
        for(Label l:labels){String kind=ScreenRules.kind(l.text);if(kind!=null&&kind.contains("填空"))fill=true;
            if(l.node&&QuizDocument.footer(l.text))footer=Math.min(footer,l.top);}
        if(fill){
            // Word tiles are below the stem; a row of tall, short labels is not question text.
            for(Label l:labels)if(l.node&&l.text.length()<=8&&l.bottom-l.top>h*.035&&l.right-l.left<h*.21)
                for(Label other:labels)if(other!=l&&other.node&&other.text.length()<=8&&other.bottom-other.top>h*.035
                        &&other.right-other.left<h*.21&&Math.abs(other.top-l.top)<h*.01&&other.left>=l.right)
                    footer=Math.min(footer,l.top);
            List<Label> pieces=new ArrayList<>();
            for(Label l:labels)if(l.node&&l.top>h*.20&&l.bottom<=footer&&!QuizDocument.chrome(l.text)&&!QuizDocument.footer(l.text)&&!l.text.trim().isEmpty())pieces.add(l);
            pieces.sort(Comparator.comparingInt((Label l)->l.top).thenComparingInt(l->l.left).thenComparingInt(l->l.bottom));StringBuilder joined=new StringBuilder();
            for(Label l:pieces){
                boolean child=false;for(Label other:pieces)if(other!=l&&other.text.length()>l.text.length()
                        &&l.top>=other.top&&l.bottom<=other.bottom&&template(other.text).contains(template(l.text))){child=true;break;}
                if(child)continue;
                if(joined.length()>0)joined.append('§');joined.append(template(l.text));
            }
            if(joined.length()>0)return joined.indexOf("§")>=0?joined.toString():joined+"§";
        }
        StringBuilder ocr=new StringBuilder();List<Label> lines=new ArrayList<>();
        for(Label l:labels)if(!l.node&&l.top>h*.20&&l.bottom<firstOption&&ScreenRules.kind(l.text)==null&&!QuizDocument.footer(l.text))lines.add(l);
        lines.sort(Comparator.comparingInt(l->l.top));for(Label l:lines)ocr.append(l.text).append('\n');
        String fallback=template(ocr.toString());return fallback.contains("§")?fallback:best;
    }
    static String body(int h,int headingBottom,List<Label> before,List<Label> after,boolean nodes){
        List<Label> fresh=new ArrayList<>();
        for(Label l:after){
            if(l.node!=nodes||l.top<headingBottom||l.bottom>h*.97||l.text.trim().length()<2||QuizDocument.chrome(l.text)||QuizDocument.footer(l.text)||QuizDocument.letter(l.text)!=null)continue;
            boolean old=false;for(Label previous:before)if(ScreenRules.norm(previous.text).equals(ScreenRules.norm(l.text))
                    &&Math.abs(previous.top-l.top)<h*.008&&Math.abs(previous.bottom-l.bottom)<h*.008){old=true;break;}
            if(!old)fresh.add(l);
        }
        // Accessibility often exposes both a paragraph and its children. Keep the enclosing text once.
        fresh.sort(Comparator.comparingInt((Label l)->-l.text.length()));List<Label> unique=new ArrayList<>();
        for(Label l:fresh){boolean duplicate=false;for(Label parent:unique)
            if(l.top>=parent.top-4&&l.bottom<=parent.bottom+4&&QuizDocument.clean(parent.text).contains(QuizDocument.clean(l.text))){duplicate=true;break;}
            if(!duplicate)unique.add(l);
        }
        unique.sort(Comparator.comparingInt((Label l)->l.top).thenComparingInt(l->l.left));
        StringBuilder text=new StringBuilder();for(Label l:unique)text.append(l.text).append('\n');return text.toString();
    }
    static Result resolve(String stem,QuizDocument document,boolean multiple,int h,int headingBottom,List<Label> before,List<Label> after){
        int characters=0;
        for(boolean nodes:new boolean[]{true,false}){
            String text=body(h,headingBottom,before,after,nodes);characters=Math.max(characters,text.length());
            List<String> answers=solve(stem,text,document,multiple);
            if(!answers.isEmpty())return new Result(answers,(nodes?"无障碍弹窗正文":"弹窗全文 OCR")+" + 题干上下文匹配",text.length());
        }
        return new Result(new ArrayList<>(),"提示正文未能唯一匹配",characters);
    }
    private static final class Gap {
        final String value;final int count;
        Gap(String value,int count){this.value=value;this.count=count;}
    }
    private static List<Gap> gaps(String stem,String text){
        List<Gap> result=new ArrayList<>();String[] pieces=stem.split("§",-1);
        if(pieces.length<2||pieces.length>9)return result;
        for(int i=0;i<pieces.length-1;){
            String left=pieces[i];int j=i+1;while(j<pieces.length-1&&pieces[j].isEmpty())j++;
            String right=pieces[j];
            if(left.length()<4||!right.isEmpty()&&right.length()<2)return new ArrayList<>();
            left=left.substring(Math.max(0,left.length()-24));right=right.substring(0,Math.min(24,right.length()));
            Set<String> matches=new LinkedHashSet<>();int from=0;
            while(from<text.length()){
                int start=text.indexOf(left,from);if(start<0)break;start+=left.length();int end=right.isEmpty()?text.length():text.indexOf(right,start);
                if(end>start&&end-start<=500)matches.add(text.substring(start,end));from=start;
            }
            if(matches.size()!=1)return new ArrayList<>();
            result.add(new Gap(matches.iterator().next(),j-i));i=j;
        }
        return result;
    }
    static List<String> solve(String stem,String body,QuizDocument document,boolean multiple){
        if(body.trim().isEmpty())return new ArrayList<>();
        if(document!=null){
            if(!document.complete())return new ArrayList<>();
            // An explicit answer label is useful even when the stem has no blank.
            Matcher explicit=Pattern.compile("(?:正确答案|参考答案|答案为|答案是)[:：\\s]*([A-H](?:[、,，\\s]*[A-H]){0,7})(?![A-Za-z])").matcher(body);
            Set<String> keys=new LinkedHashSet<>();while(explicit.find())keys.add(explicit.group(1).replaceAll("[^A-H]",""));
            if(keys.size()==1){String key=keys.iterator().next();List<String> chosen=document.choose(Arrays.asList(key),multiple);
                if(chosen.size()==key.length())return Arrays.asList(key);
            }
            if(!multiple&&isJudgment(document))return judgment(stem,body,document);
        }
        List<Gap> gaps=gaps(stem,QuizDocument.clean(body));
        if(gaps.isEmpty())return document==null?new ArrayList<>():optionContext(stem,body,document,multiple);
        List<String> result=new ArrayList<>();
        if(document==null){
            for(Gap gap:gaps){
                if(gap.count!=1||gap.value.length()>80)return new ArrayList<>();
                String original=originalAnswer(body,gap.value);if(original==null)return new ArrayList<>();result.add(original);
            }
            return result;
        }
        List<List<String>> solutions=new ArrayList<>();cover(gaps,0,gaps.get(0).value,gaps.get(0).count,document.options,new ArrayList<>(),solutions,new int[]{0});
        if(solutions.size()!=1)return result;
        String letters=String.join("",solutions.get(0));List<String> chosen=document.choose(Arrays.asList(letters),multiple);
        if(chosen.size()==letters.length())result.add(letters);return result;
    }
    private static List<String> optionContext(String stem,String body,QuizDocument document,boolean multiple){
        for(int anchor=8;anchor>=4;anchor--){
            List<String> answers=optionContext(stem,body,document,multiple,anchor);
            if(!answers.isEmpty())return answers;
        }
        return new ArrayList<>();
    }
    private static List<String> optionContext(String stem,String body,QuizDocument document,boolean multiple,int anchor){
        String[] pieces=stem.split("§",-1);int blanks=pieces.length-1;
        if(blanks<1||blanks>8||(!multiple&&blanks!=1))return new ArrayList<>();
        StringBuilder alternatives=new StringBuilder();
        for(String value:document.options.values()){
            if(value.isEmpty())return new ArrayList<>();
            if(alternatives.length()>0)alternatives.append('|');alternatives.append(Pattern.quote(value));
        }
        StringBuilder pattern=new StringBuilder();int context=0;
        for(int i=0;i<pieces.length;i++){
            String part=pieces[i];
            if(i==0)part=part.substring(Math.max(0,part.length()-anchor));
            if(i==pieces.length-1)part=part.substring(0,Math.min(anchor,part.length()));
            context+=part.length();pattern.append(Pattern.quote(part));
            if(i<blanks)pattern.append('(').append(alternatives).append(')');
        }
        if(context<4)return new ArrayList<>();
        Set<String> solutions=new LinkedHashSet<>();Matcher matcher=Pattern.compile(pattern.toString()).matcher(QuizDocument.clean(body));
        while(matcher.find()){
            Set<String> chosen=new LinkedHashSet<>();
            for(int i=1;i<=blanks;i++){
                String letter=null;
                for(Map.Entry<String,String> option:document.options.entrySet())if(option.getValue().equals(matcher.group(i))){
                    if(letter!=null)return new ArrayList<>();letter=option.getKey();
                }
                if(letter!=null)chosen.add(letter);
            }
            if(chosen.size()==blanks)solutions.add(String.join("",chosen));
        }
        return solutions.size()==1?Arrays.asList(solutions.iterator().next()):new ArrayList<>();
    }
    static boolean isJudgment(QuizDocument document){
        return document!=null&&document.complete()&&document.options.size()==2
                &&document.options.containsValue("正确")&&document.options.containsValue("错误");
    }
    private static List<String> judgment(String stem,String body,QuizDocument document){
        Set<String> verdicts=new LinkedHashSet<>();
        String bare=QuizDocument.clean(body);if(bare.equals("正确")||bare.equals("错误"))verdicts.add(bare);
        Matcher explicit=Pattern.compile("(?:正确答案|参考答案|答案|本题判断|本题说法|该说法|这一说法|上述说法|题干表述)(?:为|是)?[：:\\s]*(正确|错误)(?![\\p{IsHan}])").matcher(body);
        while(explicit.find())verdicts.add(explicit.group(1));
        if(verdicts.size()>1)return new ArrayList<>();
        String verdict=verdicts.size()==1?verdicts.iterator().next():null;
        // Trailing answer placeholders are not missing words in a true/false statement.
        String statement=stem.replaceAll("^§+|§+$","");
        if(verdict==null&&statement.length()>=4&&!statement.contains("§")&&statement.equals(QuizDocument.clean(body)))verdict="正确";
        // A changed fact, number or negation is not enough evidence to guess false.
        if(verdict!=null)for(Map.Entry<String,String> entry:document.options.entrySet())
            if(entry.getValue().equals(verdict))return Arrays.asList(entry.getKey());
        return new ArrayList<>();
    }
    private static String originalAnswer(String body,String value){
        // Matching ignores punctuation, but input answers must retain decimal points and Latin case.
        StringBuilder normalized=new StringBuilder();List<Integer> starts=new ArrayList<>(),ends=new ArrayList<>();
        for(int i=0;i<body.length();){int end=i+Character.charCount(body.codePointAt(i));String part=QuizDocument.clean(body.substring(i,end));
            if(Normalizer.normalize(body.substring(i,end),Normalizer.Form.NFKC).equals(".")&&i>0&&end<body.length()
                    &&Character.isDigit(body.charAt(i-1))&&Character.isDigit(body.charAt(end)))part=".";
            normalized.append(part);for(int j=0;j<part.length();j++){starts.add(i);ends.add(end);}i=end;
        }
        Set<String> candidates=new LinkedHashSet<>();int from=0;
        while(from<normalized.length()){
            int at=normalized.indexOf(value,from);if(at<0)break;
            candidates.add(ScreenRules.norm(body.substring(starts.get(at),ends.get(at+value.length()-1))));from=at+1;
        }
        return candidates.size()==1?candidates.iterator().next():null;
    }
    private static void cover(List<Gap> gaps,int index,String remaining,int count,Map<String,String> options,List<String> selected,List<List<String>> solutions,int[] calls){
        if(++calls[0]>1000){if(solutions.size()<2){solutions.add(new ArrayList<>());solutions.add(new ArrayList<>());}return;}
        if(solutions.size()>1)return;
        if(remaining.isEmpty()){
            if(count!=0)return;
            if(index+1==gaps.size()){solutions.add(new ArrayList<>(selected));return;}
            Gap next=gaps.get(index+1);cover(gaps,index+1,next.value,next.count,options,selected,solutions,calls);return;
        }
        if(count<=0)return;
        for(Map.Entry<String,String> option:options.entrySet())if(!selected.contains(option.getKey())&&!option.getValue().isEmpty()&&remaining.startsWith(option.getValue())){
            selected.add(option.getKey());cover(gaps,index,remaining.substring(option.getValue().length()),count-1,options,selected,solutions,calls);selected.remove(selected.size()-1);
        }
    }
}
