package com.xoda.camera;

import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.camera.core.*;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Fresh GLES2 single-texture CameraX SurfaceProcessor. Preview and recorded
 * video pass through exactly this vertex-deforming photo-beauty shader.
 */
public final class LiveCameraEffect implements SurfaceProcessor,
        SurfaceTexture.OnFrameAvailableListener, AutoCloseable {
    private static final int RECORDABLE=0x3142;
    private final HandlerThread thread=new HandlerThread("Xoda new GL");
    private final Handler handler;
    private final Executor queue;
    private final Map<SurfaceTexture,Input> inputs=new LinkedHashMap<>();
    private final Map<SurfaceOutput,Output> outputs=new LinkedHashMap<>();
    private final float[] inputTransform=new float[16],outputTransform=new float[16];
    private volatile BeautyState state=new BeautyState();
    private volatile FacePose pose=FacePose.none();
    private volatile Consumer<Throwable> errors;
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface parking=EGL14.EGL_NO_SURFACE;
    private EGLConfig config;
    private int program,vbo,ibo,attribute0,attribute1;
    private GlCompanion.Uniforms uniforms;
    private boolean closing,broken,ready;
    private long frames;
    private Input newest;
    public LiveCameraEffect(){
        thread.start();handler=new Handler(thread.getLooper());
        queue=action->{if(!handler.post(action))throw new IllegalStateException("GPU thread closed");};
    }
    public Executor executor(){return queue;}
    public void state(BeautyState s){state=s;}
    public void face(FacePose f){pose=f==null?FacePose.none():f;}
    public void onError(Consumer<Throwable> listener){errors=listener;}
    public boolean ready(){return ready&&!broken;}
    public long frames(){return frames;}
    private void init(){
        if(program!=0)return;
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version=new int[2];
        if(!EGL14.eglInitialize(display,version,0,version,1))throw egl("display init");
        EGLConfig[] list=new EGLConfig[1];int[] count=new int[1];
        int[] attrs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
                EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT|EGL14.EGL_PBUFFER_BIT,
                RECORDABLE,1,EGL14.EGL_NONE};
        if(!EGL14.eglChooseConfig(display,attrs,0,list,0,1,count,0)||count[0]<1)
            throw new IllegalStateException("Recordable GLES2 unsupported");
        config=list[0];
        context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        parking=EGL14.eglCreatePbufferSurface(display,config,
                new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
        park();
        program=GlCompanion.program(BeautyShader.VERTEX,BeautyShader.fragment(true));
        uniforms=new GlCompanion.Uniforms(program);
        attribute0=GLES20.glGetAttribLocation(program,"aPosition");
        attribute1=GLES20.glGetAttribLocation(program,"aUv");
        if(attribute0<0||attribute1<0)throw new IllegalStateException("Vertex layout invalid");
        int[] ids=new int[2];GLES20.glGenBuffers(2,ids,0);
        vbo=ids[0];ibo=ids[1];
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER,ibo);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,
                MeshWarp.INDICES*2,MeshWarp.indices(),GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER,0);
        GlCompanion.check("beauty GLES setup");
        ready=true;
    }
    private void park(){
        if(!EGL14.eglMakeCurrent(display,parking,parking,context))throw egl("parking surface");
    }
    @Override public void onInputSurface(@NonNull SurfaceRequest request)throws ProcessingException{
        if(closing||broken){request.willNotProvideSurface();return;}
        Input input=null;
        try{
            init();park();
            int id=GlCompanion.externalTexture();
            SurfaceTexture texture=new SurfaceTexture(id);
            Size size=request.getResolution();
            texture.setDefaultBufferSize(size.getWidth(),size.getHeight());
            Surface surface=new Surface(texture);
            input=new Input(request,texture,surface,id);
            final Input current=input;inputs.put(texture,input);newest=input;
            request.setTransformationInfoListener(queue,info->current.rotation=info.getRotationDegrees());
            texture.setOnFrameAvailableListener(this,handler);
            request.provideSurface(surface,queue,result->removeInput(current));
        }catch(Throwable t){
            if(input!=null)removeInput(input);
            request.willNotProvideSurface();fail(t);throw exception(t);
        }
    }
    @Override public void onOutputSurface(@NonNull SurfaceOutput owner)throws ProcessingException{
        if(closing||broken){owner.close();return;}
        try{
            init();
            Surface surface=owner.getSurface(queue,event->removeOutput(owner));
            EGLSurface egl=EGL14.eglCreateWindowSurface(display,config,surface,
                    new int[]{EGL14.EGL_NONE},0);
            if(egl==EGL14.EGL_NO_SURFACE)throw egl("camera output EGL");
            outputs.put(owner,new Output(owner,egl));
        }catch(Throwable t){owner.close();fail(t);throw exception(t);}
    }
    @Override public void onFrameAvailable(SurfaceTexture texture){
        Input item=inputs.get(texture);
        if(item==null||closing||broken)return;
        try{
            park();texture.updateTexImage();
            if(item!=newest||outputs.isEmpty())return;
            texture.getTransformMatrix(inputTransform);
            FacePose face=pose;
            if(SystemClock.elapsedRealtime()-face.observedAtMs>1100)face=FacePose.none();
            BeautyState controls=state;
            FloatBuffer mesh=MeshWarp.vertices(face,controls);
            for(Output output:new ArrayList<>(outputs.values())){
                output.owner.updateTransformMatrix(outputTransform,inputTransform);
                draw(output,item,mesh,face,controls,texture.getTimestamp());
            }
            frames++;
        }catch(Throwable t){fail(t);}
    }
    private void draw(Output output,Input input,FloatBuffer mesh,
            FacePose face,BeautyState beauty,long timestamp){
        if(!EGL14.eglMakeCurrent(display,output.egl,output.egl,context))
            throw egl("set recordable surface");
        Size size=output.owner.getSize();
        GLES20.glViewport(0,0,size.getWidth(),size.getHeight());
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,input.id);
        int sampler=uniforms.at("uCamera");
        if(sampler>=0)GLES20.glUniform1i(sampler,0);
        uniforms.mat("uTexTransform",outputTransform);
        uniforms.f("uMirror",0f);
        uniforms.settings(beauty,face,size.getWidth(),size.getHeight());
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,vbo);
        mesh.rewind();
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                MeshWarp.VERTICES*16,mesh,GLES20.GL_DYNAMIC_DRAW);
        GLES20.glVertexAttribPointer(attribute0,2,GLES20.GL_FLOAT,false,16,0);
        GLES20.glVertexAttribPointer(attribute1,2,GLES20.GL_FLOAT,false,16,8);
        GLES20.glEnableVertexAttribArray(attribute0);
        GLES20.glEnableVertexAttribArray(attribute1);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER,ibo);
        GlCompanion.check("upload portrait vertex grid");
        GLES20.glDrawElements(GLES20.GL_TRIANGLES,MeshWarp.INDICES,GLES20.GL_UNSIGNED_SHORT,0);
        GlCompanion.check("draw camera beauty");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER,0);
        if((output.owner.getTargets()&CameraEffect.VIDEO_CAPTURE)!=0)
            if(!EGLExt.eglPresentationTimeANDROID(display,output.egl,timestamp))
                throw egl("video presentation time");
        if(!EGL14.eglSwapBuffers(display,output.egl))throw egl("video/preview swap");
    }
    private void removeInput(Input i){
        if(inputs.remove(i.texture)==null)return;
        i.request.clearTransformationInfoListener();
        i.texture.setOnFrameAvailableListener(null);
        i.texture.release();i.surface.release();
        if(context!=EGL14.EGL_NO_CONTEXT){
            park();GLES20.glDeleteTextures(1,new int[]{i.id},0);
        }
        if(newest==i)newest=null;
        finish();
    }
    private void removeOutput(SurfaceOutput owner){
        Output out=outputs.remove(owner);
        if(out!=null&&display!=EGL14.EGL_NO_DISPLAY){
            park();EGL14.eglDestroySurface(display,out.egl);
        }
        owner.close();finish();
    }
    private void fail(Throwable t){
        if(broken)return;
        broken=true;ready=false;Log.e("XodaNewGpu","Live effect stopped",t);
        Consumer<Throwable> listener=errors;
        if(listener!=null)listener.accept(t);
    }
    @Override public void close(){
        handler.post(()->{
            closing=true;ready=false;
            for(Output out:new ArrayList<>(outputs.values()))removeOutput(out.owner);
            finish();
        });
    }
    private void finish(){
        if(!closing||!inputs.isEmpty()||!outputs.isEmpty()||display==EGL14.EGL_NO_DISPLAY)
            return;
        park();
        GLES20.glDeleteProgram(program);
        GLES20.glDeleteBuffers(2,new int[]{vbo,ibo},0);
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(display,parking);
        EGL14.eglDestroyContext(display,context);
        EGL14.eglTerminate(display);EGL14.eglReleaseThread();
        display=EGL14.EGL_NO_DISPLAY;
        thread.quitSafely();
    }
    private static IllegalStateException egl(String step){
        return new IllegalStateException(step+": EGL 0x"+Integer.toHexString(EGL14.eglGetError()));
    }
    private static ProcessingException exception(Throwable cause){
        ProcessingException x=new ProcessingException();x.initCause(cause);return x;
    }
    private static final class Input {
        final SurfaceRequest request;final SurfaceTexture texture;final Surface surface;
        final int id;int rotation;
        Input(SurfaceRequest request,SurfaceTexture texture,Surface surface,int id){
            this.request=request;this.texture=texture;this.surface=surface;this.id=id;
        }
    }
    private static final class Output{
        final SurfaceOutput owner;final EGLSurface egl;
        Output(SurfaceOutput owner,EGLSurface egl){this.owner=owner;this.egl=egl;}
    }
}
