package com.xoda.camera;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import java.util.HashMap;
import java.util.Map;

/** Minimal GLES2 helpers, independently authored for XODA's new engine. */
final class GlCompanion {
    private GlCompanion(){}
    static int program(String vertex,String fragment){
        int v=compile(GLES20.GL_VERTEX_SHADER,vertex),f=compile(GLES20.GL_FRAGMENT_SHADER,fragment);
        int p=GLES20.glCreateProgram();
        if(p==0)throw new IllegalStateException("GPU program allocation");
        GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);
        int[] linked={0};GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,linked,0);
        String log=GLES20.glGetProgramInfoLog(p);
        GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);
        if(linked[0]==0){GLES20.glDeleteProgram(p);throw new IllegalStateException("GPU link: "+log);}
        return p;
    }
    static int compile(int type,String source){
        int shader=GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);
        int[] ok={0};GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0)throw new IllegalStateException("GPU shader: "+GLES20.glGetShaderInfoLog(shader));
        return shader;
    }
    static int externalTexture(){
        int[] a={0};GLES20.glGenTextures(1,a,0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,a[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        check("external texture");return a[0];
    }
    static void check(String stage){
        int code=GLES20.glGetError();
        if(code!=GLES20.GL_NO_ERROR)
            throw new IllegalStateException(stage+" GL error 0x"+Integer.toHexString(code));
    }
    static final class Uniforms{
        private final int program;
        private final Map<String,Integer> cache=new HashMap<>();
        Uniforms(int p){program=p;}
        int at(String name){
            Integer n=cache.get(name);
            if(n!=null)return n;
            int location=GLES20.glGetUniformLocation(program,name);
            cache.put(name,location);return location;
        }
        void f(String name,float x){int n=at(name);if(n>=0)GLES20.glUniform1f(n,x);}
        void v2(String name,float x,float y){
            int n=at(name);if(n>=0)GLES20.glUniform2f(n,x,y);
        }
        void v4(String name,float x,float y,float z,float w){
            int n=at(name);if(n>=0)GLES20.glUniform4f(n,x,y,z,w);
        }
        void mat(String name,float[] m){
            int n=at(name);if(n>=0)GLES20.glUniformMatrix4fv(n,1,false,m,0);
        }
        void settings(BeautyState s,FacePose face,int width,int height){
            boolean has=face!=null&&face.valid&&s.beautyEnabled;
            f("uFaceValid",has?1f:0f);
            v2("uPixel",1f/Math.max(1,width),1f/Math.max(1,height));
            if(has){
                v4("uFace",face.left,face.top,face.right,face.bottom);
                v4("uEyes",face.eyeL.x,face.eyeL.y,face.eyeR.x,face.eyeR.y);
                v4("uNoseMouth",face.nose.x,face.nose.y,face.mouth.x,face.mouth.y);
                v4("uCheeks",face.cheekL.x,face.cheekL.y,face.cheekR.x,face.cheekR.y);
                v4("uBrows",face.browL.x,face.browL.y,face.browR.x,face.browR.y);
            }
            float b=has?1f:0f;
            v4("uSkin0",(s.get("smooth"))*b,s.get("balance")*b,
                    s.get("toneup")*b,s.get("eyebag")*b);
            v4("uSkin1",s.get("teeth")*b,s.get("wrinkle")*b,
                    s.get("clarity")*b,s.get("clear")*b);
            v4("uMakeup0",s.get("lipstick")*b,s.get("blusher")*b,
                    s.get("eyeshadow")*b,s.get("eyeliner")*b);
            v4("uMakeup1",s.get("eyelashes")*b,s.get("eyebrows")*b,0f,0f);
            float exposure=0f,contrast=1f,sat=1f,warmth=0f;
            float sr=1f,sg=1f,sb=1f,offset=0f;
            if("고화질".equals(s.mode)){contrast=1.06f;exposure=.012f;}
            else if("아이폰".equals(s.mode)){
                sr=1.012f;sg=1.006f;sb=.988f;contrast=.965f;offset=.009f;
            }
            v4("uColor",exposure,contrast,sat,warmth);
            v4("uStyle",sr,sg,sb,offset);
        }
    }
}
