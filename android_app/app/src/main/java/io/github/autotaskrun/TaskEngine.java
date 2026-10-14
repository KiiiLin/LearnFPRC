package io.github.autotaskrun;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TaskEngine extends Thread {
    private final AutoService service;
    private final List<String> order;
    private final boolean continueOnFailure;
    private final Object pauseLock=new Object();
    private volatile boolean stop=false,paused=false;
    private volatile int pauseVersion=0;
    private Vision vision;
    private Vision.Frame lastFrame;
    private String lastFrameTag="";
    private Journal journal;
    private File runDir,events,report,lastLog;
    private final String day=LocalDate.now().toString();
    private final JSONObject results=new JSONObject();
    private final PointsAudit.Ledger audit;
    private int studyGoal=6;
    private boolean returnWhenFinished=false;
    private static final String APP="cn.xuexi.android";
    private static final class ForegroundLost extends Exception {ForegroundLost(){super("前台窗口已切换或截图不一致，已中断本批任务；请返回学习强国后重新执行");}}
    private static final class StartupTimeout extends Exception {StartupTimeout(){super("启动/开屏广告等待超过 15 秒，已中断本批任务");}}
    private static final class QuizPageFailure extends Exception {QuizPageFailure(String reason){super(reason+"；保留当前题目，中断本批，未提交不完整答案");}}
    private static final class QuizAnswerUnavailable extends Exception {QuizAnswerUnavailable(String reason){super(reason);}}

    TaskEngine(AutoService service,List<String> order,boolean continueOnFailure){
        super("LearnFPRC");this.service=service;this.order=new ArrayList<>(order);TaskOrder.pinSupplement(this.order);this.continueOnFailure=continueOnFailure;
        audit=new PointsAudit.Ledger(this.order);
    }
    boolean paused(){return paused;}
    void togglePause(){synchronized(pauseLock){paused=!paused;if(!paused)pauseVersion++;pauseLock.notifyAll();}log(paused?"已暂停":"已继续");}
    void requestStop(){stop=true;synchronized(pauseLock){paused=false;pauseLock.notifyAll();}interrupt();}
    private void checkpoint()throws Exception{
        if(stop)throw new Exception("用户停止执行");
        if(!day.equals(LocalDate.now().toString()))throw new Exception("已跨过午夜，请重新运行任务");
        while(paused&&!stop){synchronized(pauseLock){pauseLock.wait(500);}}
        if(stop)throw new Exception("用户停止执行");
    }
    private void waitMs(long ms)throws Exception{long end=SystemClock.elapsedRealtime()+ms;
        while(SystemClock.elapsedRealtime()<end){checkpoint();Thread.sleep(Math.max(0,Math.min(250,end-SystemClock.elapsedRealtime())));}
    }
    private void log(String message){
        String line=LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))+"  "+message;
        try{
            if(lastLog!=null)try(FileWriter writer=new FileWriter(lastLog,true)){writer.write(line+"\n");}
            if(events!=null)try(FileWriter writer=new FileWriter(events,true)){
                writer.write(new JSONObject().put("at",System.currentTimeMillis()).put("message",message).toString()+"\n");}
        }catch(Exception ignored){}
        service.publish(line);
    }
    private void progress(String task,int done,int target,int remaining){
        service.progress(task+"  "+done+"/"+target+(remaining>0?" · 剩余 "+remaining+" 秒":""));
    }
    private void waitForTargetDuringTimer()throws Exception{
        if(service.targetForeground())return;
        log("学习强国暂时不在前台，当前计时作废；返回后重新计时");
        long deadline=SystemClock.elapsedRealtime()+60000;
        while(!service.targetForeground()){
            if(stop)throw new Exception("用户停止执行");
            if(!paused&&SystemClock.elapsedRealtime()>deadline)throw new Exception("一分钟内未返回学习强国，当前项目不计数");
            Thread.sleep(300);
        }
    }
    @Override public void run(){
        try{
            runDir=new File(service.getFilesDir(),"runs/"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            if(!runDir.mkdirs())throw new Exception("无法创建运行日志目录");
            events=new File(runDir,"events.jsonl");report=new File(runDir,"report.json");lastLog=new File(service.getFilesDir(),"last.log");
            Files.write(lastLog.toPath(),new byte[0]);
            Files.write(new File(service.getFilesDir(),"last_summary.json").toPath(),"{}".getBytes(StandardCharsets.UTF_8));
            journal=new Journal(service);vision=new Vision();service.setKeepAwake(true);
            log("开始手机本机任务；OCR 和进度数据只保存在本机");
            prepareStartup();
            for(String task:order){
                checkpoint();service.taskStarted(task,taskName(task));log("开始："+taskName(task));
                JSONObject one=new JSONObject().put("task",task).put("status","failed");
                try{
                    if(task.equals(PointsAudit.SUPPLEMENT))runSupplement(one);else runAudited(task,one,false);
                }catch(Exception failure){
                    if(!stop&&lastFrame!=null)diagnose(lastFrame,"最近一次成功截图识别/"+lastFrameTag);
                    one.put("status",stop?"stopped":"failed").put("reason",failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());
                    if(task.equals("articles")||task.equals("videos"))one.put("completed",journal.completed(task));
                    log(taskName(task)+"："+one.getString("status")+" · "+one.getString("reason"));
                    if(failure instanceof ForegroundLost||failure instanceof StartupTimeout||failure instanceof QuizPageFailure)stop=true;
                }
                results.put(task,one);
                saveSummary(false);
                service.taskFinished(task,one.getString("status"));
                byte[] reportBytes=results.toString(2).getBytes(StandardCharsets.UTF_8);
                Files.write(report.toPath(),reportBytes);
                Files.write(new File(service.getFilesDir(),"last_report.json").toPath(),reportBytes);
                log("结束："+taskName(task)+" · "+one.getString("status"));
                if(stop || (!continueOnFailure&&!("completed".equals(one.getString("status"))||"partial".equals(one.getString("status")))))break;
            }
            for(String task:order)if(!results.has(task)){
                results.put(task,new JSONObject().put("task",task).put("status","not_run").put("reason","本批已中断，未执行后续任务"));service.taskFinished(task,"not_run");
            }
            saveSummary(true);
            byte[] finalReport=results.toString(2).getBytes(StandardCharsets.UTF_8);Files.write(report.toPath(),finalReport);Files.write(new File(service.getFilesDir(),"last_report.json").toPath(),finalReport);
            log("本批任务结束；"+summaryText());service.progress(summaryText());returnWhenFinished=!stop;
        }catch(Exception ex){
            log("运行停止："+ex.getMessage());service.progress("运行已停止，原因见日志");
            try{
                for(String task:order)if(!results.has(task)){
                    results.put(task,new JSONObject().put("task",task).put("status","not_run").put("reason",ex.getMessage()));
                    service.taskFinished(task,"not_run");
                }
                if(report!=null){saveSummary(true);byte[] data=results.toString(2).getBytes(StandardCharsets.UTF_8);Files.write(report.toPath(),data);Files.write(new File(service.getFilesDir(),"last_report.json").toPath(),data);}
            }catch(Exception ignored){}
        }
        finally{if(vision!=null)vision.close();service.finished(this);if(returnWhenFinished)service.showResults();}
    }
    private String taskName(String id){switch(id){case "articles":return "我要选读文章";case "videos":return "我要视听学习";
        case "quiz":return "每日答题";case "subscriptions":return "订阅";case "local":return "本地频道";case "supplement":return "补充积分";default:return id;}}
    private String summaryText(){return (audit.complete()?"本次共获得 ":"本次已核验获得 ")+audit.gained()+" 积分"+(audit.complete()?"":"，部分任务未核验");}
    private void saveSummary(boolean finished)throws Exception{
        JSONObject detail=new JSONObject();
        for(String task:audit.tasks){JSONObject item=new JSONObject().put("title",taskName(task)).put("target",PointsAudit.target(task))
                .put("before",audit.before.containsKey(task)?audit.before.get(task):JSONObject.NULL)
                .put("after",audit.after.containsKey(task)?audit.after.get(task):JSONObject.NULL).put("verified",audit.verified.contains(task));
            item.put("gained",audit.verified.contains(task)?audit.after.get(task)-audit.before.get(task):JSONObject.NULL);detail.put(task,item);}
        JSONObject summary=new JSONObject().put("at",System.currentTimeMillis()).put("finished",finished).put("total_gained",audit.gained())
                .put("fully_verified",audit.complete()).put("text",summaryText()).put("tasks",detail);
        results.put("_summary",summary);Files.write(new File(service.getFilesDir(),"last_summary.json").toPath(),summary.toString(2).getBytes(StandardCharsets.UTF_8));
    }
    private boolean fatal(Exception error){return stop||error instanceof ForegroundLost||error instanceof StartupTimeout||error instanceof QuizPageFailure;}
    private int readTaskPoints(String task,boolean settle)throws Exception{
        returnPoints();Integer previous=null;int latest=-1;
        for(int i=0;i<5;i++){
            if(i>0||settle)waitMs(settle?1600:650);
            Row row=seekRow(taskName(task));
            if(row.earned!=null&&row.total!=null&&row.total==PointsAudit.target(task)){
                latest=row.earned;
                if(previous!=null&&previous==latest&&(!settle||latest==PointsAudit.target(task)||i>=2))return latest;
                previous=latest;
            }else previous=null;
        }
        throw new Exception("未能连续确认“"+taskName(task)+"”的积分，停止该项操作");
    }
    private void runAudited(String task,JSONObject out,boolean extra)throws Exception{
        int before=readTaskPoints(task,false),target=PointsAudit.target(task);audit.begin(task,before);
        out.put("points_before",before).put("points_target",target).put("points_verified",false);
        log(taskName(task)+"执行前积分："+before+"/"+target);Exception actionError=null;
        try{
            if(before>=target){out.put("status","completed").put("skipped_full",true);log("该项积分已满，跳过操作");}
            else switch(task){
                case "articles":runArticles(out,extra?journal.completed(task)+1:6);break;
                case "videos":runVideos(out,extra?journal.completed(task)+1:6);break;
                case "quiz":runQuiz(out);break;
                case "subscriptions":runSubscriptions(out);break;
                case "local":runLocal(out);break;
                default:throw new Exception("未知任务 "+task);
            }
        }catch(Exception error){actionError=error;if(fatal(error)){audit.unknown(task);throw error;}}
        int after;
        try{after=readTaskPoints(task,before<target);}
        catch(Exception error){audit.unknown(task);out.put("points_check_reason",error.getMessage()).put("status","unverified");
            if(fatal(error))throw error;if(actionError!=null)throw actionError;log("任务后积分未确认："+error.getMessage());return;}
        if(after<before){audit.unknown(task);throw new Exception("核验积分小于执行前，未计入收益");}
        audit.finish(task,after);out.put("points_after",after).put("points_gained",after-before).put("points_verified",true);
        log(taskName(task)+"执行后积分："+after+"/"+target+"，本段获得 "+(after-before)+" 分");
        if(after>=target){out.put("status","completed");if(actionError!=null)out.put("action_warning",actionError.getMessage());}
        else if(actionError!=null)throw actionError;
        else if(!out.optString("status").equals("skipped"))out.put("status","partial");
    }
    private void runSupplement(JSONObject out)throws Exception{
        List<String> scope=PointsAudit.scope(order);JSONArray details=new JSONArray();out.put("items",details);
        if(scope.isEmpty()){out.put("status","skipped").put("reason","未勾选普通任务，没有可补分项目");log("未勾选普通任务，跳过补分");return;}
        int extraGained=0;boolean complete=true;
        for(String task:scope){
            checkpoint();JSONObject item=new JSONObject().put("task",task);JSONArray attempts=new JSONArray();item.put("attempts",attempts);details.put(item);
            PointsAudit.Budget budget=new PointsAudit.Budget();int points=-1;
            try{
                points=readTaskPoints(task,false);audit.begin(task,points);audit.finish(task,points);item.put("points_before",points).put("target",PointsAudit.target(task));
                while(budget.canRun(points,PointsAudit.target(task))){
                    if(task.equals("subscriptions")&&journal.subscriptionClicks()>=2){item.put("reason","今日两次订阅点击额度已用完，禁止额外点击");break;}
                    log("补充积分："+taskName(task)+" "+points+"/"+PointsAudit.target(task)+"，补做后重新核验");
                    JSONObject attempt=new JSONObject();attempts.put(attempt);runAudited(task,attempt,true);
                    if(!attempt.optBoolean("points_verified")){item.put("reason","补分后的积分未确认");break;}
                    int after=attempt.getInt("points_after");budget.record(points,after);points=after;
                    if(attempt.optString("status").equals("skipped")){item.put("reason","没有可执行的新内容，跳过剩余补分");break;}
                }
                boolean full=points>=PointsAudit.target(task);item.put("status",full?"completed":"partial");
                if(!full&&!item.has("reason"))item.put("reason",budget.stalled>=PointsAudit.Budget.MAX_STALLED?"连续两次补做积分未增长，停止该项":"已达到本项补做上限");
                complete&=full;
            }catch(Exception error){item.put("status","failed").put("reason",error.getMessage());complete=false;if(fatal(error))throw error;}
            item.put("points_after",audit.verified.contains(task)?audit.after.get(task):JSONObject.NULL).put("actions",attempts.length());
            for(int i=0;i<attempts.length();i++){JSONObject attempt=attempts.getJSONObject(i);if(attempt.optBoolean("points_verified"))extraGained+=attempt.optInt("points_gained");}
            JSONObject original=results.optJSONObject(task);
            if(original!=null){original.put("final_points",audit.after.containsKey(task)?audit.after.get(task):JSONObject.NULL);
                if(audit.verified.contains(task)&&audit.after.get(task)>=PointsAudit.target(task)){
                    original.put("status_before_supplement",original.optString("status")).put("status","completed");service.taskFinished(task,"completed");}}
            log("补分核验："+taskName(task)+" · "+item.optString("status")+(item.has("reason")?" · "+item.getString("reason"):""));saveSummary(false);
        }
        out.put("points_gained",extraGained).put("status",complete?"completed":"partial");
    }
    private Vision.Frame frame(String tag)throws Exception{
        return frame(tag,SystemClock.elapsedRealtime()+12000);
    }
    private Vision.Frame frame(String tag,long deadline)throws Exception{
        long transitionDeadline=Math.min(deadline,SystemClock.elapsedRealtime()+2500);
        while(SystemClock.elapsedRealtime()<deadline){
            checkpoint();AutoService.WindowSnapshot before=service.windowSnapshot();
            if(!APP.equals(before.pkg)){
                if(SystemClock.elapsedRealtime()>=transitionDeadline)throw new ForegroundLost();
                waitMs(Math.min(200,Math.max(0,deadline-SystemClock.elapsedRealtime())));continue;
            }
            Bitmap shot=service.screenshot(Math.max(1,deadline-SystemClock.elapsedRealtime()));
            try{
                List<Vision.Line> text=vision.read(shot,Math.max(1,deadline-SystemClock.elapsedRealtime()));
                AutoService.WindowSnapshot after=service.windowSnapshot();
                Vision.Frame f=new Vision.Frame(shot,text,after.nodes);
                if(ContentRules.sameWindow(before.pkg,before.id,after.pkg,after.id)&&!ContentRules.recents(Vision.labels(f))){
                    if(SystemClock.elapsedRealtime()>=deadline)throw new java.util.concurrent.TimeoutException("页面识别超过等待时限");
                    lastFrame=f;lastFrameTag=tag;return f;
                }
                shot.recycle();if(SystemClock.elapsedRealtime()>=transitionDeadline)throw new ForegroundLost();
            }catch(Exception ex){if(!(ex instanceof java.util.concurrent.TimeoutException)&&!shot.isRecycled())shot.recycle();throw ex;}
        }
        throw new java.util.concurrent.TimeoutException("页面等待超时："+tag);
    }
    private void prepareStartup()throws Exception{
        PageWait gate=new PageWait(SystemClock.elapsedRealtime());long deadline=gate.deadline,lastSkip=0;
        if(!service.targetForeground())service.launchTarget();
        log("等待启动及开屏广告，最长 15 秒；加载期间不执行返回");service.progress("等待学习强国启动 · 最长 15 秒");
        while(SystemClock.elapsedRealtime()<deadline){
            checkpoint();if(!service.targetForeground()){gate.ready(false,SystemClock.elapsedRealtime());waitMs(Math.min(200,deadline-SystemClock.elapsedRealtime()));continue;}
            Vision.Frame f;
            try{f=frame("startup",deadline);}catch(java.util.concurrent.TimeoutException timeout){throw new StartupTimeout();}
            catch(ForegroundLost transition){gate.ready(false,SystemClock.elapsedRealtime());continue;}
            boolean ready=Vision.isHome(f)||isPoints(f)||isQuiz(f)||f.has("待订阅")||f.has("地方平台发布内容")
                    ||ContentRules.loadedDetail(Vision.labels(f));
            PointsEntryLocator.Label skip=ContentRules.skipAd(f.w,f.h,Vision.labels(f));
            if(gate.ready(ready&&skip==null,SystemClock.elapsedRealtime())){log("启动完成，连续两帧确认可操作页面");return;}
            if(!ready||skip!=null){if(skip!=null&&SystemClock.elapsedRealtime()-lastSkip>3000){
                checkpoint();if(!service.targetForeground())continue;
                long remaining=deadline-SystemClock.elapsedRealtime();if(remaining<=0)break;
                log("已识别开屏广告跳过按钮："+skip.text);service.tap(skip.x(),skip.y(),remaining);lastSkip=SystemClock.elapsedRealtime();
            }}
            waitMs(Math.max(0,Math.min(250,deadline-SystemClock.elapsedRealtime())));
        }
        throw new StartupTimeout();
    }
    private void tap(int x,int y)throws Exception{checkpoint();if(!service.targetForeground())throw new ForegroundLost();service.tap(x,y);waitMs(1600);}
    private void tap(Vision.Line line)throws Exception{tap(line.x(),line.y());}
    private void clickControl(Vision.Frame f,PointsEntryLocator.Label target,boolean gestureOnly)throws Exception{
        checkpoint();if(!service.targetForeground())throw new ForegroundLost();
        boolean acted=!gestureOnly&&service.clickControl(target,f.w,f.h);
        log("点击“"+target.text+"”："+(acted?"无障碍控件":"屏幕手势")+" ("+target.x()+", "+target.y()+")，屏幕 "+f.w+"×"+f.h);
        if(acted)waitMs(1600);else tap(target.x(),target.y());
    }
    private void diagnose(Vision.Frame f,String stage){
        StringBuilder text=new StringBuilder();for(Vision.Line line:f.all()){
            if(text.length()>1600)break;
            text.append(line.text.replace('\n',' ')).append('@').append(line.box.toShortString()).append("; ");
        }
        log(stage+"，屏幕 "+f.w+"×"+f.h+"，识别："+text);
    }
    private void swipe(Vision.Frame f,double x1,double y1,double x2,double y2,int duration)throws Exception{
        checkpoint();if(!service.targetForeground())throw new ForegroundLost();
        service.swipe((int)(f.w*x1),(int)(f.h*y1),(int)(f.w*x2),(int)(f.h*y2),duration);waitMs(1200);
    }
    private void back()throws Exception{checkpoint();if(!service.targetForeground())throw new ForegroundLost();service.back();waitMs(1300);}
    private boolean dismissQuizExit(Vision.Frame f)throws Exception{
        boolean context=f.has("退出答题")||f.has("退出本次")||f.has("确定退出")||f.has("确认退出")||f.has("继续答题")||f.has("本次答题");
        if(!context)return false;
        for(String label:new String[]{"退出","确定退出","确认退出","确认","确定"})for(Vision.Line l:f.all())
            if(ScreenRules.norm(l.text).equals(label)&&l.y()>f.h*.25&&l.y()<f.h*.85&&l.box.width()<f.w*.45){
                log("确认退出当前答题，恢复任务导航");tap(l);return true;
            }
        throw new Exception("检测到答题退出弹窗，但无法确认退出按钮");
    }
    private Vision.Frame home()throws Exception{
        if(!service.targetForeground())throw new ForegroundLost();
        Vision.Frame f=frame("home");int backs=0;
        for(int i=0;i<16;i++){
            if(Vision.isHome(f))return f;
            if(ContentRules.splash(Vision.labels(f))||ContentRules.skipAd(f.w,f.h,Vision.labels(f))!=null){prepareStartup();f=frame("home-after-startup");continue;}
            if(dismissQuizExit(f)){f=frame("quiz-exit-confirmed");continue;}
            if(f.lines.size()<4 || f.has("跳过")){waitMs(1000);f=frame("loading");continue;}
            if(backs++>=5)break;
            back();f=frame("back-home");
        }
        throw new Exception("未能回到学习强国首页，请手动打开首页后重试");
    }
    private void runArticles(JSONObject out,int target)throws Exception{
        studyGoal=target;int done=journal.completed("articles");out.put("completed",done).put("target",target).put("points_verified",false);
        if(done>=target){out.put("status","completed");return;}
        Vision.Frame f=home();
        List<Vision.Line> footer=new ArrayList<>();for(Vision.Line l:f.all())if(l.y()>f.h*.80 &&
                (l.text.contains("强国通")||l.text.contains("百灵")||l.text.contains("电视台")||l.text.contains("电台")))footer.add(l);
        if(footer.size()<2)throw new Exception("首页底部导航未确认，停止点击");
        double sum=0;for(Vision.Line l:footer)sum+=l.y()-1.4*l.box.height();int y=(int)(sum/footer.size());
        tap(f.w/2,y);
        boolean channel=false;
        for(int i=0;i<=10;i++){
            f=frame("region-nav");if(!Vision.isHome(f))throw new Exception("寻找地区频道时离开首页");
            Vision.Line tab=Vision.regionTab(f);
            if(tab!=null){tap(tab);f=frame("region-selected");if(!Vision.regionSelected(f))throw new Exception("地区频道未显示红色选中状态");
                channel=true;log("已进入地区频道："+tab.text);break;}
            if(i==10)break;
            int[] band=Vision.navBand(f);double navY=(band[0]+band[1])/2.0/f.h;
            if(band[1]<=band[0])throw new Exception("无法识别频道栏");
            swipe(f,i<5?.72:.18,navY,i<5?.18:.72,navY,500);
        }
        if(!channel)throw new Exception("未找到综合左侧的地区标签");
        int swipes=0,attempts=0,unchanged=0;String previous="";
        while(journal.completed("articles")<target){
            f=frame("article-list");if(!Vision.isHome(f)||!Vision.regionSelected(f))throw new Exception("地区文章列表状态无法确认");
            List<Vision.Article> list=Vision.articles(f);Vision.Article choice=null;
            for(Vision.Article a:list)if(a.unread&&!journal.seen("articles",a.title)){choice=a;break;}
            if(choice!=null){
                if(++attempts>15)throw new Exception("文章打开尝试已达上限");
                readArticle(choice);previous="";unchanged=0;continue;
            }
            StringBuilder signature=new StringBuilder();for(Vision.Article a:list)signature.append(a.title).append('|');
            unchanged=signature.toString().equals(previous)?unchanged+1:0;previous=signature.toString();
            if(unchanged>=3||swipes>=25)throw new Exception("列表不再变化或已达到翻页上限，未找到足够未读文章");
            log("本屏无新的深色文章标题，向下浏览");swipe(f,.46,.83,.46,.39,500);swipes++;
        }
        out.put("completed",journal.completed("articles")).put("status","completed");
        log("文章阅读流程完成，准备核验积分");
    }
    private void readArticle(Vision.Article article)throws Exception{
        journal.record("articles",article.title,"attempted");log("打开文章："+article.title);
        tap(article.box.centerX(),article.box.centerY());
        journal.record("articles",article.title,"reading");
        dwell("articles","阅读");
        Vision.Frame f=frame("article-dwell-end");
        if(Vision.isHome(f))throw new Exception("计时结束时仍在首页列表，未确认进入内容，本篇不计数");
        back();f=frame("article-return");
        if(!Vision.isHome(f)||!Vision.regionSelected(f))throw new Exception("阅读计时完成，但未返回地区列表，本篇暂不计数");
        journal.record("articles",article.title,"completed");
        log("阅读完成："+journal.completed("articles")+"/"+studyGoal);
    }
    private void runVideos(JSONObject out,int target)throws Exception{
        studyGoal=target;int done=journal.completed("videos");out.put("completed",done).put("target",target).put("points_verified",false);
        if(done>=target){out.put("status","completed");return;}
        Vision.Frame f=home();Vision.Line tab=null;
        for(Vision.Line l:f.all())if(l.text.equals("百灵")&&l.y()>f.h*.80){tab=l;break;}
        if(tab==null)throw new Exception("未识别到百灵底部导航，停止点击");tap(tab);
        f=frame("bailing");if(!Vision.bailingSelected(f))throw new Exception("百灵底栏未确认选中");
        int swipes=0,refreshes=0,attempts=0,unchanged=0;String previous="";
        while(journal.completed("videos")<target){
            f=frame("video-list");if(!Vision.bailingSelected(f))throw new Exception("百灵视频列表状态无法确认");
            List<Vision.Video> list=Vision.videos(f);Vision.Video choice=null;
            for(Vision.Video v:list)if(v.eligible&&!journal.seen("videos",v.title)){choice=v;break;}
            if(choice!=null){
                if(++attempts>15)throw new Exception("视频打开尝试已达上限");
                watchVideo(choice);previous="";unchanged=0;continue;
            }
            StringBuilder signature=new StringBuilder();for(Vision.Video v:list)signature.append(v.title).append(v.duration).append('|');
            unchanged=signature.toString().equals(previous)?unchanged+1:0;previous=signature.toString();
            if(unchanged>=3||swipes>=30){
                if(refreshes++>=3)throw new Exception("视频列表刷新上限已到，未找到足够长视频");
                log("视频列表已浏览，返回顶部刷新（"+refreshes+"/3）");refreshVideos(f);swipes=unchanged=0;previous="";continue;
            }
            log("本屏无新的深色且至少 90 秒的视频，向下浏览（浅灰标题和历史重复已过滤）");swipe(f,.5,.83,.5,.38,500);swipes++;
        }
        out.put("completed",journal.completed("videos")).put("status","completed");
        log("视频计时流程完成，准备核验积分");
    }
    private void watchVideo(Vision.Video video)throws Exception{
        journal.record("videos",video.title,"attempted");log("打开视频："+video.title+"（"+video.duration+" 秒）");
        tap(video.x,video.y);
        journal.record("videos",video.title,"playing");
        dwell("videos","视听");
        Vision.Frame f=frame("video-dwell-end");
        if(Vision.isHome(f))throw new Exception("计时结束时仍在首页列表，未确认进入内容，本视频不计数");
        back();f=frame("video-return");if(!Vision.bailingSelected(f))throw new Exception("计时结束后未返回百灵列表，本视频暂不计数");
        journal.record("videos",video.title,"completed");log("视听完成："+journal.completed("videos")+"/"+studyGoal);
    }
    private void dwell(String task,String label)throws Exception{
        log(label+"：点击完成，直接按本机时钟计时 60 秒；期间不核验标题、正文或播放进度");
        StudyTimer timer=new StudyTimer(SystemClock.elapsedRealtime());long lastTick=SystemClock.elapsedRealtime();int version=pauseVersion;
        while(true){
            checkpoint();boolean leftApp=!service.targetForeground();if(leftApp)waitForTargetDuringTimer();
            long now=SystemClock.elapsedRealtime();
            if(leftApp||version!=pauseVersion){timer.reset(now);lastTick=now;version=pauseVersion;log(label+"：返回或继续后重新计时 60 秒");}
            if(now-lastTick>40000)throw new Exception("检测到长时间休眠或卡顿，本次不计数");lastTick=now;
            progress(label,journal.completed(task),studyGoal,timer.remaining(now));
            if(timer.complete(now))return;waitMs(500);
        }
    }
    private void refreshVideos(Vision.Frame f)throws Exception{
        Vision.Line tab=null;for(Vision.Line l:f.all())if(l.text.equals("百灵")&&l.y()>f.h*.80){tab=l;break;}
        if(tab==null)throw new Exception("刷新时找不到百灵底栏");tap(tab);
        f=frame("video-top");if(!Vision.bailingSelected(f))throw new Exception("未返回百灵列表顶部");
        swipe(f,.5,.35,.5,.78,600);f=frame("video-refreshed");if(!Vision.bailingSelected(f))throw new Exception("刷新后百灵状态无法确认");
    }

    private static final class Row {
        Vision.Line heading,button;Integer earned,total;
        Row(Vision.Line heading,Vision.Line button,Integer earned,Integer total){
            this.heading=heading;this.button=button;this.earned=earned;this.total=total;
        }
    }
    private boolean isPoints(Vision.Frame f){return ScreenRules.points(Vision.labels(f));}
    private boolean isQuiz(Vision.Frame f){return quizKind(f)!=null;}
    private Vision.Frame points()throws Exception{
        if(!service.targetForeground())throw new ForegroundLost();
        Vision.Frame f=frame("points-check");if(isPoints(f))return f;
        f=home();
        for(int attempt=0;attempt<2;attempt++){
            PointsEntryLocator.Label score=vision.pointsEntry(f);
            if(score==null)throw new Exception("首页积分入口无法可靠识别，已停止，避免误点 AI");
            if(!ScreenRules.norm(score.text).matches(".*[0-9].*")){
                checkpoint();if(!service.targetForeground())throw new Exception("应用不在前台，禁止点击");
                if(attempt>0||!service.clickControl(score,f.w,f.h))throw new Exception("仅识别到积分小字，未找到数字区域或可点击控件，停止盲点");
                log("积分入口：点击匹配到的无障碍控件");waitMs(1600);
            }else clickControl(f,score,attempt>0);
            for(int i=0;i<6;i++){f=frame("points-open");if(isPoints(f))return f;waitMs(1000);}
            if(!Vision.isHome(f))throw new Exception("点击积分后进入了非预期页面，停止重试");
            if(attempt==0)log("仍在首页，重新识别积分数字区域，补一次手势点击");
        }
        throw new Exception("两次积分入口点击后仍在首页，未能打开积分中心");
    }
    private Row rowOnScreen(Vision.Frame f,String label)throws Exception{
        Vision.Line heading=null;
        for(Vision.Line l:f.all())if(Vision.norm(l.text).equals(Vision.norm(label)) && l.y()>f.h*.23 && l.y()<f.h*.93){heading=l;break;}
        if(heading==null)return null;
        Vision.Line button=null;for(Vision.Line l:f.all())if(l.x()>f.w*.70 && Math.abs(l.y()-heading.y())<f.h*.095
                && (l.text.contains("去答题")||l.text.contains("去看看")||l.text.contains("已完成")))
            if(button==null||Math.abs(l.y()-heading.y())<Math.abs(button.y()-heading.y()))button=l;
        Integer earned=null,total=null;
        int expected=0,bottom=Math.min((int)(f.h*.98),(int)(heading.y()+f.h*.20));
        for(String id:new String[]{"articles","videos","quiz","subscriptions","local"})if(taskName(id).equals(label))expected=PointsAudit.target(id);
        for(Vision.Line l:f.all())if(l.y()>heading.y()+f.h*.03)for(String title:new String[]{"登录","我要选读文章","我要视听学习","每日答题","趣味答题","订阅","发表观点","本地频道","强国运动"})
            if(ScreenRules.norm(l.text).equals(title))bottom=Math.min(bottom,l.box.top);
        for(int pass=0;pass<2&&earned==null;pass++){
            if(pass==1&&bottom>heading.y()+f.h*.035)vision.enhance(f,.70,heading.y()/(double)f.h+.025,.99,bottom/(double)f.h);
            for(PointsEntryLocator.Label l:Vision.labels(f))if(l.x()>f.w*.67&&l.y()>heading.y()&&l.y()<bottom){
                Integer value=PointsAudit.parse(l.text,expected);if(value!=null){earned=value;total=expected;break;}
            }
        }
        return new Row(heading,button,earned,total);
    }
    private Row seekRow(String label)throws Exception{
        Vision.Frame f=points();
        ScreenRules.Scan scan=new ScreenRules.Scan();boolean returningTop=false,adjusted=false;
        while(true){
            if(!isPoints(f))throw new Exception("查找任务时离开积分中心，停止滚动");
            Row row=rowOnScreen(f,label);
            List<PointsEntryLocator.Label> labels=Vision.labels(f);boolean top=ScreenRules.pointsTop(labels);
            if(!returningTop&&!adjusted&&row!=null&&row.earned==null&&row.heading.y()>f.h*.65&&!ScreenRules.pointsBottom(labels)){
                adjusted=true;swipe(f,.5,.78,.5,.50,450);f=frame("points-row-reveal");continue;
            }
            if((!returningTop||top)&&row!=null&&row.button!=null){log("积分中心："+label+" · "+row.button.text+" · "+row.earned+"/"+row.total);return row;}
            String direction=scan.next(ScreenRules.pointsBottom(labels),top,ScreenRules.signature(f.h,labels));
            if(direction.equals("stop"))break;
            if(direction.equals("up")&&!returningTop){returningTop=true;log("已到强国运动或列表底部，停止向下浏览，回到积分页顶部");}
            // Finish the reset before rescanning, even if a task passes through the viewport.
            swipe(f,.5,direction.equals("up")?.30:.80,.5,direction.equals("up")?.82:.32,450);f=frame("points-scan");
            if(returningTop&&ScreenRules.pointsTop(Vision.labels(f))){
                // One bounded downward pass from the top may be needed for a middle task.
                return seekRowFromTop(f,label);
            }
        }
        throw new Exception("积分中心未找到“"+label+"”的操作按钮");
    }
    private Row seekRowFromTop(Vision.Frame f,String label)throws Exception{
        String previous="";int unchanged=0;
        for(int i=0;i<9;i++){
            if(!isPoints(f))throw new Exception("已离开积分中心，停止滚动");
            Row row=rowOnScreen(f,label);if(row!=null&&row.button!=null){log("积分中心："+label+" · "+row.button.text+" · "+row.earned+"/"+row.total);return row;}
            List<PointsEntryLocator.Label> labels=Vision.labels(f);
            String signature=ScreenRules.signature(f.h,labels);unchanged=signature.equals(previous)?unchanged+1:0;previous=signature;
            if(ScreenRules.pointsBottom(labels)||unchanged>=2)break;
            swipe(f,.5,.80,.5,.32,450);f=frame("points-rescan");
        }
        // Always leave a failed scan at the top instead of repeatedly pulling the bottom.
        for(int i=0;i<10&&!ScreenRules.pointsTop(Vision.labels(f));i++){
            if(!isPoints(f))break;
            String before=ScreenRules.signature(f.h,Vision.labels(f));swipe(f,.5,.30,.5,.82,450);f=frame("points-reset-top");
            if(before.equals(ScreenRules.signature(f.h,Vision.labels(f))))break;
        }
        throw new Exception("积分页已从顶部检查至运动板块，未找到“"+label+"”，停止本项");
    }
    private Vision.Frame returnPoints()throws Exception{
        for(int i=0;i<5;i++){
            Vision.Frame f=frame("return-points");if(isPoints(f))return f;
            if(Vision.isHome(f))return points();
            if(dismissQuizExit(f))continue;
            back();
        }
        throw new Exception("未能返回积分中心");
    }
    private void runLocal(JSONObject out)throws Exception{
        Row row=seekRow("本地频道");
        if(row.button.text.contains("已完成")||(row.earned!=null&&row.earned>=1)){
            out.put("status","completed").put("points",row.earned);return;
        }
        tap(row.button);Vision.Frame f=frame("local-entry");Vision.Line label=null;
        for(Vision.Line l:f.all())if(l.text.contains("学习平台")&&l.x()>f.w*.02&&l.x()<f.w*.35
                && l.y()>f.h*.17&&l.y()<f.h*.45)
            if(label==null||l.y()<label.y())label=l;
        if(label==null)throw new Exception("本地频道未找到左上角的学习平台入口");
        String name=label.text;log("打开本地学习平台："+name);tap(label.x(),(int)(label.y()-f.h*.065));
        f=frame("platform-open");boolean title=false;
        for(Vision.Line l:f.all())if(Vision.norm(l.text).contains(Vision.norm(name))){title=true;break;}
        if(!title)throw new Exception("未能确认学习平台页面标题");
        returnPoints();row=seekRow("本地频道");out.put("points",row.earned);
        if(row.earned==null||row.earned<1)throw new Exception("已打开本地频道，但积分中心尚未确认 1/1");
        out.put("status","completed");
    }
    private static final class Pending {
        final String name;final Vision.Line button;
        Pending(String name,Vision.Line button){this.name=name;this.button=button;}
    }
    private Vision.Line pendingTab()throws Exception{
        for(int i=0;i<10;i++){
            Vision.Frame f=frame("subscription-entry");
            for(Vision.Line l:f.all())if(Vision.norm(l.text).equals("待订阅")&&l.x()<f.w*.3)return l;
            waitMs(1000);
        }
        throw new Exception("订阅页面未找到“待订阅”，禁止盲点");
    }
    private List<Pending> pendingRows(Vision.Frame f){
        List<Pending> rows=new ArrayList<>();
        for(Vision.Line button:f.all())if(Vision.norm(button.text).equals("订阅")&&button.x()>f.w*.78
                &&button.y()>f.h*.07&&button.y()<f.h*.95){
            Vision.Line name=null;
            for(Vision.Line l:f.all())if(l.x()>f.w*.30&&l.x()<f.w*.76
                    &&l.y()>button.y()-f.h*.062&&l.y()<button.y()+f.h*.035&&Vision.norm(l.text).length()>=2)
                if(name==null||l.y()<name.y())name=l;
            if(name!=null)rows.add(new Pending(name.text,button));
        }
        rows.sort(Comparator.comparingInt(r->r.button.y()));return rows;
    }
    private void runSubscriptions(JSONObject out)throws Exception{
        int clicks=journal.subscriptionClicks();out.put("clicks_today",clicks).put("target",2);
        Row row=seekRow("订阅");
        if(row.earned!=null&&row.earned>=2){out.put("points",row.earned).put("status","completed");return;}
        if(clicks>=2){out.put("points",row.earned).put("status","unverified");log("今日已记录两次订阅点击，不再操作");return;}
        if(row.earned==null)throw new Exception("无法确认订阅当日积分，禁止点击");
        int allowed=Math.min(2-clicks,2-row.earned);
        if(allowed<=0){out.put("status","completed");return;}
        tap(row.button);tap(pendingTab());boolean noNew=false;
        for(int n=0;n<allowed;n++){
            List<Pending> rows=new ArrayList<>();
            for(int retry=0;retry<8;retry++){
                Vision.Frame f=frame("pending-rows");rows=pendingRows(f);if(!rows.isEmpty())break;waitMs(1000);
            }
            Pending choice=null;for(Pending candidate:rows)if(!journal.subscribedBefore(candidate.name)){choice=candidate;break;}
            if(choice==null){noNew=true;log("待订阅列表没有新的可识别账号，跳过剩余次数");break;}
            journal.reserveSubscription(choice.name);
            log("已永久预留今日第 "+journal.subscriptionClicks()+"/2 次点击："+choice.name);
            tap(choice.button);journal.markSubscription(choice.name,"clicked");
            boolean returned=false;for(int i=0;i<8;i++){Vision.Frame f=frame("after-subscribe");if(f.has("待订阅")){returned=true;break;}waitMs(1000);}
            if(!returned)throw new Exception("订阅后页面未加载；该点击已计入上限，禁止重试");
        }
        returnPoints();row=seekRow("订阅");out.put("points",row.earned).put("clicks_today",journal.subscriptionClicks());
        if(row.earned!=null&&row.earned>=2)out.put("status","completed");
        else out.put("status",noNew?"skipped":"unverified");
    }

    private String quizKind(Vision.Frame f){
        for(Vision.Line l:f.all()){String kind=ScreenRules.kind(l.text);if(kind!=null)return kind;}
        return questionNumber(f)>0?"未知题型":null;
    }
    private int questionNumber(Vision.Frame f){
        return ScreenRules.questionNumber(f.w,f.h,Vision.labels(f));
    }
    private Vision.Frame readyQuestion(Vision.Frame f)throws Exception{
        for(int i=0;i<6;i++){
            if(quizKind(f)!=null&&questionNumber(f)>0)return f;
            if(i==1||i==4){vision.enhance(f,.04,.07,.97,.42);if(quizKind(f)!=null&&questionNumber(f)>0)return f;}
            if(f.has("网络开小差"))throw new Exception("答题页显示网络开小差");
            waitMs(1000);f=frame("quiz-ready");
        }
        throw new Exception("等待后仍无法确认题型或题号，已记录屏幕文字框");
    }
    private Vision.Frame openQuiz()throws Exception{
        Vision.Frame f=frame("quiz-entry-check");if(f.has("网络开小差"))f=recoverNetwork(f);
        if(isQuiz(f)&&hintHeading(f)!=null){
            // A previous failed batch deliberately left this dialog open. Reopen it later with a clean baseline.
            log("发现上次保留的提示弹窗，先关闭以重新采集题目与提示文字");back();
            f=frame("quiz-resume-from-hint");
            if(hintHeading(f)!=null||!isQuiz(f))throw new QuizPageFailure("关闭上次提示后未确认答题页");
        }
        if(isQuiz(f))return readyQuestion(f);
        Row row=seekRow("每日答题");
        if(row.button.text.contains("已完成")||(row.earned!=null&&row.earned>=5))return null;
        tap(row.button);
        for(int i=0;i<8;i++){f=frame("quiz-open");if(isQuiz(f))return readyQuestion(f);waitMs(1000);}
        throw new Exception("每日答题页面未加载");
    }
    private Vision.Frame recoverNetwork(Vision.Frame f)throws Exception{
        for(int retry=0;retry<2;retry++){
            Vision.Line button=f.find("重试");if(button==null)return f;
            log("答题显示网络开小差，重试 "+(retry+1)+"/2");tap(button);
            for(int i=0;i<35;i++){
                f=frame("network-retry");if(f.has("网络开小差"))break;
                if(isPoints(f)||(!isQuiz(f)&&f.lines.size()>3))return f;
                waitMs(1000);
            }
        }
        throw new Exception("答题网络重试两次仍失败");
    }
    private void checkQuestion(Vision.Frame f,int original)throws Exception{
        int number=questionNumber(f);
        if(number>0&&number!=original||Vision.isHome(f)||isPoints(f)||f.has("退出答题")||f.has("网络开小差"))
            throw new QuizPageFailure("滚动期间题目或页面发生变化");
    }
    private String quizSignature(Vision.Frame f){return QuizDocument.signature(f.w,f.h,Vision.labels(f));}
    private Vision.Frame quizTop(Vision.Frame f,int original)throws Exception{
        QuizDocument.Scan scan=new QuizDocument.Scan();
        while(true){
            checkQuestion(f,original);
            if(scan.observe(quizSignature(f))){
                if(questionNumber(f)!=original)throw new QuizPageFailure("回顶后未确认原题号");
                return f;
            }
            if(scan.exhausted())throw new QuizPageFailure("答题回顶达到滚动上限");
            swipe(f,.50,.33,.50,.78,480);f=frame("quiz-scroll-top");
        }
    }
    private static final class QuestionScan {
        final QuizDocument document;final Vision.Frame bottom;final String stem;
        QuestionScan(QuizDocument document,Vision.Frame bottom,String stem){this.document=document;this.bottom=bottom;this.stem=stem;}
    }
    private QuestionScan collectOptions(Vision.Frame f,int original)throws Exception{
        QuizDocument document=new QuizDocument();QuizDocument.Scan scan=new QuizDocument.Scan();int page=0;String stem="";
        while(true){
            checkQuestion(f,original);
            if(document.options.isEmpty()){
                String part=QuizHintText.stem(f.h,Vision.labels(f)),merged=QuizDocument.merge(stem,part);
                if(merged!=null)stem=merged;
            }
            document.add(f.w,f.h,Vision.labels(f));
            log("收集第 "+(++page)+" 屏选项："+String.join("、",document.options.keySet()));
            if(scan.observe(quizSignature(f))){
                document.atBottom();
                if(!document.complete())throw new QuizAnswerUnavailable(document.problem());
                log("完整选项已收集："+document.options.size()+" 项");return new QuestionScan(document,f,stem);
            }
            if(scan.exhausted())throw new QuizAnswerUnavailable("题目超过 18 屏扫描上限，未确认完整选项");
            swipe(f,.50,.80,.50,.42,480);f=frame("quiz-collect-options");
        }
    }
    private Vision.Frame seekHint(Vision.Frame f,int original,boolean upward)throws Exception{
        QuizDocument.Scan scan=new QuizDocument.Scan();
        while(true){
            checkQuestion(f,original);
            if(ScreenRules.hintButton(f.w,f.h,Vision.labels(f))!=null)return f;
            if(scan.observe(quizSignature(f))||scan.exhausted()){
                vision.enhance(f,.50,.18,.98,.95);
                if(ScreenRules.hintButton(f.w,f.h,Vision.labels(f))!=null)return f;
                throw new QuizAnswerUnavailable("滚动查找后仍未发现查看提示按钮");
            }
            log("当前屏未见提示，"+(upward?"向上":"向下")+"滚动查找");
            swipe(f,.50,upward?.33:.80,.50,upward?.78:.42,480);f=frame("quiz-find-hint");
        }
    }
    private void selectOptions(Vision.Frame f,int original,QuizDocument document,List<String> chosen)throws Exception{
        f=quizTop(f,original);QuizDocument.Selection selection=new QuizDocument.Selection(document,chosen);QuizDocument.Scan scan=new QuizDocument.Scan();
        while(true){
            checkQuestion(f,original);PointsEntryLocator.Label target;
            try{target=selection.next(f.w,f.h,Vision.labels(f));}catch(IllegalStateException changed){throw new QuizPageFailure(changed.getMessage());}
            if(target!=null){
                // Reacquire a fresh frame after EVERY tap. Scrolling never reuses old coordinates.
                String letter=QuizDocument.letter(target.text);
                tap(target.x(),target.y());selection.clicked(letter);log("选择 "+letter+"（"+(chosen.size()-selection.left())+"/"+chosen.size()+"）");
                f=frame("quiz-option-selected");continue;
            }
            if(selection.complete())break;
            if(scan.observe(quizSignature(f))||scan.exhausted())throw new QuizPageFailure("尚未定位全部待选项："+String.join("、",chosen));
            swipe(f,.50,.80,.50,.42,480);f=frame("quiz-locate-option");
        }
        quizTop(f,original); // Restore the question number and submit control before submitting.
    }
    private QuizHintText.Result recognizeHint(Vision.Frame popup,Vision.Line heading,List<PointsEntryLocator.Label> background,
                                            String stem,QuizDocument document,boolean multiple)throws Exception{
        QuizHintText.Result text=QuizHintText.resolve(stem,document,multiple,popup.h,heading.box.bottom,background,Vision.labels(popup));
        log("提示正文识别："+text.characters+" 字；"+text.method);
        return text;
    }
    private List<String> hint(Vision.Frame f,int original,String stem,QuizDocument document,boolean multiple)throws Exception{
        String originalSurface=quizSignature(f);List<PointsEntryLocator.Label> background=Vision.labels(f);Vision.Frame popup=f;Vision.Line heading=null;
        for(int attempt=0;attempt<2&&heading==null;attempt++){
            PointsEntryLocator.Label button=ScreenRules.hintButton(f.w,f.h,Vision.labels(f));
            if(button==null){vision.enhance(f,.50,.25,.98,.95);button=ScreenRules.hintButton(f.w,f.h,Vision.labels(f));}
            if(button==null)throw new Exception("未识别到查看提示按钮，已停止盲点");
            clickControl(f,button,attempt>0);
            for(int i=0;i<6;i++){
                popup=frame("quiz-hint");heading=hintHeading(popup);if(heading!=null)break;waitMs(800);
            }
            if(heading==null){
                checkQuestion(popup,original);
                if(ScreenRules.hintButton(popup.w,popup.h,Vision.labels(popup))==null)
                    throw new Exception("点击提示后页面状态改变，停止重复点击");
                f=popup;if(attempt==0)log("仍在同一道题，重新定位提示按钮后补一次手势点击");
            }
        }
        if(heading==null)throw new Exception("两次点击后未看到提示弹窗");
        QuizHintText.Result recognized=recognizeHint(popup,heading,background,stem,document,multiple);
        // The sheet can open before its text finishes rendering. Retry fresh frames.
        for(int retry=0;recognized.answers.isEmpty()&&retry<2;retry++){
            waitMs(800);Vision.Frame fresh=frame("hint-text-ready");Vision.Line freshHeading=hintHeading(fresh);
            if(freshHeading==null)throw new Exception("识别答案期间提示弹窗已关闭");
            popup=fresh;heading=freshHeading;recognized=recognizeHint(popup,heading,background,stem,document,multiple);
        }
        List<String> answers=recognized.answers;
        log("识别方式："+recognized.method);
        if(answers.isEmpty()){
            diagnose(popup,"提示答案未确认");
            log("无法确定答案，关闭提示后按设置选择 A，继续本组答题");
        }
        log("提示答案："+(answers.isEmpty()?"无":String.join(" / ",answers)));
        Vision.Line close=null;for(Vision.Line l:popup.all())if(l.x()>popup.w*.80 && Math.abs(l.y()-heading.y())<popup.h*.05
                &&(l.text.equalsIgnoreCase("x")||l.text.equals("×")||l.text.equals("关闭")||l.text.equals("关闭提示"))){close=l;break;}
        if(close!=null)tap(close);else back();
        boolean closed=false;for(int i=0;i<5;i++){
            Vision.Frame after=frame("hint-closed");
            checkQuestion(after,original);
            if(hintHeading(after)==null&&(questionNumber(after)==original||originalSurface.equals(quizSignature(after)))){closed=true;break;}
            waitMs(800);
        }
        if(!closed)throw new Exception("关闭提示后未确认返回原题，停止作答");
        return answers;
    }
    private Vision.Line hintHeading(Vision.Frame f){
        for(Vision.Line l:f.all())if(ScreenRules.norm(l.text).equals("提示")&&l.x()>f.w*.25&&l.x()<f.w*.75
                &&l.y()>f.h*.12&&l.y()<f.h*.85&&l.box.width()<f.w*.30)return l;
        return null;
    }
    private boolean answerQuestion(Vision.Frame f)throws Exception{
        f=readyQuestion(f);
        String kind=quizKind(f);int number=questionNumber(f);
        if(kind==null||number==0)throw new Exception("无法确认题型或题号");
        log("处理第 "+number+"/5 题："+kind);
        if(kind.equals("未知题型"))throw new QuizAnswerUnavailable("未识别的题型");
        f=quizTop(f,number);QuizDocument document=null;String stemText=QuizHintText.stem(f.h,Vision.labels(f));
        boolean choice=kind.equals("单选题")||kind.equals("多选题");
        if(choice){QuestionScan scan=collectOptions(f,number);document=scan.document;f=scan.bottom;stemText=scan.stem;}
        if(QuizHintText.isJudgment(document))log("已识别判断题：按正确/错误选项文字匹配，不固定选项字母");
        f=seekHint(f,number,choice);
        List<String> hints=hint(f,number,stemText,document,kind.equals("多选题"));if(hints.isEmpty())return false;
        f=frame("answer-ready");StringBuilder joined=new StringBuilder();for(String part:hints)joined.append(Vision.norm(part));
        String answer=joined.toString();
        if(choice){
            List<String> chosen=document.choose(hints,kind.equals("多选题"));
            if(kind.equals("单选题")?chosen.size()!=1:chosen.size()<2){log("提示与选项无法可靠匹配，将选择 A");return false;}
            log("本题待选："+String.join("、",chosen));selectOptions(f,number,document,chosen);
        }else if(kind.equals("点选填空题")){
            f=quizTop(f,number);
            List<Vision.Line> chips=new ArrayList<>();for(Vision.Line l:f.all()){
                String value=Vision.norm(l.text);if(l.y()>f.h*.32&&l.y()<f.h*.66&&value.length()>=1&&value.length()<=8&&answer.contains(value))chips.add(l);
            }
            List<Vision.Line> sequence=new ArrayList<>();if(!matchChips(answer,chips,new boolean[chips.size()],sequence)){
                log("点选填空词块无法与提示完整匹配");return false;
            }
            for(Vision.Line chip:sequence){
                Vision.Frame fresh=frame("fill-chip");checkQuestion(fresh,number);Vision.Line target=null;
                for(Vision.Line l:fresh.all())if(Vision.norm(l.text).equals(Vision.norm(chip.text))&&l.y()>fresh.h*.30&&l.y()<fresh.h*.85){target=l;break;}
                if(target==null)throw new QuizPageFailure("点选填空词块位置变化，未定位剩余词块");tap(target);
            }
        }else{
            f=quizTop(f,number);
            fillInputs(f,hints);
        }
        submit(number);return true;
    }
    private boolean matchChips(String remaining,List<Vision.Line> chips,boolean[] used,List<Vision.Line> selected){
        if(remaining.isEmpty())return true;
        if(selected.size()>=8)return false;
        for(int i=0;i<chips.size();i++)if(!used[i]){
            String value=Vision.norm(chips.get(i).text);
            if(remaining.startsWith(value)){
                used[i]=true;selected.add(chips.get(i));
                if(matchChips(remaining.substring(value.length()),chips,used,selected))return true;
                selected.remove(selected.size()-1);used[i]=false;
            }
        }
        return false;
    }
    private void fillInputs(Vision.Frame f,List<String> hints)throws Exception{
        List<String> fragments=new ArrayList<>();for(String hint:hints){String value=QuizHintText.inputValue(hint);if(!value.isEmpty())fragments.add(value);}
        if(fragments.isEmpty())throw new Exception("输入填空没有可用答案");
        List<AccessibilityNodeInfo> fields=new ArrayList<>();
        for(int i=0;i<3;i++){fields=service.inputFields((int)(f.h*.23),(int)(f.h*.80));if(!fields.isEmpty())break;waitMs(1000);}
        List<String> answers=new ArrayList<>();if(fields.size()==1)answers.add(String.join("",fragments));else answers.addAll(fragments);
        if(fields.size()!=answers.size())throw new QuizAnswerUnavailable("输入框与提示片段数量不一致");
        for(int i=0;i<fields.size();i++){
            AccessibilityNodeInfo field=fields.get(i);Rect box=new Rect();field.getBoundsInScreen(box);tap(box.centerX(),box.centerY());
            if(!service.setInput(field,answers.get(i)))throw new Exception("无法通过无障碍设置中文答案");
            waitMs(500);
            List<AccessibilityNodeInfo> actual=service.inputFields((int)(f.h*.23),(int)(f.h*.80));
            boolean verified=false;for(AccessibilityNodeInfo item:actual)if(item.getText()!=null&&answers.get(i).equals(item.getText().toString()))verified=true;
            if(!verified)throw new Exception("中文答案未能在输入框中核实");
        }
        log("填空已写入并核对："+String.join(" / ",answers));
    }
    private void submit(int previous)throws Exception{
        Vision.Frame f=frame("before-submit");
        int retries=0;String lastAction="";
        for(int i=0;i<55;i++){
            int current=questionNumber(f);
            if(current>0&&current!=previous)return;
            if(QuizControls.result(Vision.labels(f)))return;
            if(f.has("网络开小差")&&f.has("重试")){
                if(retries++>=2)throw new Exception("答题提交网络重试两次仍失败");
                log("答题提交遇到网络问题，重试 "+retries+"/2");tap(f.find("重试"));lastAction="";
            }else{
                PointsEntryLocator.Label button=QuizControls.button(f.w,f.h,Vision.labels(f),"下一题","完成","确定","提交");
                if(button!=null&&!lastAction.equals(ScreenRules.norm(button.text))){
                    clickControl(f,button,false);lastAction=ScreenRules.norm(button.text);log("答题操作："+lastAction);
                }
            }
            waitMs(1000);f=frame("after-submit");
        }
        throw new Exception("提交后题号未推进");
    }
    private boolean fallbackAnswer(Vision.Frame f,int number,JSONObject out)throws Exception{
        f=quizTop(f,number);QuizDocument.Scan scan=new QuizDocument.Scan();
        while(true){
            checkQuestion(f,number);
            PointsEntryLocator.Label a=QuizControls.optionA(f.w,f.h,Vision.labels(f));
            if(a!=null){
                log("无法判断答案，按设置选择 A（不代表正确答案）");tap(a.x(),a.y());
                out.put("default_a",out.optInt("default_a")+1);
                quizTop(frame("fallback-a-selected"),number);submit(number);return true;
            }
            List<AccessibilityNodeInfo> fields=service.inputFields((int)(f.h*.18),(int)(f.h*.92));
            if(!fields.isEmpty()){
                boolean written=true;
                for(AccessibilityNodeInfo field:fields){
                    checkpoint();if(!service.targetForeground())throw new ForegroundLost();
                    if(!service.setInput(field,"学习")){written=false;break;}
                    waitMs(300);field.refresh();
                    if(field.getText()==null||!"学习".contentEquals(field.getText())){written=false;break;}
                }
                if(written){
                    log("无法匹配填空答案，按设置输入占位答案：学习");
                    out.put("placeholder_answers",out.optInt("placeholder_answers")+1);
                    quizTop(frame("fallback-input"),number);submit(number);return true;
                }
                log("已找到输入框，但未能写入并核实占位答案");return false;
            }
            if(scan.observe(quizSignature(f))||scan.exhausted())return false;
            swipe(f,.50,.80,.50,.42,480);f=frame("fallback-find-answer-control");
        }
    }
    private Vision.Frame checkQuizPoints(JSONObject out)throws Exception{
        returnPoints();Row row=seekRow("每日答题");
        for(int retry=0;retry<2&&(row.earned==null||row.earned<5);retry++){waitMs(1500);row=seekRow("每日答题");}
        if(row.earned!=null)out.put("correct",row.earned);
        if(row.earned!=null&&row.earned>=5)return null;
        if(row.earned==null)throw new Exception("无法读取每日答题积分，停止重复答题");
        log("积分中心确认 "+row.earned+"/5，继续下一组答题");
        return openQuiz();
    }
    private void runQuiz(JSONObject out)throws Exception{
        out.put("correct",0).put("target",5).put("refreshes",0).put("refresh_limit",5).put("rounds",0).put("submitted",0).put("default_a",0).put("placeholder_answers",0);
        Vision.Frame f=openQuiz();if(f==null){out.put("correct",5).put("status","completed");return;}
        while(true){
            checkpoint();
            if(f==null){out.put("correct",5).put("status","completed");return;}
            if(QuizControls.result(Vision.labels(f))){
                out.put("rounds",out.optInt("rounds")+1);log("本组答题完成，返回积分中心核验");
                f=checkQuizPoints(out);continue;
            }
            f=readyQuestion(f);int number=questionNumber(f);boolean answered;
            try{answered=answerQuestion(f);}catch(QuizAnswerUnavailable unknown){
                log("本题无法可靠判断："+unknown.getMessage()+"；尝试选择 A 或填写占位答案");answered=false;
            }
            if(!answered)answered=fallbackAnswer(frame("quiz-fallback"),number,out);
            if(!answered){
                if(out.optInt("refreshes")>=5)throw new Exception("既无可定位的 A 选项，也无法输入；已达到 5 次无法作答刷新上限");
                out.put("refreshes",out.optInt("refreshes")+1);
                log("本题没有可操作的 A 或输入框，仅此情况退出重进（"+out.optInt("refreshes")+"/5）");
                f=checkQuizPoints(out);
            }else{
                out.put("submitted",out.optInt("submitted")+1);f=frame("next-question");
            }
        }
    }
}
