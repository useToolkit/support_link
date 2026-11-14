package com.xoda.camera;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * MediaPipe 478-point inference on ARM64, with independent ML Kit landmark
 * fallback if that native runtime is unavailable. No remote recognition.
 */
public final class LandmarkTracker implements ImageAnalysis.Analyzer,AutoCloseable {
    private static final String TAG="XodaRebornFaces";
    private final Context context;
    private final Consumer<FacePose> callback;
    private FaceLandmarker landmarker;
    private boolean mediaPipeUnavailable;
    private FaceDetector fallback;
    private FacePose previous=FacePose.none();
    private long lastProcessed;
    private boolean closed;
    public LandmarkTracker(Context context,Consumer<FacePose> callback){
        this.context=context.getApplicationContext();
        this.callback=callback;
        fallback=FaceDetection.getClient(new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .enableTracking().build());
    }
    @Override public void analyze(@NonNull ImageProxy frame){
        try{
            if(closed)return;
            long now=SystemClock.elapsedRealtime();
            if(now-lastProcessed<75L)return;
            lastProcessed=now;
            Bitmap bitmap=toUprightBitmap(frame);
            if(bitmap==null)return;
            try{
                FacePose next=detect(bitmap,now);
                if(next.valid)next=next.smooth(previous,.30f);
                previous=next;
                callback.accept(next);
            } finally {bitmap.recycle();}
        }catch(Throwable e){
            Log.w(TAG,"Landmark processing failed, retaining camera preview",e);
            callback.accept(FacePose.none());
        }finally{frame.close();}
    }
    private FacePose detect(Bitmap bitmap,long timestamp){
        if(!mediaPipeUnavailable){
            try{
                if(landmarker==null) {
                    BaseOptions base=BaseOptions.builder()
                        .setModelAssetPath("face_landmarker.task").build();
                    FaceLandmarker.FaceLandmarkerOptions options=
                        FaceLandmarker.FaceLandmarkerOptions.builder()
                        .setBaseOptions(base).setRunningMode(RunningMode.IMAGE)
                        .setNumFaces(1).setMinFaceDetectionConfidence(.48f)
                        .setMinFacePresenceConfidence(.48f).build();
                    landmarker=FaceLandmarker.createFromOptions(context,options);
                }
                MPImage image=new BitmapImageBuilder(bitmap).build();
                List<List<NormalizedLandmark>> faces=landmarker.detect(image).faceLandmarks();
                if(!faces.isEmpty()&&faces.get(0).size()>=468)
                    return fromDense(faces.get(0),timestamp);
            }catch(Throwable error){
                mediaPipeUnavailable=true;
                Log.w(TAG,"Dense landmark engine unavailable: ML Kit fallback",error);
                if(landmarker!=null){try{landmarker.close();}catch(Exception ignored){}landmarker=null;}
            }
        }
        try{
            List<Face> faces=Tasks.await(fallback.process(
                    InputImage.fromBitmap(bitmap,0)),1600,TimeUnit.MILLISECONDS);
            if(!faces.isEmpty())return fromMlKit(faces.get(0),
                    bitmap.getWidth(),bitmap.getHeight(),timestamp);
        }catch(Exception e){Log.w(TAG,"Fallback face detection",e);}
        return FacePose.none();
    }
    private static FacePose fromDense(List<NormalizedLandmark> p,long at){
        float minX=1,minY=1,maxX=0,maxY=0;
        float[] dense=new float[p.size()*3];
        for(int i=0;i<p.size();i++){
            NormalizedLandmark q=p.get(i);
            dense[i*3]=q.x();dense[i*3+1]=q.y();dense[i*3+2]=q.z();
            // Exclude a few extraneous points with a soft edge margin.
            minX=Math.min(minX,q.x());maxX=Math.max(maxX,q.x());
            minY=Math.min(minY,q.y());maxY=Math.max(maxY,q.y());
        }
        return new FacePose(minX,minY,maxX,maxY,
                at(p,468,33),at(p,473,263),at(p,1,4),at(p,13,0),
                at(p,205,234),at(p,425,454),at(p,152,175),
                at(p,10,151),at(p,105,70),at(p,334,300),dense,at);
    }
    private static PointF at(List<NormalizedLandmark> l,int index,int alternative){
        int n=index<l.size()?index:alternative;
        NormalizedLandmark p=l.get(n);return FacePose.point(p.x(),p.y());
    }
    private static FacePose fromMlKit(Face f,int width,int height,long at){
        Rect b=f.getBoundingBox();
        float l=Math.max(0f,(float)b.left/width),t=Math.max(0f,(float)b.top/height);
        float r=Math.min(1f,(float)b.right/width),bt=Math.min(1f,(float)b.bottom/height);
        float w=r-l,h=bt-t;
        PointF left=landmark(f,FaceLandmark.LEFT_EYE,l+w*.32f,t+h*.40f,width,height);
        PointF right=landmark(f,FaceLandmark.RIGHT_EYE,l+w*.68f,t+h*.40f,width,height);
        PointF nose=landmark(f,FaceLandmark.NOSE_BASE,l+w*.50f,t+h*.59f,width,height);
        PointF mouth=landmark(f,FaceLandmark.MOUTH_BOTTOM,l+w*.50f,t+h*.75f,width,height);
        return new FacePose(l,t,r,bt,left,right,nose,mouth,
                FacePose.point(l+w*.23f,t+h*.62f),
                FacePose.point(l+w*.77f,t+h*.62f),
                FacePose.point(l+w*.5f,bt-h*.04f),
                FacePose.point(l+w*.50f,t+h*.13f),
                FacePose.point(left.x,left.y-h*.13f),
                FacePose.point(right.x,right.y-h*.13f),new float[0],at);
    }
    private static PointF landmark(Face f,int type,float x,float y,int w,int h){
        FaceLandmark landmark=f.getLandmark(type);
        if(landmark==null)return FacePose.point(x,y);
        PointF p=landmark.getPosition();return FacePose.point(p.x/w,p.y/h);
    }
    private static Bitmap toUprightBitmap(ImageProxy image){
        int width=image.getWidth(),height=image.getHeight();
        ImageProxy.PlaneProxy[] planes=image.getPlanes();
        if(planes.length!=3)return null;
        byte[] nv21=new byte[width*height*3/2];
        copyLuma(planes[0],width,height,nv21);
        ByteBuffer v=planes[2].getBuffer(),u=planes[1].getBuffer();
        int vStride=planes[2].getRowStride(),uStride=planes[1].getRowStride();
        int vPixel=planes[2].getPixelStride(),uPixel=planes[1].getPixelStride();
        for(int y=0;y<height/2;y++)for(int x=0;x<width/2;x++){
            int dst=width*height+y*width+x*2;
            int vi=y*vStride+x*vPixel,ui=y*uStride+x*uPixel;
            if(vi<v.limit()&&ui<u.limit()){
                nv21[dst]=v.get(vi);nv21[dst+1]=u.get(ui);
            }
        }
        YuvImage yuv=new YuvImage(nv21,android.graphics.ImageFormat.NV21,
                width,height,null);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        if(!yuv.compressToJpeg(new Rect(0,0,width,height),80,out))return null;
        byte[] encoded=out.toByteArray();
        Bitmap decoded=BitmapFactory.decodeByteArray(encoded,0,encoded.length);
        if(decoded==null)return null;
        int rotation=image.getImageInfo().getRotationDegrees();
        if(rotation==0)return decoded;
        Matrix m=new Matrix();m.postRotate(rotation);
        Bitmap rotated=Bitmap.createBitmap(decoded,0,0,width,height,m,true);
        if(rotated!=decoded)decoded.recycle();
        return rotated;
    }
    private static void copyLuma(ImageProxy.PlaneProxy p,int w,int h,byte[] out){
        ByteBuffer b=p.getBuffer();int row=p.getRowStride(),pixel=p.getPixelStride();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int index=y*row+x*pixel;
            if(index<b.limit())out[y*w+x]=b.get(index);
        }
    }
    @Override public void close(){
        closed=true;
        try{if(landmarker!=null)landmarker.close();}catch(Exception ignored){}
        fallback.close();
    }
}
