package com.xoda.camera;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Only two supported facial geometry operations.
 * 1) facesize contracts the facial envelope with a feathered boundary.
 * 2) mfeyebrownose shortens brow-to-nose middle face without moving the brow
 *    or mouth anchors. These are original 2D effects, not SODA coefficients.
 *
 * One mesh is shared by real-time preview, recorded video and still export.
 */
final class MeshWarp {
    static final int COLUMNS=49, ROWS=73;
    static final int VERTICES=COLUMNS*ROWS;
    static final int INDICES=(COLUMNS-1)*(ROWS-1)*6;

    static FloatBuffer vertices(FacePose face,BeautyState settings) {
        FloatBuffer out=ByteBuffer.allocateDirect(VERTICES*4*Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        boolean valid=face!=null&&face.valid&&settings.beautyEnabled;
        float scale=valid?clamp(settings.get("facesize"),0f,1f):0f;
        float midface=valid?clamp(settings.get("mfeyebrownose"),0f,1f):0f;
        float cx=valid?face.cx():.5f, cy=valid?face.cy():.5f;
        float w=valid?face.width():.4f, h=valid?face.height():.6f;
        float brow=valid?clamp((face.browL.y+face.browR.y)*.5f,face.top,face.bottom):.3f;
        float eyes=valid?(face.eyeL.y+face.eyeR.y)*.5f:.4f;
        float nose=valid?Math.max(eyes+h*.10f,face.nose.y):.5f;
        float mouth=valid?Math.max(nose+h*.12f,face.mouth.y):.65f;
        float magnitude=midface*.26f*Math.max(.08f*h,nose-brow);
        float upper=eyes,lower=Math.max(mouth,nose+h*.12f);
        for(int iy=0;iy<ROWS;iy++){
            float y=(float)iy/(ROWS-1);
            for(int ix=0;ix<COLUMNS;ix++){
                float x=(float)ix/(COLUMNS-1);
                float dx=0f,dy=0f;
                if(scale>0f){
                    float rx=(x-cx)/(.60f*w),ry=(y-cy)/(.61f*h);
                    float r=(float)Math.sqrt(rx*rx+ry*ry);
                    float fade=1f-smoothstep(.70f,1.30f,r);
                    dx-=(x-cx)*(.22f*scale)*fade;
                    dy-=(y-cy)*(.17f*scale)*fade;
                }
                if(midface>0f&&lower>upper+.001f){
                    float rise=smoothstep(upper,nose,y);
                    float fall=1f-smoothstep(nose,lower,y);
                    float side=Math.abs(x-cx)/w;
                    float feather=1f-smoothstep(.32f,.60f,side);
                    dy-=magnitude*rise*fall*feather;
                }
                if(valid){
                    dx=clamp(dx,-.14f*w,.14f*w);
                    dy=clamp(dy,-.12f*h,.12f*h);
                }
                float px=clamp(x+dx,0f,1f),py=clamp(y+dy,0f,1f);
                out.put(px*2f-1f).put(1f-py*2f).put(x).put(1f-y);
            }
        }
        out.rewind();
        return out;
    }
    static ShortBuffer indices(){
        ShortBuffer out=ByteBuffer.allocateDirect(INDICES*2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        for(int y=0;y<ROWS-1;y++)for(int x=0;x<COLUMNS-1;x++){
            short a=(short)(y*COLUMNS+x),b=(short)(a+1);
            short c=(short)(a+COLUMNS),d=(short)(c+1);
            out.put(a).put(c).put(b).put(b).put(c).put(d);
        }
        out.rewind();return out;
    }
    private static float smoothstep(float a,float b,float x){
        float t=clamp((x-a)/Math.max(.00001f,b-a),0f,1f);
        return t*t*(3f-2f*t);
    }
    private static float clamp(float x,float lo,float hi){
        return Math.max(lo,Math.min(hi,x));
    }
    private MeshWarp(){}
}
