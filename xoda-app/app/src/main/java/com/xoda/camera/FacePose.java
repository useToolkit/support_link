package com.xoda.camera;

import android.graphics.PointF;

/** Upright, top-left normalized geometry, independent of any camera SDK. */
public final class FacePose {
    public final boolean valid;
    public final float left,top,right,bottom;
    public final PointF eyeL,eyeR,nose,mouth,cheekL,cheekR,chin,forehead,browL,browR;
    public final float[] dense;
    public final long observedAtMs;
    public FacePose(float left,float top,float right,float bottom,
            PointF eyeL,PointF eyeR,PointF nose,PointF mouth,PointF cheekL,PointF cheekR,
            PointF chin,PointF forehead,PointF browL,PointF browR,float[] dense,long t){
        this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        this.eyeL=eyeL;this.eyeR=eyeR;this.nose=nose;this.mouth=mouth;
        this.cheekL=cheekL;this.cheekR=cheekR;this.chin=chin;this.forehead=forehead;
        this.browL=browL;this.browR=browR;this.dense=dense;this.observedAtMs=t;
        valid=right>left+.015f&&bottom>top+.015f;
    }
    public float cx(){return (left+right)*.5f;}
    public float cy(){return (top+bottom)*.5f;}
    public float width(){return Math.max(.001f,right-left);}
    public float height(){return Math.max(.001f,bottom-top);}
    public static PointF point(float x,float y){return new PointF(x,y);}
    public static FacePose none(){return new FacePose(0,0,0,0,
            point(0,0),point(0,0),point(0,0),point(0,0),point(0,0),point(0,0),
            point(0,0),point(0,0),point(0,0),point(0,0),new float[0],0);}
    public FacePose smooth(FacePose prior,float alpha){
        if(prior==null||!prior.valid||!valid)return this;
        if(Math.abs(prior.cx()-cx())>.16f||Math.abs(prior.cy()-cy())>.16f)return this;
        float a=Math.max(0f,Math.min(1f,alpha));
        return new FacePose(lerp(prior.left,left,a),lerp(prior.top,top,a),
                lerp(prior.right,right,a),lerp(prior.bottom,bottom,a),
                lerp(prior.eyeL,eyeL,a),lerp(prior.eyeR,eyeR,a),
                lerp(prior.nose,nose,a),lerp(prior.mouth,mouth,a),
                lerp(prior.cheekL,cheekL,a),lerp(prior.cheekR,cheekR,a),
                lerp(prior.chin,chin,a),lerp(prior.forehead,forehead,a),
                lerp(prior.browL,browL,a),lerp(prior.browR,browR,a),dense,observedAtMs);
    }
    private static float lerp(float x,float y,float a){return x*(1-a)+y*a;}
    private static PointF lerp(PointF x,PointF y,float a){
        return point(lerp(x.x,y.x,a),lerp(x.y,y.y,a));
    }
}
