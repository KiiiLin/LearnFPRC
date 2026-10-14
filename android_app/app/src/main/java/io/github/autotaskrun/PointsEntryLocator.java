package io.github.autotaskrun;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Pure geometry/text policy, shared by OCR and accessibility observations. */
final class PointsEntryLocator {
    static final class Label {
        final String text;
        final int left, top, right, bottom;
        final boolean node;
        Label(String text,int left,int top,int right,int bottom,boolean node){
            this.text=text;this.left=left;this.top=top;this.right=right;this.bottom=bottom;this.node=node;
        }
        int x(){return (left+right)/2;}
        int y(){return (top+bottom)/2;}
    }
    private static String clean(String text){
        return Normalizer.normalize(text,Normalizer.Form.NFKC).replaceAll("\\s+","").toLowerCase(Locale.ROOT);
    }
    static Label locate(int width,int height,List<Label> labels){
        Label best=null;int bestRank=-1;
        for(Label candidate:labels){
            String text=clean(candidate.text);
            if(candidate.left<width*.50 || candidate.right>width*.92 || candidate.top<height*.035
                    || candidate.bottom>height*.19 || candidate.right<=candidate.left || candidate.bottom<=candidate.top
                    || candidate.right-candidate.left>width*.25 || candidate.bottom-candidate.top>height*.07)continue;
            boolean explicit=text.matches("(?:学习)?积分(?:[0-9]{1,8})?(?:分)?|[0-9]{1,8}积分");
            // A vertical 积/分 label can be misread as one Chinese character (e.g. 磐6833).
            boolean number=text.matches("[\\u4e00-\\u9fff]{0,2}[0-9]{1,8}分?");
            if(!explicit&&!number)continue;
            boolean toolbar=false,adjacentPoints=false,blocked=false;
            for(Label anchor:labels){
                String value=clean(anchor.text);
                boolean sameRow=Math.abs(anchor.y()-candidate.y())<height*.022;
                if(value.equals("我的")&&anchor.left>width*.82&&anchor.y()>height*.035&&anchor.y()<height*.19
                        &&sameRow&&candidate.right<anchor.left-width*.015)toolbar=true;
                if(value.matches("积分|积|分")&&sameRow&&anchor.x()<=candidate.x()
                        &&candidate.left-anchor.right<width*.075&&candidate.left-anchor.right>-width*.025)adjacentPoints=true;
                if((value.equals("ai")||value.contains("智能助手"))&&sameRow&&candidate.right>=anchor.left)blocked=true;
            }
            if(blocked)continue;
            // Unlabelled numbers are permitted only in the score zone of a confirmed toolbar.
            if(!(toolbar||explicit&&adjacentPoints))continue;
            if(!explicit&&!adjacentPoints&&candidate.right>width*.79)continue;
            // Prefer the large score number over the tiny vertical 积分 label.
            int rank=(text.matches(".*[0-9].*")?80:0)+(explicit?20:0)+(candidate.node?8:0)+(adjacentPoints?10:0);
            if(rank>bestRank || rank==bestRank&&best!=null&&candidate.right-candidate.left<best.right-best.left){
                best=candidate;bestRank=rank;
            }
        }
        return best;
    }
}
