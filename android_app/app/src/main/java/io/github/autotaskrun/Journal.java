package io.github.autotaskrun;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

final class Journal {
    private final SharedPreferences prefs;
    Journal(Context context) {
        prefs=context.getSharedPreferences("task_state_v1",Context.MODE_PRIVATE);
    }
    private String day(){return LocalDate.now().toString();}
    private String key(String task){return task+"_"+day();}
    private JSONObject entries(String task) throws Exception {return new JSONObject(prefs.getString(key(task),"{}"));}
    synchronized int completed(String task)throws Exception{
        JSONObject all=entries(task);int count=0;
        for(java.util.Iterator<String> keys=all.keys();keys.hasNext();)if("completed".equals(all.getString(keys.next())))count++;
        return count;
    }
    synchronized boolean seen(String task,String title)throws Exception{
        JSONObject all=entries(task);
        for(java.util.Iterator<String> keys=all.keys();keys.hasNext();){String name=keys.next();
            if(task.equals("videos")?VideoIdentity.same(title,name):Vision.same(title,name))return true;}
        return false;
    }
    synchronized void record(String task,String title,String status)throws Exception{
        JSONObject all=entries(task);
        if(task.equals("videos"))for(java.util.Iterator<String> keys=all.keys();keys.hasNext();){String name=keys.next();
            if(VideoIdentity.same(title,name)){title=name;break;}}
        all.put(title,status);
        if(!prefs.edit().putString(key(task),all.toString()).commit())throw new Exception("进度未能写入本机存储，已停止");
    }
    synchronized int subscriptionClicks()throws Exception{
        return new JSONArray(prefs.getString("subscription_attempts_"+day(),"[]")).length();
    }
    synchronized boolean subscribedBefore(String name)throws Exception{
        JSONArray history=new JSONArray(prefs.getString("subscription_history","[]"));
        for(int i=0;i<history.length();i++)if(Vision.same(name,history.getString(i)))return true;
        return false;
    }
    synchronized void reserveSubscription(String name)throws Exception{
        if(subscriptionClicks()>=2 || subscribedBefore(name))throw new Exception("今日订阅点击已达上限或账号曾订阅过，禁止点击");
        String today=day();JSONArray attempts=new JSONArray(prefs.getString("subscription_attempts_"+today,"[]"));
        JSONArray history=new JSONArray(prefs.getString("subscription_history","[]"));
        attempts.put(new JSONObject().put("name",name).put("status","reserved").put("at",System.currentTimeMillis()));
        history.put(name);
        // Commit reservation before touching the app. A crash cannot authorize a third click.
        if(!prefs.edit().putString("subscription_attempts_"+today,attempts.toString())
                .putString("subscription_history",history.toString()).commit())throw new Exception("订阅预留记录未能写入，禁止点击");
    }
    synchronized void markSubscription(String name,String status)throws Exception{
        String today=day();JSONArray attempts=new JSONArray(prefs.getString("subscription_attempts_"+today,"[]"));
        for(int i=0;i<attempts.length();i++){
            JSONObject item=attempts.getJSONObject(i);if(name.equals(item.optString("name"))){item.put("status",status);break;}
        }
        if(!prefs.edit().putString("subscription_attempts_"+today,attempts.toString()).commit())throw new Exception("订阅状态写入失败");
    }
}
