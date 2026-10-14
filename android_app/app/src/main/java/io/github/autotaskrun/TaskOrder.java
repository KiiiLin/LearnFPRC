package io.github.autotaskrun;

import java.util.List;

final class TaskOrder {
    static void pinSupplement(List<String> order){if(order.remove(PointsAudit.SUPPLEMENT))order.add(PointsAudit.SUPPLEMENT);}
    static boolean move(List<String> order,String source,String target){
        if(PointsAudit.SUPPLEMENT.equals(source))return false;
        int from=order.indexOf(source),to=order.indexOf(target);
        if(from<0||to<0||from==to)return false;
        order.remove(from);order.add(to,source);pinSupplement(order);return true;
    }
}
