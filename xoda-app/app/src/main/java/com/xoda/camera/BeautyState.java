package com.xoda.camera;

import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** No legacy effect settings or coefficients are imported. */
public final class BeautyState {
    private final LinkedHashMap<String,Float> values=new LinkedHashMap<>();
    public String mode="기본";
    public String style="original";
    public float filter=0f;
    public boolean beautyEnabled=true;
    public BeautyState(){
        for(ReferenceControls.Item item:ReferenceControls.ALL)
            values.put(item.key,0f);
    }
    public float get(String key){Float f=values.get(key);return f==null?0f:f;}
    public void set(String key,float strength){
        if(!values.containsKey(key))throw new IllegalArgumentException(key);
        values.put(key,Float.isFinite(strength)?Math.max(-1f,Math.min(1f,strength)):0f);
    }
    public Map<String,Float> snapshot(){return new LinkedHashMap<>(values);}
    public void resetBeauty(){
        for(ReferenceControls.Item item:ReferenceControls.ALL)
            if(!item.category.equals(ReferenceControls.SHAPES))values.put(item.key,0f);
        style="original";beautyEnabled=true;
    }
    public void resetAll(){resetBeauty();mode="기본";filter=0f;}
    public void applyStyle(String selected){
        for(ReferenceControls.Item item:ReferenceControls.group(ReferenceControls.SHAPES))
            values.put(item.key,item.key.equals(selected)?item.initial:0f);
        style=selected;
        switch(selected) {
            case "daily":
                set("smooth",.20f);set("enlarge",.11f);set("narrow",.12f);
                set("slim",.12f);break;
            case "natural":
                set("smooth",.10f);set("enlarge",.04f);set("slim",.06f);break;
            case "baby":
                set("smooth",.20f);set("enlarge",.22f);set("short",.19f);break;
            case "slim_style":
                set("slim",.30f);set("cheekbone",.13f);break;
            case "beauty_style":
                set("smooth",.30f);set("enlarge",.18f);set("slim",.20f);
                set("toneup",.15f);break;
            case "original":
                // Neutral face-style recipe. General individual beauty controls remain
                // independently editable after selecting a style.
                set("smooth",0f);set("enlarge",0f);set("slim",0f);break;
        }
    }
    public String serialize(){
        JSONObject json=new JSONObject();
        try {
            json.put("schema",4).put("mode",mode).put("style",style)
                .put("filter",filter).put("beautyEnabled",beautyEnabled);
            JSONObject controls=new JSONObject();
            for(Map.Entry<String,Float> e:values.entrySet())controls.put(e.getKey(),e.getValue());
            json.put("controls",controls);
        }catch(Exception e){throw new IllegalStateException(e);}
        return json.toString();
    }
    public static BeautyState restore(SharedPreferences prefs){
        BeautyState b=new BeautyState();
        String stored=prefs.getString("xoda_focus_schema_4",null);
        if(stored==null)return b;
        try{
            JSONObject json=new JSONObject(stored);
            if(json.optInt("schema",0)!=4)return b;
            b.mode=json.optString("mode","기본");
            b.style=json.optString("style","original");
            b.beautyEnabled=json.optBoolean("beautyEnabled",true);
            b.filter=(float)json.optDouble("filter",0f);
            JSONObject controls=json.optJSONObject("controls");
            if(controls!=null)for(ReferenceControls.Item item:ReferenceControls.ALL)
                if(controls.has(item.key))b.set(item.key,(float)controls.getDouble(item.key));
        }catch(Exception ignored){}
        return b;
    }
    public void persist(SharedPreferences prefs){
        prefs.edit().putString("xoda_focus_schema_4",serialize()).apply();
    }
}
