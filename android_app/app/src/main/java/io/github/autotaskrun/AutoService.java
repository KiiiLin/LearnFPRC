package io.github.autotaskrun;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class AutoService extends AccessibilityService {
    static volatile AutoService instance;
    private volatile TaskEngine engine;
    private volatile String eventPackage="";
    private final Map<String,String> taskStatuses=new LinkedHashMap<>();
    private volatile String latestProgress="";
    private long lastShot;
    private View keepAwake;
    private WindowManager windowManager;

    @Override protected void onServiceConnected(){super.onServiceConnected();instance=this;MainActivity.refreshUi();}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event!=null && event.getPackageName()!=null)eventPackage=event.getPackageName().toString();
    }
    @Override public void onInterrupt(){stopRun();}
    @Override public void onDestroy(){stopRun();if(instance==this)instance=null;super.onDestroy();MainActivity.refreshUi();}

    synchronized boolean startRun(List<String> order,boolean continueOnFailure){
        if(engine!=null && engine.isAlive())return false;
        taskStatuses.clear();for(String task:order)taskStatuses.put(task,"waiting");
        latestProgress="正在准备任务";
        engine=new TaskEngine(this,new ArrayList<>(order),continueOnFailure);
        engine.start();return true;
    }
    synchronized void pauseRun(){if(engine!=null)engine.togglePause();}
    synchronized void stopRun(){if(engine!=null)engine.requestStop();}
    synchronized boolean running(){return engine!=null && engine.isAlive();}
    synchronized boolean paused(){return engine!=null && engine.paused();}
    synchronized Map<String,String> taskStatusSnapshot(){return new LinkedHashMap<>(taskStatuses);}
    String latestProgress(){return latestProgress;}
    void taskStarted(String id,String title){synchronized(this){taskStatuses.put(id,"running");}progress(title+" · 正在执行");}
    void taskFinished(String id,String status){synchronized(this){taskStatuses.put(id,status);}MainActivity.refreshUi();}
    void finished(TaskEngine current){
        setKeepAwake(false);
        synchronized(this){if(engine==current){engine=null;taskStatuses.replaceAll((id,state)->state.equals("waiting")?"not_run":state.equals("running")?"stopped":state);}}
        MainActivity.refreshUi();
    }
    void publish(String line){MainActivity.appendLog(line);}
    void progress(String value){latestProgress=value;MainActivity.setProgress(value);}
    void showResults(){
        new Handler(Looper.getMainLooper()).post(()->{
            Intent intent=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("show_results",true);
            try{startActivity(intent);}catch(Exception error){publish("自动返回助手失败，请手动打开查看本次积分");}
        });
    }

    void launchTarget() throws Exception {
        Intent launch=getPackageManager().getLaunchIntentForPackage("cn.xuexi.android");
        if(launch==null)throw new Exception("未安装学习强国，或无法读取其启动入口");
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(launch);
    }
    boolean targetForeground(){
        AccessibilityNodeInfo root=getRootInActiveWindow();
        return root!=null&&root.getPackageName()!=null&&"cn.xuexi.android".contentEquals(root.getPackageName());
    }
    static final class WindowSnapshot {
        final String pkg;final int id;final List<Vision.Line> nodes;
        WindowSnapshot(String pkg,int id,List<Vision.Line> nodes){this.pkg=pkg;this.id=id;this.nodes=nodes;}
    }
    WindowSnapshot windowSnapshot(){
        AccessibilityNodeInfo root=getRootInActiveWindow();List<Vision.Line> lines=new ArrayList<>();
        if(root==null||root.getPackageName()==null)return new WindowSnapshot("",-1,lines);
        String pkg=root.getPackageName().toString();if("cn.xuexi.android".equals(pkg))walk(root,lines,0);
        return new WindowSnapshot(pkg,root.getWindowId(),lines);
    }
    Bitmap screenshot() throws Exception {return screenshot(12000);}
    Bitmap screenshot(long timeoutMs) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+timeoutMs;
        long delay=750-(SystemClock.elapsedRealtime()-lastShot);
        if(delay>0)Thread.sleep(Math.min(delay,Math.max(0,timeoutMs)));
        if(SystemClock.elapsedRealtime()>=deadline)throw new java.util.concurrent.TimeoutException("截图超过页面等待时限");
        lastShot=SystemClock.elapsedRealtime();
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<Bitmap> value=new AtomicReference<>();
        AtomicReference<String> error=new AtomicReference<>();
        takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
            @Override public void onSuccess(ScreenshotResult result){
                try{
                    Bitmap hardware=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());
                    if(hardware==null)error.set("截图返回空图像");
                    else{value.set(hardware.copy(Bitmap.Config.ARGB_8888,false));hardware.recycle();}
                    result.getHardwareBuffer().close();
                }catch(Exception ex){error.set(ex.getMessage());}finally{done.countDown();}
            }
            @Override public void onFailure(int code){error.set("截图失败，代码 "+code);done.countDown();}
        });
        if(!done.await(Math.max(1,deadline-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS))throw new java.util.concurrent.TimeoutException("截图等待超时");
        if(value.get()==null)throw new Exception(error.get()==null?"截图失败":error.get());
        return value.get();
    }
    void tap(int x,int y)throws Exception{gesture(x,y,x,y,70);}
    void tap(int x,int y,long timeoutMs)throws Exception{gesture(x,y,x,y,70,timeoutMs);}
    void swipe(int x1,int y1,int x2,int y2,int duration)throws Exception{gesture(x1,y1,x2,y2,duration);}
    private void gesture(int x1,int y1,int x2,int y2,int duration)throws Exception{
        gesture(x1,y1,x2,y2,duration,8000);
    }
    private void gesture(int x1,int y1,int x2,int y2,int duration,long timeoutMs)throws Exception{
        Path path=new Path();path.moveTo(x1,y1);path.lineTo(x2,y2);
        GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(path,0,duration);
        GestureDescription desc=new GestureDescription.Builder().addStroke(stroke).build();
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Boolean> okay=new AtomicReference<>(false);
        boolean queued=dispatchGesture(desc,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gestureDescription){okay.set(true);done.countDown();}
            @Override public void onCancelled(GestureDescription gestureDescription){done.countDown();}
        },new Handler(Looper.getMainLooper()));
        if(!queued || !done.await(Math.max(1,timeoutMs),TimeUnit.MILLISECONDS) || !okay.get())throw new Exception("无障碍手势被取消或超时，已停止");
    }
    void back()throws Exception{if(!performGlobalAction(GLOBAL_ACTION_BACK))throw new Exception("返回手势失败");}
    boolean clickControl(PointsEntryLocator.Label target,int width,int height){
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null||root.getPackageName()==null||!"cn.xuexi.android".contentEquals(root.getPackageName()))return false;
        return clickMatching(root,target,width,height,0);
    }
    private boolean clickMatching(AccessibilityNodeInfo node,PointsEntryLocator.Label target,int w,int h,int depth){
        if(depth>30)return false;
        Rect bounds=new Rect();node.getBoundsInScreen(bounds);
        CharSequence text=node.getText(),description=node.getContentDescription();
        String expected=ScreenRules.norm(target.text);
        boolean matches=text!=null&&ScreenRules.norm(text.toString()).equals(expected)
                ||description!=null&&ScreenRules.norm(description.toString()).equals(expected);
        if(matches&&node.isVisibleToUser()&&Math.abs(bounds.centerX()-target.x())<w*.04&&Math.abs(bounds.centerY()-target.y())<h*.025){
            AccessibilityNodeInfo action=node;
            for(int level=0;action!=null&&level<4;level++,action=action.getParent()){
                Rect area=new Rect();action.getBoundsInScreen(area);
                // Do not climb into an entire toolbar or question card: siblings may include AI or answers.
                if(area.width()>w*.26||area.height()>h*.09||!area.contains(target.x(),target.y())
                        ||Math.abs(area.centerX()-target.x())>w*.045||Math.abs(area.centerY()-target.y())>h*.025)break;
                if(action.isClickable()&&action.isEnabled())return action.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null&&clickMatching(child,target,w,h,depth+1))return true;}
        return false;
    }
    List<Vision.Line> nodes(){
        List<Vision.Line> out=new ArrayList<>();
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root!=null)walk(root,out,0);
        return out;
    }
    private void walk(AccessibilityNodeInfo node,List<Vision.Line> out,int depth){
        if(depth>30 || out.size()>500)return;
        CharSequence text=node.getText();
        if((text==null || text.length()==0) && node.getContentDescription()!=null)text=node.getContentDescription();
        if(node.isVisibleToUser()&&text!=null && text.length()>0){Rect b=new Rect();node.getBoundsInScreen(b);if(!b.isEmpty())out.add(new Vision.Line(text.toString(),b));}
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)walk(child,out,depth+1);}
    }
    List<AccessibilityNodeInfo> inputFields(int minY,int maxY){
        List<AccessibilityNodeInfo> out=new ArrayList<>();AccessibilityNodeInfo root=getRootInActiveWindow();
        collectInputs(root,out,minY,maxY,0);return out;
    }
    private void collectInputs(AccessibilityNodeInfo node,List<AccessibilityNodeInfo> out,int minY,int maxY,int depth){
        if(node==null || depth>30)return;
        CharSequence type=node.getClassName();Rect b=new Rect();node.getBoundsInScreen(b);
        if(type!=null && type.toString().contains("EditText") && node.isVisibleToUser() && node.isEnabled() && b.width()>0 && b.centerY()>minY && b.centerY()<maxY)out.add(node);
        for(int i=0;i<node.getChildCount();i++)collectInputs(node.getChild(i),out,minY,maxY,depth+1);
    }
    boolean setInput(AccessibilityNodeInfo node,String text){
        Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);
    }
    void setKeepAwake(boolean enabled){
        new Handler(Looper.getMainLooper()).post(()->{
            try{
                if(enabled && keepAwake==null){
                    windowManager=(WindowManager)getSystemService(WINDOW_SERVICE);
                    View view=new View(this);view.setBackgroundColor(0x00000000);
                    WindowManager.LayoutParams p=new WindowManager.LayoutParams(1,1,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,PixelFormat.TRANSLUCENT);
                    p.gravity=Gravity.TOP|Gravity.START;windowManager.addView(view,p);keepAwake=view;
                }else if(!enabled && keepAwake!=null){windowManager.removeView(keepAwake);keepAwake=null;}
            }catch(Exception ignored){keepAwake=null;}
        });
    }
}
