package io.github.autotaskrun;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.SystemClock;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Vision {
    static final class Line {
        final String text;
        final Rect box;
        Line(String text, Rect box) { this.text = text == null ? "" : text.trim(); this.box = box; }
        int x() { return box.centerX(); }
        int y() { return box.centerY(); }
    }
    static final class Frame {
        final Bitmap image;
        final List<Line> lines;
        final List<Line> nodes;
        final int w, h;
        Frame(Bitmap image, List<Line> lines, List<Line> nodes) {
            this.image = image; this.lines = lines; this.nodes = nodes;
            w = image.getWidth(); h = image.getHeight();
        }
        List<Line> all() { List<Line> out = new ArrayList<>(lines); out.addAll(nodes); return out; }
        Line find(String text) {
            for (Line l : all()) if (l.text.contains(text)) return l;
            return null;
        }
        boolean has(String text) { return find(text) != null; }
    }
    static final class Article {
        final String title; final Rect box; final boolean unread; final double dark;
        Article(String title, Rect box, boolean unread, double dark) {
            this.title=title; this.box=box; this.unread=unread; this.dark=dark;
        }
    }
    static final class Video {
        final String title; final int duration, x, y; final boolean eligible,unread;
        Video(String title, int duration, int x, int y,boolean unread) {
            this.title=title; this.duration=duration; this.x=x; this.y=y;this.unread=unread;this.eligible=duration>=90&&unread;
        }
    }

    private final TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    List<Line> read(Bitmap bitmap) throws Exception {
        return read(bitmap,15000);
    }
    List<Line> read(Bitmap bitmap,long timeoutMs) throws Exception {
        if(timeoutMs<=0)throw new java.util.concurrent.TimeoutException("OCR 超过页面等待时限");
        Text recognized = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), timeoutMs, TimeUnit.MILLISECONDS);
        List<Line> lines = new ArrayList<>();
        for (Text.TextBlock block : recognized.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                if (box != null && !line.getText().trim().isEmpty()) lines.add(new Line(line.getText(), box));
            }
        }
        lines.sort(Comparator.comparingInt((Line l)->l.box.top).thenComparingInt(l->l.box.left));
        return lines;
    }
    void close() { recognizer.close(); }

    static List<PointsEntryLocator.Label> labels(Frame f){
        List<PointsEntryLocator.Label> out=new ArrayList<>();
        for(Line l:f.nodes)out.add(new PointsEntryLocator.Label(l.text,l.box.left,l.box.top,l.box.right,l.box.bottom,true));
        for(Line l:f.lines)out.add(new PointsEntryLocator.Label(l.text,l.box.left,l.box.top,l.box.right,l.box.bottom,false));
        return out;
    }
    void enhance(Frame f,double x1,double y1,double x2,double y2)throws Exception{
        int left=(int)(f.w*x1),top=(int)(f.h*y1),right=(int)(f.w*x2),bottom=(int)(f.h*y2);
        Bitmap crop=Bitmap.createBitmap(f.image,left,top,right-left,bottom-top);
        Bitmap enlarged=Bitmap.createScaledBitmap(crop,crop.getWidth()*2,crop.getHeight()*2,true);
        try{for(Line l:read(enlarged))f.lines.add(new Line(l.text,new Rect(left+l.box.left/2,top+l.box.top/2,left+l.box.right/2,top+l.box.bottom/2)));}
        finally{enlarged.recycle();crop.recycle();}
    }

    PointsEntryLocator.Label pointsEntry(Frame f) throws Exception {
        List<PointsEntryLocator.Label> observed=new ArrayList<>();
        for(Line l:f.nodes)observed.add(new PointsEntryLocator.Label(l.text,l.box.left,l.box.top,l.box.right,l.box.bottom,true));
        for(Line l:f.lines)observed.add(new PointsEntryLocator.Label(l.text,l.box.left,l.box.top,l.box.right,l.box.bottom,false));
        PointsEntryLocator.Label found=PointsEntryLocator.locate(f.w,f.h,observed);
        if(found!=null&&ScreenRules.norm(found.text).matches(".*[0-9].*"))return found;
        // Magnify the toolbar for small score text; this crop never supplies a blind tap position.
        int left=(int)(f.w*.48),top=(int)(f.h*.035),right=(int)(f.w*.98),bottom=(int)(f.h*.19);
        Bitmap crop=Bitmap.createBitmap(f.image,left,top,right-left,bottom-top);
        Bitmap enlarged=Bitmap.createScaledBitmap(crop,crop.getWidth()*2,crop.getHeight()*2,true);
        try{
            for(Line l:read(enlarged))observed.add(new PointsEntryLocator.Label(l.text,
                    left+l.box.left/2,top+l.box.top/2,left+l.box.right/2,top+l.box.bottom/2,false));
        }finally{enlarged.recycle();crop.recycle();}
        return PointsEntryLocator.locate(f.w,f.h,observed);
    }

    static String norm(String value) {
        String source = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return source.replaceAll("[^a-z0-9_\\u4e00-\\u9fff]", "");
    }
    static boolean same(String a, String b) {
        a=norm(a); b=norm(b);
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        if (Math.min(a.length(), b.length())>=10 && (a.contains(b)||b.contains(a))) return true;
        if (Math.min(a.length(), b.length())<8) return false;
        return (double) lcs(a,b).length() / Math.max(a.length(), b.length()) >= .82;
    }
    private static String lcs(String a,String b) {
        int[][] d=new int[a.length()+1][b.length()+1];
        int best=0,end=0;
        for (int i=1;i<=a.length();i++) for (int j=1;j<=b.length();j++) {
            if(a.charAt(i-1)==b.charAt(j-1)) { d[i][j]=d[i-1][j-1]+1; if(d[i][j]>best){best=d[i][j];end=i;} }
        }
        return a.substring(end-best,end);
    }
    static boolean isHome(Frame f) {
        return ScreenRules.home(f.w,f.h,labels(f));
    }
    static int[] navBand(Frame f) {
        List<Line> anchors=new ArrayList<>();
        for(Line l:f.all()) if(l.y()>f.h*.035 && l.y()<f.h*.25 &&
                ("推荐 要闻 思想 乡村阅读 发现 上海 北京 综合 融媒中心 地方".contains(l.text) && l.text.length()>=2)) anchors.add(l);
        int best=0,top=0,bottom=0;
        for(Line a:anchors){int count=0,t=a.box.top,b=a.box.bottom;
            for(Line other:anchors) if(Math.abs(a.y()-other.y())<f.h*.015){count++;t=Math.min(t,other.box.top);b=Math.max(b,other.box.bottom);}
            if(count>best){best=count;top=t;bottom=b;}}
        return best>=2?new int[]{top,bottom}:new int[]{(int)(f.h*.095),(int)(f.h*.17)};
    }
    static Line regionTab(Frame f) {
        int[] band=navBand(f);
        for(Line anchor:f.all()) if(anchor.text.equals("综合") && anchor.y()>=band[0] && anchor.y()<=band[1]){
            Line result=null;
            for(Line l:f.all()) if(l.x()<anchor.x() && anchor.x()-l.x()>f.w*.02 && anchor.x()-l.x()<f.w*.18
                    && Math.abs(l.y()-anchor.y())<f.h*.018 && l.text.matches("[\\u4e00-\\u9fff]{2,6}"))
                if(result==null || l.x()>result.x()) result=l;
            if(result!=null) return result;
        }
        return null;
    }
    static boolean red(Bitmap image, Rect box, double threshold) {
        int left=Math.max(0,box.left),right=Math.min(image.getWidth(),box.right);
        int top=Math.max(0,box.top),bottom=Math.min(image.getHeight(),box.bottom);
        int red=0,all=0;
        for(int y=top;y<bottom;y+=2) for(int x=left;x<right;x+=2){
            int c=image.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c);
            if(r>140 && r-g>45 && r-b>45) red++; all++;
        }
        return all>0 && (double)red/all>threshold;
    }
    static boolean regionSelected(Frame f) {
        Line tab=regionTab(f);
        return tab!=null && red(f.image,tab.box,.04);
    }
    static boolean bailingSelected(Frame f) {
        if(!isHome(f)) return false;
        for(Line l:f.all()) if(l.text.equals("百灵") && l.y()>f.h*.80 && red(f.image,l.box,.03)) return true;
        return false;
    }
    static List<Article> articles(Frame f) {
        int[] band=navBand(f);
        double floor=(double)band[1]/f.h+.02;
        boolean shortcuts=f.has("切换地区");
        if(shortcuts) for(Line l:f.lines) if(l.y()<f.h*.36 && (l.text.contains("学习平台")||l.text.contains("上观新闻")||l.text.contains("红途")||l.text.contains("乡村阅读")))
            floor=Math.max(floor,(double)l.box.bottom/f.h+.018);
        List<Line> fragments=new ArrayList<>();
        for(Line l:f.lines) {
            Rect b=l.box;
            if(!(b.top>=floor*f.h && b.bottom<=f.h*.91)) continue;
            if(b.left>f.w*.16 || b.right>f.w*.65 || b.width()<f.w*.08) continue;
            if(b.height()<f.h*.014 || b.height()>f.h*.065) continue;
            if(l.text.matches(".*(\\d{4}[-年/.]\\d{1,2}|切换地区|学习平台|(?i:step)\\s*\\d).*")) continue;
            fragments.add(l);
        }
        List<List<Line>> groups=new ArrayList<>();
        for(Line l:fragments){
            if(!groups.isEmpty()){
                List<Line> last=groups.get(groups.size()-1);Line end=last.get(last.size()-1);
                int gap=l.box.top-end.box.bottom;
                if(gap>=0 && gap<=f.h*.022){last.add(l);continue;}
            }
            List<Line> group=new ArrayList<>();group.add(l);groups.add(group);
        }
        List<Article> out=new ArrayList<>();
        for(List<Line> group:groups){
            StringBuilder title=new StringBuilder();Rect box=new Rect(group.get(0).box);
            for(Line l:group){title.append(l.text);box.union(l.box);}
            if(norm(title.toString()).length()<7 || box.bottom>f.h*.89) continue;
            double dark=inkDark(f.image,group);
            boolean unread=dark>=.30;
            for(Line l:f.lines) if(l.text.matches("\\d{1,2}:\\d{2}") && l.box.left>f.w*.65
                    && l.y()>box.top-f.h*.02 && l.y()<box.bottom+f.h*.075) unread=false;
            if(title.toString().matches("^[【\\[]?(视频|直播).*")) unread=false;
            out.add(new Article(title.toString(),box,unread,dark));
        }
        return out;
    }
    private static double inkDark(Bitmap image,List<Line> lines){int ink=0,dark=0;
        for(Line l:lines){Rect b=l.box;
            for(int y=Math.max(0,b.top+2);y<Math.min(image.getHeight(),b.bottom-2);y+=2)
                for(int x=Math.max(0,b.left+2);x<Math.min(image.getWidth(),b.right-2);x+=2){
                    int c=image.getPixel(x,y),r=Color.red(c),g=Color.green(c),bl=Color.blue(c);
                    int mean=(r+g+bl)/3;
                    if(Math.max(r,Math.max(g,bl))-Math.min(r,Math.min(g,bl))<45 && mean<225){ink++;if(mean<150)dark++;}
                }
        }
        return ink==0?0:(double)dark/ink;
    }
    static boolean articleDetail(Frame f,String title){
        return ContentRules.article(f.w,f.h,labels(f),title);
    }
    static Integer seconds(String text){
        Matcher m=Pattern.compile("^\\s*(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})\\s*$").matcher(text);
        if(!m.matches())return null;
        int mm=Integer.parseInt(m.group(2)),ss=Integer.parseInt(m.group(3));
        if(mm>=60 || ss>=60)return null;
        return Integer.parseInt(m.group(1)==null?"0":m.group(1))*3600+mm*60+ss;
    }
    static List<Video> videos(Frame f){
        List<Line> badges=new ArrayList<>();
        for(Line l:f.lines) if(l.y()>f.h*.14 && l.y()<f.h*.83 && seconds(l.text)!=null) badges.add(l);
        boolean hasLeft=false;
        for(Line l:badges) if(l.x()>f.w*.25 && l.x()<f.w*.5)hasLeft=true;
        List<Video> out=new ArrayList<>();
        for(Line b:badges){
            int col=b.x()<f.w*.5?0:1;
            double left=col*f.w*.5,right=(col+1)*f.w*.5;
            Line title=null;
            for(Line l:f.lines) if(l.x()>left+f.w*.015 && l.x()<right-f.w*.015
                    && l.box.top>=b.box.bottom+f.h*.008 && l.box.top<=b.box.bottom+f.h*.085
                    && l.box.height()>=f.h*.012 && l.box.height()<f.h*.05 && l.text.length()>=4)
                if(title==null || l.box.top<title.box.top)title=l;
            int x=(int)(f.w*(col==0?.25:.75)),y=(int)(b.y()-f.h*.07);
            if(title==null && !hasLeft && col==1 && b.box.left>f.w*.74){
                for(Line l:f.lines) if(l.x()<f.w*.62 && l.box.top>=b.box.bottom+f.h*.008
                        && l.box.top<=b.box.bottom+f.h*.075 && l.text.length()>=4)
                    if(title==null || l.box.top<title.box.top)title=l;
                x=f.w/2;y=(int)Math.max(f.h*.20,b.y()-f.h*.15);
            }
            if(title!=null && y>=f.h*.19){
                List<Line> parts=new ArrayList<>();parts.add(title);Line previous=title;
                List<Line> ordered=new ArrayList<>(f.lines);ordered.sort(Comparator.comparingInt(l->l.box.top));
                for(Line line:ordered){
                    if(parts.size()>=3)break;
                    if(line.box.top<=previous.box.bottom||line.box.top-previous.box.bottom>f.h*.022)continue;
                    if(Math.abs(line.box.left-title.box.left)>f.w*.06||line.x()<left||line.x()>right
                            ||line.box.height()<title.box.height()*.85||line.box.height()>title.box.height()*1.3
                            ||line.box.bottom>title.box.bottom+f.h*.095||seconds(line.text)!=null)continue;
                    parts.add(line);previous=line;
                }
                StringBuilder fullTitle=new StringBuilder();VideoIdentity.Ink ink=new VideoIdentity.Ink();
                for(Line part:parts){fullTitle.append(part.text);Rect box=part.box;
                    for(int py=Math.max(0,box.top+2);py<Math.min(f.h,box.bottom-2);py+=2)
                        for(int px=Math.max(0,box.left+2);px<Math.min(f.w,box.right-2);px+=2)ink.add(f.image.getPixel(px,py));
                }
                boolean duplicate=false;for(Video existing:out)if(VideoIdentity.same(existing.title,fullTitle.toString())){duplicate=true;break;}
                if(!duplicate)out.add(new Video(fullTitle.toString(),seconds(b.text),x,y,ink.unread()));
            }
        }
        return out;
    }
    static boolean player(Frame f,String title,int expected){
        return ContentRules.player(f.w,f.h,labels(f),title,expected);
    }
}
