package io.github.autotaskrun;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Verified per-task deltas, never local viewing counts or an assumed two points per minute. */
final class PointsAudit {
    static final String SUPPLEMENT="supplement";
    static int target(String id){switch(id){case "articles":case "videos":return 12;case "quiz":return 5;case "subscriptions":return 2;case "local":return 1;default:return 0;}}
    static List<String> scope(List<String> order){List<String> result=new ArrayList<>();for(String id:order)if(target(id)>0&&!result.contains(id))result.add(id);return result;}
    static Integer parse(String text,int target){
        Matcher m=Pattern.compile("(?<![0-9])([0-9]+)分?[/／]([0-9]+)分?(?![0-9])").matcher(ScreenRules.norm(text));
        while(m.find())try{int value=Integer.parseInt(m.group(1)),total=Integer.parseInt(m.group(2));if(total==target&&value>=0&&value<=total)return value;}catch(NumberFormatException ignored){}
        return null;
    }
    static final class Ledger {
        final List<String> tasks;
        final Map<String,Integer> before=new LinkedHashMap<>(),after=new LinkedHashMap<>();
        final Set<String> verified=new LinkedHashSet<>();
        Ledger(List<String> order){tasks=scope(order);}
        void begin(String id,int points){validate(id,points);if(after.containsKey(id)&&points<after.get(id))throw new IllegalArgumentException("积分低于上次核验，停止补做");before.putIfAbsent(id,points);after.put(id,points);verified.remove(id);}
        void finish(String id,int points){validate(id,points);if(!before.containsKey(id)||points<before.get(id)){verified.remove(id);return;}after.put(id,points);verified.add(id);}
        void unknown(String id){verified.remove(id);}
        int gained(){int result=0;for(String id:verified)result+=after.get(id)-before.get(id);return result;}
        boolean complete(){return verified.containsAll(tasks);}
        private void validate(String id,int points){if(!tasks.contains(id)||points<0||points>target(id))throw new IllegalArgumentException("无效任务积分");}
    }
    static final class Budget {
        static final int MAX_ACTIONS=12,MAX_STALLED=2;
        int actions,stalled;
        boolean canRun(int points,int target){return points<target&&actions<MAX_ACTIONS&&stalled<MAX_STALLED;}
        void record(int before,int after){actions++;stalled=after>before?0:stalled+1;}
    }
}
