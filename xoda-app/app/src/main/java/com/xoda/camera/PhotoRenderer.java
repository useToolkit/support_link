package com.xoda.camera;

import android.graphics.Bitmap;
import android.opengl.*;
import java.nio.*;

/**
 * Captured stills use the SAME independent mesh + beauty color shader as live
 * CameraX preview/video (sampler2D variant), not an unfiltered JPEG save.
 */
final class PhotoRenderer implements AutoCloseable {
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface parking=EGL14.EGL_NO_SURFACE;
    private int program,position,uv,vbo,ibo,sourceTex,outputTex,fbo;
    private GlCompanion.Uniforms uniforms;
    PhotoRenderer(){
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version=new int[2];
        if(!EGL14.eglInitialize(display,version,0,version,1))
            throw new IllegalStateException("Photo GPU initialization");
        EGLConfig[] c=new EGLConfig[1];int[] count=new int[1];
        int[] spec={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
                EGL14.EGL_ALPHA_SIZE,8,
                EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_NONE};
        if(!EGL14.eglChooseConfig(display,spec,0,c,0,1,count,0)||count[0]==0)
            throw new IllegalStateException("Photo GL framebuffer unsupported");
        context=EGL14.eglCreateContext(display,c[0],EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        parking=EGL14.eglCreatePbufferSurface(display,c[0],
                new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
        if(!EGL14.eglMakeCurrent(display,parking,parking,context))
            throw new IllegalStateException("Photo GL makeCurrent");
        program=GlCompanion.program(BeautyShader.VERTEX,BeautyShader.fragment(false));
        uniforms=new GlCompanion.Uniforms(program);
        position=GLES20.glGetAttribLocation(program,"aPosition");
        uv=GLES20.glGetAttribLocation(program,"aUv");
        int[] ids=new int[2];GLES20.glGenBuffers(2,ids,0);vbo=ids[0];ibo=ids[1];
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER,ibo);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,
                MeshWarp.INDICES*Short.BYTES,MeshWarp.indices(),GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER,0);
        int[] tex=new int[2];GLES20.glGenTextures(2,tex,0);
        sourceTex=tex[0];outputTex=tex[1];
        GLES20.glGenFramebuffers(1,ids,0);fbo=ids[0];
        GlCompanion.check("initialize offscreen beauty exporter");
    }
    Bitmap render(Bitmap original,BeautyState state,FacePose pose){
        if(original==null||original.isRecycled())throw new IllegalArgumentException("Invalid photo");
        int originalW=original.getWidth(),originalH=original.getHeight();
        float scale=Math.min(1f,3072f/Math.max(originalW,originalH));
        int width=Math.max(1,Math.round(originalW*scale));
        int height=Math.max(1,Math.round(originalH*scale));
        Bitmap input=(width==originalW&&height==originalH)?original:
                Bitmap.createScaledBitmap(original,width,height,true);
        Bitmap result=null;
        try{
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,sourceTex);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,input,0);
            GLES20.glUniform1i(uniforms.at("uCamera"),0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,outputTex);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,width,height,
                    0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D,outputTex,0);
            if(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)!=GLES20.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Photo GPU output incomplete");
            GLES20.glViewport(0,0,width,height);
            GLES20.glDisable(GLES20.GL_BLEND);
            float[] matrix=new float[16];Matrix.setIdentityM(matrix,0);
            // Source Bitmap and camera SurfaceTexture use opposite Y origins.
            matrix[5]=-1f;matrix[13]=1f;
            uniforms.mat("uTexTransform",matrix);
            uniforms.f("uMirror",0f);
            uniforms.settings(state,pose,width,height);
            FloatBuffer vertices=MeshWarp.vertices(pose,state);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,vbo);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                    MeshWarp.VERTICES*4*Float.BYTES,vertices,GLES20.GL_STATIC_DRAW);
            GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,16,0);
            GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,8);
            GLES20.glEnableVertexAttribArray(position);GLES20.glEnableVertexAttribArray(uv);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER,ibo);
            GlCompanion.check("set still beauty geometry");
            GLES20.glDrawElements(GLES20.GL_TRIANGLES,MeshWarp.INDICES,
                    GLES20.GL_UNSIGNED_SHORT,0);
            GlCompanion.check("render still beauty");
            result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            int strip=16;
            ByteBuffer bytes=ByteBuffer.allocateDirect(width*strip*4);
            int[] colors=new int[width*strip];
            for(int y=0;y<height;y+=strip){
                int n=Math.min(strip,height-y);
                bytes.clear();
                GLES20.glReadPixels(0,y,width,n,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,bytes);
                GlCompanion.check("read still beauty pixels");
                bytes.rewind();
                for(int j=0;j<n;j++)for(int x=0;x<width;x++){
                    int r=bytes.get()&255,g=bytes.get()&255,b=bytes.get()&255,a=bytes.get()&255;
                    colors[(n-j-1)*width+x]=(a<<24)|(r<<16)|(g<<8)|b;
                }
                result.setPixels(colors,0,width,0,height-y-n,width,n);
            }
            return result;
        }catch(Throwable error){
            if(result!=null)result.recycle();
            throw error;
        }finally{
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER,0);
            if(input!=original)input.recycle();
        }
    }
    @Override public void close(){
        if(display==EGL14.EGL_NO_DISPLAY)return;
        EGL14.eglMakeCurrent(display,parking,parking,context);
        GLES20.glDeleteTextures(2,new int[]{sourceTex,outputTex},0);
        GLES20.glDeleteBuffers(2,new int[]{vbo,ibo},0);
        GLES20.glDeleteFramebuffers(1,new int[]{fbo},0);
        GLES20.glDeleteProgram(program);
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(display,parking);
        EGL14.eglDestroyContext(display,context);
        EGL14.eglTerminate(display);EGL14.eglReleaseThread();
        display=EGL14.EGL_NO_DISPLAY;
    }
}
