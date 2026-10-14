package io.github.autotaskrun;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import org.json.JSONObject;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static WeakReference<MainActivity> visible=new WeakReference<>(null);
    private static final int BG=0xFFF4F6F5,WHITE=Color.WHITE,INK=0xFF193630,MUTED=0xFF75867F,ACCENT=0xFF21846B,SOFT=0xFFE9F4EF,LINE=0xFFE2E9E5;
    private static final String[] DEFAULTS={"articles","videos","quiz","subscriptions","local","supplement"};
    private final List<String> order=new ArrayList<>();
    private final Map<String,Boolean> selected=new LinkedHashMap<>();
    private SharedPreferences prefs;
    private LinearLayout cards,setupBanner,logPage;
    private ScrollView taskPage,logScroll;
    private TextView connection,heroTitle,heroCaption,heroCount,selectionLabel,log,dockSummary,logStatus,taskTab,logTab;
    private TextView setupTitle,setupCaption,setupAction;
    private ProgressBar progressBar;
    private TextView resultSummary;
    private String summaryDetails="";
    private int summaryGain;
    private boolean summaryVerified;
    private Button primary,stop;
    private String page="tasks",cardState="",latestProgress="";
    private boolean emptyLog=true;
    private boolean permissionGuideChecked;

    @Override public void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("ui_v1",MODE_PRIVATE);loadOrder();reloadSummary();if(state!=null){page=state.getString("page","tasks");permissionGuideChecked=state.getBoolean("permission_guide_checked",true);}build();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("page",page);out.putBoolean("permission_guide_checked",permissionGuideChecked);super.onSaveInstanceState(out);}
    @Override protected void onResume(){super.onResume();visible=new WeakReference<>(this);reloadLog();reloadSummary();updateState();checkPermissionGuide();}
    @Override protected void onPause(){if(visible.get()==this)visible.clear();super.onPause();}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable shape(int color,int radius){return shape(color,radius,0);}
    private GradientDrawable shape(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    private LinearLayout.LayoutParams lp(int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));}
    private void space(LinearLayout host,int height){host.addView(new View(this),lp(1,height));}
    private Button button(String title,int color,int foreground){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(foreground);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(14),0,dp(14),0);b.setBackground(shape(color,18));return b;}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private boolean running(){AutoService s=AutoService.instance;return s!=null&&s.running();}
    private int selectionCount(){int n=0;for(boolean checked:selected.values())if(checked)n++;return n;}
    private void loadOrder(){for(String id:prefs.getString("order","").split(","))if(valid(id)&&!order.contains(id))order.add(id);for(String id:DEFAULTS)if(!order.contains(id))order.add(id);TaskOrder.pinSupplement(order);for(String id:order)selected.put(id,prefs.getBoolean("task_"+id,!id.equals("supplement")));}
    private boolean valid(String id){for(String known:DEFAULTS)if(known.equals(id))return true;return false;}
    private String name(String id){switch(id){case "articles":return "文章阅读";case "videos":return "视听学习";case "quiz":return "每日答题";case "subscriptions":return "订阅账号";case "supplement":return "补充积分";default:return "本地频道";}}
    private String detail(String id){switch(id){case "articles":return "6 篇文章 · 每篇 60 秒";case "videos":return "6 个视频 · 每个 60 秒";case "quiz":return "查看提示作答 · 目标 5 分";case "subscriptions":return "新账号 · 每日最多 2 次";case "supplement":return "仅补本批勾选项目 · 固定队尾";default:return "自动识别地区学习平台";}}

    private void build(){
        getWindow().setDecorFitsSystemWindows(false);LinearLayout root=column();root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
        setContentView(root);root.requestApplyInsets();WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null)controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        LinearLayout header=row();header.setPadding(dp(22),dp(18),dp(20),dp(14));root.addView(header,lp(-1,-2));LinearLayout heading=column();header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(text(getString(R.string.app_name),27,INK,true));heading.addView(text(LocalDate.now().format(DateTimeFormatter.ofPattern("M 月 d 日  EEEE",Locale.CHINA)),12,MUTED,false));
        connection=text("待授权",11,ACCENT,true);connection.setPadding(dp(10),dp(6),dp(10),dp(6));connection.setBackground(shape(SOFT,20));header.addView(connection);connection.setOnClickListener(v->settings());
        FrameLayout settingsButton=new FrameLayout(this);settingsButton.setBackground(shape(WHITE,16));LinearLayout.LayoutParams sb=lp(44,44);sb.leftMargin=dp(10);header.addView(settingsButton,sb);settingsButton.addView(new AppIcon(this,"settings",INK),new FrameLayout.LayoutParams(dp(21),dp(21),Gravity.CENTER));settingsButton.setContentDescription("运行设置");settingsButton.setOnClickListener(v->settings());
        FrameLayout pages=new FrameLayout(this);root.addView(pages,new LinearLayout.LayoutParams(-1,0,1));buildTasks(pages);buildLogs(pages);buildDock(root);buildTabs(root);showPage(page);updateState();
    }
    private void buildTasks(FrameLayout pages){
        taskPage=new ScrollView(this);taskPage.setClipToPadding(false);taskPage.setPadding(dp(20),dp(4),dp(20),dp(12));taskPage.setVerticalScrollBarEnabled(false);pages.addView(taskPage,new FrameLayout.LayoutParams(-1,-1));LinearLayout body=column();taskPage.addView(body);
        LinearLayout hero=column();hero.setPadding(dp(22),dp(20),dp(22),dp(20));GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF173E35,0xFF2C6554});gradient.setCornerRadius(dp(26));hero.setBackground(gradient);body.addView(hero,lp(-1,-2));
        hero.addView(text("TODAY  /  今日计划",11,0xFFAACDBF,true));space(hero,9);LinearLayout heroRow=row();hero.addView(heroRow,lp(-1,-2));LinearLayout heroCopy=column();heroRow.addView(heroCopy,new LinearLayout.LayoutParams(0,-2,1));
        heroTitle=text("准备好，就开始",21,WHITE,true);heroCopy.addView(heroTitle);heroCaption=text("任务按你的顺序，依次完成",12,0xFFC8DED4,false);heroCaption.setPadding(0,dp(6),0,0);heroCopy.addView(heroCaption);heroCount=text("05",38,WHITE,true);heroCount.setPadding(dp(14),0,0,0);heroRow.addView(heroCount);
        space(hero,16);progressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progressBar.setMax(100);progressBar.setProgressTintList(ColorStateList.valueOf(0xFF91D5B7));progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF3E6F5D));hero.addView(progressBar,lp(-1,4));
        setupBanner=row();setupBanner.setPadding(dp(16),dp(14),dp(16),dp(14));setupBanner.setBackground(shape(0xFFFFF5DF,20));LinearLayout.LayoutParams bp=lp(-1,-2);bp.topMargin=dp(14);body.addView(setupBanner,bp);LinearLayout setupText=column();setupBanner.addView(setupText,new LinearLayout.LayoutParams(0,-2,1));setupTitle=text("还差一步，开启无障碍",14,0xFF755C28,true);setupCaption=text("用于识别页面和执行所选任务",11,0xFF97804D,false);setupText.addView(setupTitle);setupText.addView(setupCaption);setupAction=text("查看步骤  ›",13,0xFF755C28,true);setupBanner.addView(setupAction);setupBanner.setOnClickListener(v->permissionGuide(false));
        resultSummary=text("",13,INK,false);resultSummary.setPadding(dp(18),dp(16),dp(18),dp(16));resultSummary.setBackground(shape(WHITE,20));LinearLayout.LayoutParams resultP=lp(-1,-2);resultP.topMargin=dp(14);body.addView(resultSummary,resultP);
        space(body,22);LinearLayout section=row();body.addView(section,lp(-1,-2));section.addView(text("任务清单",18,INK,true),new LinearLayout.LayoutParams(0,-2,1));selectionLabel=text("已选 5 项",12,ACCENT,true);section.addView(selectionLabel);TextView instruction=text("长按手柄调整顺序；补充积分固定在队尾",11,MUTED,false);instruction.setPadding(0,dp(6),0,dp(14));body.addView(instruction);cards=column();body.addView(cards);renderCards();TextView foot=text("每项执行前后核验积分，结束后自动返回此页。",11,MUTED,false);foot.setPadding(dp(4),dp(7),dp(4),dp(10));body.addView(foot);
    }
    private void buildLogs(FrameLayout pages){
        logPage=column();logPage.setPadding(dp(20),dp(4),dp(20),dp(12));pages.addView(logPage,new FrameLayout.LayoutParams(-1,-1));LinearLayout toolbar=row();toolbar.setPadding(0,dp(4),0,dp(12));logPage.addView(toolbar);LinearLayout copy=column();toolbar.addView(copy,new LinearLayout.LayoutParams(0,-2,1));copy.addView(text("执行记录",22,INK,true));copy.addView(text("保留最近一次运行，随时分享排查",11,MUTED,false));Button share=button("分享",SOFT,ACCENT);toolbar.addView(share,lp(72,44));share.setOnClickListener(v->shareReport());
        logStatus=text("等待任务开始",12,ACCENT,true);logStatus.setPadding(dp(14),dp(12),dp(14),dp(12));logStatus.setBackground(shape(SOFT,16));logPage.addView(logStatus,lp(-1,-2));space(logPage,12);logScroll=new ScrollView(this);logScroll.setFillViewport(true);logScroll.setBackground(shape(WHITE,22));logPage.addView(logScroll,new LinearLayout.LayoutParams(-1,0,1));
        log=text("还没有执行记录\n\n选择任务并点击“开始执行”，进度会显示在这里。",12,MUTED,false);log.setPadding(dp(18),dp(18),dp(18),dp(24));log.setLineSpacing(dp(5),1);log.setTypeface(Typeface.MONOSPACE);log.setTextIsSelectable(true);logScroll.addView(log);
    }
    private void buildDock(LinearLayout root){
        LinearLayout dock=column();dock.setPadding(dp(20),dp(11),dp(20),dp(10));dock.setBackgroundColor(WHITE);root.addView(dock,lp(-1,-2));dockSummary=text("已选 5 项，按清单顺序执行",12,MUTED,false);dockSummary.setMaxLines(2);dock.addView(dockSummary);space(dock,9);LinearLayout actions=row();dock.addView(actions,lp(-1,52));primary=button("开始执行  →",ACCENT,WHITE);actions.addView(primary,new LinearLayout.LayoutParams(0,-1,1));stop=button("停止",0xFFFBEDEB,0xFFB4594B);LinearLayout.LayoutParams sp=lp(86,-1);sp.leftMargin=dp(10);actions.addView(stop,sp);primary.setOnClickListener(v->primaryAction());stop.setOnClickListener(v->{AutoService s=AutoService.instance;if(s!=null)s.stopRun();updateState();});
    }
    private void buildTabs(LinearLayout root){
        LinearLayout nav=row();nav.setPadding(dp(20),dp(4),dp(20),dp(6));nav.setBackgroundColor(WHITE);root.addView(nav,lp(-1,56));taskTab=text("▦  任务",14,ACCENT,true);logTab=text("≡  日志",14,MUTED,true);for(TextView t:new TextView[]{taskTab,logTab}){t.setGravity(Gravity.CENTER);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(5),0,dp(5),0);nav.addView(t,p);}taskTab.setOnClickListener(v->showPage("tasks"));logTab.setOnClickListener(v->showPage("logs"));
    }
    private void showPage(String next){page=next;boolean tasks=next.equals("tasks");taskPage.setVisibility(tasks?View.VISIBLE:View.GONE);logPage.setVisibility(tasks?View.GONE:View.VISIBLE);taskTab.setBackground(shape(tasks?SOFT:WHITE,18));taskTab.setTextColor(tasks?ACCENT:MUTED);logTab.setBackground(shape(tasks?WHITE:SOFT,18));logTab.setTextColor(tasks?MUTED:ACCENT);}
    private String stateText(String value){switch(value){case "running":return "执行中";case "completed":return "已完成";case "failed":return "未完成";case "stopped":return "已停止";case "skipped":return "已跳过";case "not_run":return "未执行";case "unverified":return "待核验";case "partial":return "积分未满";default:return "";}}
    private void renderCards(){
        if(cards==null)return;cards.removeAllViews();boolean busy=running();AutoService s=AutoService.instance;Map<String,String> states=s==null?new LinkedHashMap<>():s.taskStatusSnapshot();
        for(int index=0;index<order.size();index++){
            String id=order.get(index);boolean checked=Boolean.TRUE.equals(selected.get(id));LinearLayout card=row();card.setPadding(dp(12),dp(13),dp(8),dp(13));card.setBackground(shape(WHITE,22,checked?0xFFCEE3D7:LINE));LinearLayout.LayoutParams cp=lp(-1,-2);cp.bottomMargin=dp(10);cards.addView(card,cp);
            FrameLayout tile=new FrameLayout(this);tile.setBackground(shape(checked?SOFT:BG,15));card.addView(tile,lp(44,44));tile.addView(new AppIcon(this,id,checked?ACCENT:MUTED),new FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER));LinearLayout copy=column();LinearLayout.LayoutParams textP=new LinearLayout.LayoutParams(0,-2,1);textP.leftMargin=dp(12);card.addView(copy,textP);String status=stateText(states.getOrDefault(id,""));copy.addView(text(name(id),15,INK,true));copy.addView(text(detail(id),11,MUTED,false));
            if(!status.isEmpty()){TextView badge=text(status,10,status.equals("未完成")?0xFFBA6855:ACCENT,true);badge.setPadding(0,dp(3),0,0);copy.addView(badge);}
            CheckBox check=new CheckBox(this);check.setButtonTintList(ColorStateList.valueOf(ACCENT));check.setChecked(checked);check.setEnabled(!busy);check.setContentDescription("选择"+name(id));card.addView(check,lp(44,48));check.setOnCheckedChangeListener((v,on)->{if(running())return;selected.put(id,on);prefs.edit().putBoolean("task_"+id,on).apply();renderCards();updateState();});card.setOnClickListener(v->{if(!running())check.setChecked(!check.isChecked());});
            LinearLayout handle=column();handle.setGravity(Gravity.CENTER);card.addView(handle,lp(30,52));handle.addView(text(id.equals("supplement")?"队尾":String.format(Locale.ROOT,"%02d",index+1),9,MUTED,true));if(!id.equals("supplement"))handle.addView(new AppIcon(this,"drag",MUTED),lp(22,26));handle.setContentDescription(id.equals("supplement")?"补充积分固定队尾":"长按拖动"+name(id));
            View.OnLongClickListener drag=v->{if(running()||id.equals("supplement"))return false;card.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);return card.startDragAndDrop(ClipData.newPlainText("task",id),new View.DragShadowBuilder(card),id,0);};handle.setOnLongClickListener(drag);card.setOnLongClickListener(drag);copy.setOnLongClickListener(drag);
            card.setOnDragListener((v,event)->{
                if(running()||!(event.getLocalState() instanceof String))return false;
                if(event.getAction()==DragEvent.ACTION_DRAG_ENTERED)card.setBackground(shape(SOFT,22,ACCENT));
                if(event.getAction()==DragEvent.ACTION_DRAG_EXITED||event.getAction()==DragEvent.ACTION_DRAG_ENDED)card.setBackground(shape(WHITE,22,checked?0xFFCEE3D7:LINE));
                if(event.getAction()==DragEvent.ACTION_DRAG_LOCATION){int[] a=new int[2],b=new int[2];card.getLocationOnScreen(a);taskPage.getLocationOnScreen(b);float y=a[1]+event.getY()-b[1];if(y<dp(56))taskPage.smoothScrollBy(0,-dp(18));else if(y>taskPage.getHeight()-dp(56))taskPage.smoothScrollBy(0,dp(18));}
                if(event.getAction()==DragEvent.ACTION_DROP&&TaskOrder.move(order,(String)event.getLocalState(),id)){prefs.edit().putString("order",String.join(",",order)).apply();renderCards();}return true;
            });
        }
    }
    private void primaryAction(){
        AutoService s=AutoService.instance;if(s==null){permissionGuide(false);return;}
        if(s.running()){boolean resuming=s.paused();s.pauseRun();if(resuming){Intent launch=getPackageManager().getLaunchIntentForPackage("cn.xuexi.android");if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(launch);}}updateState();return;}
        List<String> tasks=new ArrayList<>();for(String id:order)if(Boolean.TRUE.equals(selected.get(id)))tasks.add(id);if(tasks.isEmpty()){toast("请先选择任务");return;}log.setText("");emptyLog=true;if(!s.startRun(tasks,prefs.getBoolean("continue",true)))toast("已有任务在执行");updateState();
    }
    private void updateState(){
        if(primary==null)return;AutoService s=AutoService.instance;boolean connected=s!=null,busy=connected&&s.running(),paused=busy&&s.paused();Map<String,String> states=connected?s.taskStatusSnapshot():new LinkedHashMap<>();int finished=0;for(String value:states.values())if(!value.equals("waiting")&&!value.equals("running"))finished++;int total=busy?states.size():selectionCount();
        boolean granted=connected||AccessibilityGrant.enabled(this);connection.setText(!connected?(granted?"等待连接":"待授权"):busy?(paused?"已暂停":"执行中"):"已就绪");setupBanner.setVisibility(connected?View.GONE:View.VISIBLE);setupTitle.setText(granted?"无障碍已开启，等待服务连接":"还差一步，开启无障碍");setupCaption.setText(granted?"若长时间未连接，可按图示检查系统开关":"查看三步图文说明，按系统提示开启");selectionLabel.setText("已选 "+selectionCount()+" 项");heroCount.setText(busy?finished+"/"+total:String.format(Locale.ROOT,"%02d",total));latestProgress=connected?s.latestProgress():latestProgress;heroTitle.setText(busy?(paused?"暂停一下，随时继续":"正在按计划执行"):"准备好，就开始");heroCaption.setText(busy?(paused?"继续后会重新确认页面":latestProgress):"已选 "+total+" 项任务 · 按顺序执行");progressBar.setProgress(busy&&total>0?finished*100/total:0);
        dockSummary.setText(busy?(paused?"已暂停 · 点击继续返回学习强国":latestProgress):"已选 "+total+" 项，按清单顺序执行");logStatus.setText(latestProgress.isEmpty()?"等待任务开始":latestProgress);primary.setText(busy?(paused?"继续执行  →":"暂停执行"):"开始执行  →");if(!busy&&!summaryDetails.isEmpty()){heroTitle.setText(summaryVerified?"本次获得积分":"本次已核验积分");heroCount.setText("+"+summaryGain);heroCaption.setText(summaryVerified?"已根据执行前后积分核算":"部分项目未核验，收益尚不完整");dockSummary.setText("上次获得 "+summaryGain+" 分 · 已选 "+total+" 项");}
        resultSummary.setText(summaryDetails);resultSummary.setVisibility(!busy&&!summaryDetails.isEmpty()?View.VISIBLE:View.GONE);
        primary.setEnabled(connected&&(busy||total>0));primary.setAlpha(primary.isEnabled()?1:.45f);stop.setVisibility(busy?View.VISIBLE:View.GONE);String next=busy+states.toString();if(!next.equals(cardState)){cardState=next;renderCards();}
    }
    private void settings(){
        LinearLayout panel=column();panel.setPadding(dp(24),dp(24),dp(24),dp(14));panel.addView(text("运行设置",23,INK,true));space(panel,16);
        LinearLayout help=column();help.setPadding(dp(16),dp(16),dp(16),dp(16));help.setBackground(shape(SOFT,20));panel.addView(help,lp(-1,-2));help.addView(text("权限设置指南",17,INK,true));space(help,6);help.addView(text("示例为小米型号手机",12,ACCENT,true));space(help,12);help.addView(text("首次授权\n1. 应用信息 → 权限管理\n2. 点击右上角三个点\n3. 允许应用获取权限 / 允许受限设置",12,MUTED,false));space(help,8);Button initialHelp=button("查看首次授权图示  ›",WHITE,ACCENT);help.addView(initialHelp,lp(-1,48));space(help,14);help.addView(text("开启无障碍\n1. 辅助功能 → 已下载的应用\n2. 每日学习助手 · 屏幕操作\n3. 打开“使用”开关，确认后返回助手",12,MUTED,false));space(help,8);Button access=button("查看无障碍图示  ›",WHITE,ACCENT);help.addView(access,lp(-1,48));space(panel,18);
        Switch keepGoing=new Switch(this);keepGoing.setText("未完成时继续下一项");keepGoing.setTextColor(INK);keepGoing.setTextSize(14);keepGoing.setChecked(prefs.getBoolean("continue",true));keepGoing.setEnabled(!running());keepGoing.setPadding(0,dp(8),0,dp(8));panel.addView(keepGoing);keepGoing.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean("continue",checked).apply());space(panel,14);panel.addView(text("阅读 / 视听固定为 6 × 60 秒。\n订阅点击每日最多两次，失败也保留次数。",12,MUTED,false));space(panel,16);panel.addView(text(getString(R.string.app_name)+"  "+BuildConfig.VERSION_NAME,11,MUTED,false));ScrollView settingsScroll=new ScrollView(this);settingsScroll.setFillViewport(false);settingsScroll.addView(panel);AlertDialog dialog=new AlertDialog.Builder(this).setView(settingsScroll).setPositiveButton("完成",(d,w)->{}).create();initialHelp.setOnClickListener(v->{dialog.dismiss();permissionGuide(true);});access.setOnClickListener(v->{dialog.dismiss();permissionGuide(false);});dialog.show();if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(shape(WHITE,26));dialog.getWindow().setLayout(-1,Math.round(getResources().getDisplayMetrics().heightPixels*.82f));}
    }
    private void checkPermissionGuide(){
        if(permissionGuideChecked)return;permissionGuideChecked=true;
        if(running()||getIntent().getBooleanExtra("show_results",false))return;
        if(!prefs.getBoolean("permission_guide_seen_v1",false))permissionGuide(true);
        else if(!AccessibilityGrant.enabled(this))permissionGuide(false);
    }
    private void permissionGuide(boolean initial){
        startActivity(new Intent(this,PermissionGuideActivity.class).putExtra(PermissionGuideActivity.EXTRA_INITIAL,initial));
        if(initial)prefs.edit().putBoolean("permission_guide_seen_v1",true).apply();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("show_results",false)){reloadSummary();showPage("tasks");updateState();taskPage.post(()->taskPage.smoothScrollTo(0,0));}}
    private void reloadSummary(){
        summaryDetails="";
        try{File file=new File(getFilesDir(),"last_summary.json");if(!file.isFile())return;JSONObject summary=new JSONObject(read(file));if(!summary.optBoolean("finished"))return;
            summaryGain=summary.optInt("total_gained");summaryVerified=summary.optBoolean("fully_verified");StringBuilder lines=new StringBuilder("本次积分明细");JSONObject items=summary.optJSONObject("tasks");
            if(items!=null)for(java.util.Iterator<String> keys=items.keys();keys.hasNext();){String id=keys.next();JSONObject item=items.getJSONObject(id);
                lines.append("\n\n").append(name(id)).append("  ").append(item.isNull("before")?"待核验":item.optInt("before")).append(" → ")
                    .append(item.optBoolean("verified")?item.optInt("after"):"待核验").append(" / ").append(item.optInt("target"));
                if(item.optBoolean("verified"))lines.append("    +").append(item.optInt("gained")).append(" 分");}
            if(items==null||items.length()==0)lines.append("\n未选择普通任务，没有积分变动。");summaryDetails=lines.toString();
        }catch(Exception ignored){summaryDetails="";}
    }
    private String read(File file)throws Exception{return file.isFile()?new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8):"";}
    private void reloadLog(){try{String content=read(new File(getFilesDir(),"last.log"));if(!content.isEmpty()){log.setText(content.substring(Math.max(0,content.length()-60000)));log.setTextColor(INK);emptyLog=false;}}catch(Exception ex){toast("日志读取失败");}}
    private void shareReport(){try{String report=read(new File(getFilesDir(),"last_report.json")),logs=read(new File(getFilesDir(),"last.log"));if(report.isEmpty()&&logs.isEmpty()){toast("当前没有运行报告");return;}String message="报告\n"+report+"\n\n日志\n"+logs.substring(Math.max(0,logs.length()-100000));Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,message);startActivity(Intent.createChooser(send,"分享任务报告"));}catch(Exception ex){toast("分享失败："+ex.getMessage());}}
    static void appendLog(String message){MainActivity screen=visible.get();if(screen!=null)screen.runOnUiThread(()->{boolean follow=screen.emptyLog||screen.logScroll.getChildAt(0).getHeight()<=screen.logScroll.getHeight()+screen.logScroll.getScrollY()+screen.dp(60);if(screen.emptyLog){screen.log.setText("");screen.emptyLog=false;}screen.log.setTextColor(INK);screen.log.append(message+"\n");if(screen.log.length()>60000)screen.log.setText(screen.log.getText().subSequence(screen.log.length()-60000,screen.log.length()));if(follow)screen.logScroll.post(()->screen.logScroll.fullScroll(View.FOCUS_DOWN));screen.updateState();});}
    static void setProgress(String message){MainActivity screen=visible.get();if(screen!=null)screen.runOnUiThread(()->{screen.latestProgress=message;screen.updateState();});}
    static void refreshUi(){MainActivity screen=visible.get();if(screen!=null)screen.runOnUiThread(()->{if(!screen.running())screen.reloadSummary();screen.updateState();});}
}
