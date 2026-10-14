package io.github.autotaskrun;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class AppIcon extends View {
    private final String kind;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    public AppIcon(Context context){this(context,"articles",0xFF21846B);}
    AppIcon(Context context,String kind,int color){super(context);this.kind=kind;paint.setColor(color);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.save();float size=Math.min(getWidth(),getHeight());c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/24,size/24);
        paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);Path p=path;p.reset();
        switch(kind){
            case "supplement":c.drawArc(3,3,21,21,35,290,false,paint);p.moveTo(18,2);p.lineTo(20,7);p.lineTo(15,7);c.drawPath(p,paint);c.drawLine(8,12,16,12,paint);c.drawLine(12,8,12,16,paint);break;
            case "articles":p.moveTo(3,5);p.quadTo(7,3,12,6);p.quadTo(17,3,21,5);p.lineTo(21,19);p.quadTo(17,17,12,20);p.quadTo(7,17,3,19);p.close();c.drawPath(p,paint);c.drawLine(12,6,12,20,paint);break;
            case "videos":c.drawRoundRect(2.5f,4,21.5f,20,4,4,paint);p.moveTo(10,8);p.lineTo(16,12);p.lineTo(10,16);p.close();c.drawPath(p,paint);break;
            case "quiz":c.drawRoundRect(4,3,20,22,3,3,paint);p.moveTo(7,9);p.lineTo(9,11);p.lineTo(12,7);c.drawPath(p,paint);c.drawLine(14,9,17,9,paint);c.drawLine(8,15,17,15,paint);c.drawLine(8,18,14,18,paint);break;
            case "subscriptions":c.drawCircle(10,8,4,paint);c.drawArc(3,13,17,26,190,160,false,paint);c.drawLine(19,12,19,19,paint);c.drawLine(15.5f,15.5f,22.5f,15.5f,paint);break;
            case "local":p.moveTo(12,22);p.cubicTo(7,16,4,12,4,9);p.cubicTo(4,-1,20,-1,20,9);p.cubicTo(20,12,17,16,12,22);c.drawPath(p,paint);c.drawCircle(12,9,3,paint);break;
            case "settings":c.drawCircle(12,12,7,paint);c.drawCircle(12,12,2.5f,paint);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,paint);}break;
            case "drag":paint.setStyle(Paint.Style.FILL);for(int x=9;x<=15;x+=6)for(int y=5;y<=19;y+=7)c.drawCircle(x,y,1.3f,paint);break;
            default:c.drawRoundRect(4,2,20,22,3,3,paint);for(int y=7;y<=17;y+=5)c.drawLine(8,y,16,y,paint);
        }
        c.restore();
    }
}
