package io.github.autotaskrun;

import java.util.List;
import io.github.autotaskrun.PointsEntryLocator.Label;

final class QuizControls {
    static Label optionA(int w,int h,List<Label> labels){
        for(Label l:QuizDocument.rows(w,h,labels))if("A".equals(QuizDocument.letter(l.text)))return l;
        return null;
    }
    static Label button(int w,int h,List<Label> labels,String...names){
        for(String name:names)for(Label l:labels)if(ScreenRules.norm(l.text).equals(name)&&l.top>h*.035&&l.bottom<h*.97
                &&l.bottom-l.top<h*.12&&l.right-l.left<w*.65)return l;
        return null;
    }
    static boolean result(List<Label> labels){
        for(Label l:labels){String text=ScreenRules.norm(l.text);
            if(text.startsWith("本次答对")||text.startsWith("本次得分")||text.equals("再来一组")||text.equals("再练一次")||text.equals("答题完成"))return true;
        }
        return false;
    }
}
