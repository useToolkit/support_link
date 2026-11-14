package com.xoda.camera;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Rational;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.*;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

/**
 * New native camera UI and CameraX capture graph.
 * Only the user-approved icon survives the previous XODA implementation.
 */
public final class RebornActivity extends ComponentActivity {
    private static final int WHITE=0xfffafafa,GRAY=0xff929297,INK=0xff242529;
    private static final int ACCENT=0xff65b6d5,LINE=0xffe8e8e9;
    private final ExecutorService workers=Executors.newSingleThreadExecutor();
    private final ExecutorService analysis=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private BeautyState state;
    private ProcessCameraProvider provider;
    private LiveCameraEffect effect;
    private LandmarkTracker tracker;
    private androidx.camera.core.Camera camera;
    private ImageCapture still;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private PreviewView viewfinder;
    private FrameLayout viewfinderFrame;
    private LinearLayout bottom, itemStrip;
    private HorizontalScrollView horizontal;
    private SeekBar slider;
    private TextView sliderLabel,sliderValue,shutter,status,modePhoto,modeVideo;
    private final TextView[] tabs=new TextView[3],looks=new TextView[3];
    private String category=ReferenceControls.BASIC;
    private ReferenceControls.Item selected;
    private FacePose lastFace=FacePose.none();
    private boolean videoMode,front=true,saving,recordingFlag,stopped;
    private boolean usingAudio,photoOriginal;
    private int captureRotation;
    private int timerSeconds,selectedTimer;
    private long lastFrameTime;
    private int capturedPhotos;

    @Override public void onCreate(Bundle stateBundle){
        super.onCreate(stateBundle);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        state=BeautyState.restore(getSharedPreferences("xoda_face_focus",MODE_PRIVATE));
        createScreen();
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)
                ==PackageManager.PERMISSION_GRANTED)openCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA},11);
    }
    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView text(String label,int font,int color){
        TextView t=new TextView(this);t.setText(label);t.setTextColor(color);
        t.setTextSize(font);t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER);return t;
    }
    private void createScreen(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(WHITE);
        setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(11),dp(4),dp(11),dp(4));root.addView(header,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView more=text("•••",20,INK);more.setContentDescription("카메라 설정");
        more.setOnClickListener(v->settingsDialog());
        header.addView(more,new LinearLayout.LayoutParams(dp(42),dp(46)));
        TextView headline=text("얼굴 크기 · 중안부",15,INK);
        headline.setBackground(round(0xfff0f0f1,23));
        headline.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(0,dp(37),1);
        ml.setMargins(dp(12),0,dp(12),0);
        header.addView(headline,ml);
        TextView switchCamera=text("↻",27,INK);
        switchCamera.setContentDescription("전면 후면 카메라 전환");
        switchCamera.setOnClickListener(v->{if(!saving&&recording==null){front=!front;openCamera();}});
        header.addView(switchCamera,new LinearLayout.LayoutParams(dp(44),dp(46)));
        refreshLooks();

        viewfinderFrame=new FrameLayout(this);
        viewfinderFrame.setBackgroundColor(Color.BLACK);
        root.addView(viewfinderFrame,new LinearLayout.LayoutParams(-1,0,1));
        viewfinder=new PreviewView(this);
        viewfinder.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        viewfinder.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        viewfinderFrame.addView(viewfinder,new FrameLayout.LayoutParams(-1,-1));
        status=text("카메라 시작 중",12,Color.WHITE);
        status.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        status.setPadding(dp(12),0,dp(12),0);
        status.setBackground(round(0x88000000,12));
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-2,dp(31),
                Gravity.TOP|Gravity.LEFT);sp.setMargins(dp(12),dp(12),0,0);
        viewfinderFrame.addView(status,sp);
        LinearLayout cameraTools=new LinearLayout(this);
        cameraTools.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,dp(46),Gravity.BOTTOM);
        tp.bottomMargin=dp(7);viewfinderFrame.addView(cameraTools,tp);
        TextView flash=text("⚡",22,Color.WHITE);
        flash.setOnClickListener(v->flash());
        cameraTools.addView(flash,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView timer=text("타이머",12,Color.WHITE);
        timer.setOnClickListener(v->{timerSeconds=timerSeconds==0?3:timerSeconds==3?10:0;
            timer.setText(timerSeconds==0?"타이머":timerSeconds+"초");});
        cameraTools.addView(timer,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView ratio=text("3:4",12,Color.WHITE);
        ratio.setContentDescription("촬영 프레임 3대4");
        cameraTools.addView(ratio,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView compare=text("◐",23,Color.WHITE);
        compare.setOnTouchListener((v,e)->{
            if(effect==null)return false;
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                BeautyState neutral=new BeautyState();neutral.resetAll();
                effect.state(neutral);return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){
                effect.state(state);return true;
            }return true;
        });
        cameraTools.addView(compare,new LinearLayout.LayoutParams(0,dp(42),1));

        bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(WHITE);
        bottom.setPadding(dp(4),dp(8),dp(4),dp(4));
        root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(253)));
        LinearLayout categoryRow=new LinearLayout(this);
        bottom.addView(categoryRow,new LinearLayout.LayoutParams(-1,dp(40)));
        TextView heading=text("정밀 얼굴 비율 · 2가지 조절",14,INK);
        heading.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        categoryRow.addView(heading,new LinearLayout.LayoutParams(-1,-1));
        horizontal=new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        itemStrip=new LinearLayout(this);itemStrip.setGravity(Gravity.CENTER_VERTICAL);
        itemStrip.setPadding(dp(8),0,dp(8),0);
        horizontal.addView(itemStrip);
        bottom.addView(horizontal,new LinearLayout.LayoutParams(-1,dp(99)));

        LinearLayout strength=new LinearLayout(this);strength.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(strength,new LinearLayout.LayoutParams(-1,dp(39)));
        sliderLabel=text("항목을 선택해 주세요",12,GRAY);
        strength.addView(sliderLabel,new LinearLayout.LayoutParams(dp(124),dp(38)));
        slider=new SeekBar(this);slider.setMax(100);
        slider.setProgressTintList(ColorStateList.valueOf(ACCENT));
        slider.setThumbTintList(ColorStateList.valueOf(ACCENT));
        slider.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        slider.setVisibility(View.INVISIBLE);
        strength.addView(slider,new LinearLayout.LayoutParams(0,dp(36),1));
        sliderValue=text("",12,INK);
        strength.addView(sliderValue,new LinearLayout.LayoutParams(dp(37),dp(36)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int p,boolean user){
                if(user&&selected!=null){state.set(selected.key,p/100f);
                    sliderValue.setText(String.valueOf(p));changed();refreshDots();}
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        LinearLayout capture=new LinearLayout(this);capture.setGravity(Gravity.CENTER);
        bottom.addView(capture,new LinearLayout.LayoutParams(-1,dp(65)));
        TextView collapse=text("⌄",28,GRAY);collapse.setOnClickListener(v->{
            horizontal.setVisibility(horizontal.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
        });capture.addView(collapse,new LinearLayout.LayoutParams(0,-1,1));
        TextView importPhoto=text("▧",26,GRAY);
        importPhoto.setContentDescription("사진 불러오기");
        importPhoto.setOnClickListener(v->importPhoto());
        capture.addView(importPhoto,new LinearLayout.LayoutParams(0,-1,1));
        shutter=text("◯",53,ACCENT);this.shutter=shutter;
        shutter.setContentDescription("사진 촬영 또는 영상 녹화");
        shutter.setOnClickListener(v->shutter());
        capture.addView(shutter,new LinearLayout.LayoutParams(0,-1,2));
        TextView styles=text("스타일",12,GRAY);
        styles.setVisibility(View.GONE);
        capture.addView(styles,new LinearLayout.LayoutParams(0,-1,1));
        TextView reset=text("초기화",12,GRAY);
        reset.setOnClickListener(v->{
            new AlertDialog.Builder(this).setTitle("뷰티 초기화")
                .setMessage("개별 보정값을 원본 초기 설정으로 되돌릴까요?")
                .setNegativeButton("취소",null)
                .setPositiveButton("초기화",(d,w)->{
                    state.resetBeauty();changed();refreshItems();
                }).show();
        });
        capture.addView(reset,new LinearLayout.LayoutParams(0,-1,1));

        LinearLayout bottomModes=new LinearLayout(this);root.addView(bottomModes,
                new LinearLayout.LayoutParams(-1,dp(42)));
        modePhoto=text("사진",14,ACCENT);modeVideo=text("영상",14,GRAY);
        modePhoto.setOnClickListener(v->setVideoMode(false));
        modeVideo.setOnClickListener(v->setVideoMode(true));
        bottomModes.addView(modePhoto,new LinearLayout.LayoutParams(0,-1,1));
        bottomModes.addView(modeVideo,new LinearLayout.LayoutParams(0,-1,1));
        refreshItems();
        selectItem(ReferenceControls.find("facesize"));
    }
    private android.graphics.drawable.GradientDrawable round(int color,int dpRadius){
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);bg.setCornerRadius(dp(dpRadius));return bg;
    }
    private void refreshLooks(){
        String[] labels={"고화질","기본","아이폰"};
        for(int i=0;i<looks.length;i++)if(looks[i]!=null){
            boolean active=labels[i].equals(state.mode);
            looks[i].setBackground(active?round(Color.WHITE,19):round(0xfff0f0f1,19));
            looks[i].setTextColor(active?INK:GRAY);
        }
    }
    private void chooseCategory(int idx){
        String[] names={ReferenceControls.BASIC,ReferenceControls.ADVANCED,ReferenceControls.MAKEUP};
        category=names[idx];
        for(int i=0;i<3;i++)tabs[i].setTextColor(i==idx?ACCENT:GRAY);
        selected=null;sliderLabel.setText("항목을 선택해 주세요");slider.setVisibility(View.INVISIBLE);
        sliderValue.setText("");refreshItems();
    }
    private List<ReferenceControls.Item> visibleItems(){
        return Arrays.asList(ReferenceControls.find("facesize"),
                ReferenceControls.find("mfeyebrownose"));
    }
    private void refreshItems(){
        itemStrip.removeAllViews();
        List<ReferenceControls.Item> items=visibleItems();
        for(ReferenceControls.Item item:items){
            LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            FaceGlyph glyph=new FaceGlyph(item.key,item.category);
            cell.addView(glyph,new LinearLayout.LayoutParams(dp(48),dp(47)));
            TextView title=text(item.title,11,INK);
            title.setMaxLines(1);title.setSingleLine(true);
            cell.addView(title,new LinearLayout.LayoutParams(-1,dp(26)));
            TextView dot=text(state.get(item.key)>.0001f?"•":"·",11,
                    state.get(item.key)>.0001f?ACCENT:LINE);
            dot.setTag("dot");
            cell.addView(dot,new LinearLayout.LayoutParams(-1,dp(12)));
            cell.setContentDescription(item.title+(item.approximation?" · 2D 근사":""));
            cell.setOnClickListener(v->selectItem(item));
            itemStrip.addView(cell,new LinearLayout.LayoutParams(dp(78),dp(92)));
        }
        horizontal.scrollTo(0,0);
    }
    private void refreshDots(){
        List<ReferenceControls.Item> items=visibleItems();
        for(int i=0;i<items.size()&&i<itemStrip.getChildCount();i++){
            LinearLayout cell=(LinearLayout)itemStrip.getChildAt(i);
            TextView dot=(TextView)cell.getChildAt(2);
            float v=state.get(items.get(i).key);
            dot.setText(v>.0001f?"•":"·");
            dot.setTextColor(v>.0001f?ACCENT:LINE);
        }
    }
    private void selectItem(ReferenceControls.Item item){
        selected=item;
        sliderLabel.setText(item.title);
        slider.setVisibility(View.VISIBLE);
        int value=Math.round(state.get(item.key)*100f);
        slider.setProgress(value);slider.setContentDescription(item.title+" 강도");
        sliderValue.setText(String.valueOf(value));
        for(int i=0;i<itemStrip.getChildCount();i++)
            itemStrip.getChildAt(i).setBackground(
                itemStrip.getChildAt(i).getContentDescription().toString().startsWith(item.title)?
                    round(0xffe9f4f7,15):null);
    }
    private void chooseStyles(){
        List<ReferenceControls.Item> styles=ReferenceControls.group(ReferenceControls.SHAPES);
        String[] names=new String[styles.size()];
        for(int i=0;i<styles.size();i++)names[i]=styles.get(i).title;
        new AlertDialog.Builder(this).setTitle("얼굴 스타일").setItems(names,(d,which)->{
            state.applyStyle(styles.get(which).key);changed();refreshItems();
        }).show();
    }
    private void changed(){
        if(effect!=null)effect.state(state);
        state.persist(getSharedPreferences("xoda_face_focus",MODE_PRIVATE));
    }
    private void setVideoMode(boolean enabled){
        if(recording!=null||saving)return;
        videoMode=enabled;
        modePhoto.setTextColor(!enabled?ACCENT:GRAY);
        modeVideo.setTextColor(enabled?ACCENT:GRAY);
        shutter.setText(enabled?"⬤":"◯");
        openCamera();
    }
    private void openCamera(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            return;
        if(provider==null){
            ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
            future.addListener(()->{
                try{provider=future.get();bindCamera();}
                catch(Exception error){status.setText("카메라 열기 실패");showFailure(error);}
            },ContextCompat.getMainExecutor(this));
        }else bindCamera();
    }
    private void bindCamera(){
        if(provider==null||stopped)return;
        provider.unbindAll();
        if(effect!=null)effect.close();
        if(tracker!=null)tracker.close();
        effect=new LiveCameraEffect();
        effect.state(state);
        effect.onError(error->runOnUiThread(()->showFailure(error)));
        tracker=new LandmarkTracker(this,pose->{
            lastFace=pose;LiveCameraEffect renderer=effect;
            if(renderer!=null)renderer.face(pose);
            long now=SystemClock.elapsedRealtime();
            if(now-lastFrameTime>1000L){
                lastFrameTime=now;
                ui.post(()->status.setText(pose.valid?"얼굴 추적 · 실시간 보정":"얼굴을 화면에 맞춰 주세요"));
            }
        });
        try{
            int orientation=viewfinder.getDisplay()!=null?
                    viewfinder.getDisplay().getRotation():Surface.ROTATION_0;
            Preview preview=new Preview.Builder().setTargetRotation(orientation).build();
            preview.setSurfaceProvider(viewfinder.getSurfaceProvider());
            ImageAnalysis analyzer=new ImageAnalysis.Builder()
                    .setTargetRotation(orientation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(new android.util.Size(640,480)).build();
            analyzer.setAnalyzer(analysis,tracker);
            UseCaseGroup.Builder group=new UseCaseGroup.Builder()
                    .setViewPort(new ViewPort.Builder(new Rational(3,4),orientation)
                            .setScaleType(ViewPort.FILL_CENTER).build())
                    .addUseCase(preview).addUseCase(analyzer);
            if(videoMode){
                Recorder recorder=new Recorder.Builder().setQualitySelector(
                        QualitySelector.from(Quality.FHD,
                                FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD))).build();
                videoCapture=VideoCapture.withOutput(recorder);
                still=null;group.addUseCase(videoCapture);
                group.addEffect(new CameraGpuEffect(CameraEffect.PREVIEW|CameraEffect.VIDEO_CAPTURE,
                        effect.executor(),effect,err->showFailure(err)));
            } else {
                still=new ImageCapture.Builder()
                        .setTargetRotation(orientation)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                videoCapture=null;group.addUseCase(still);
                group.addEffect(new CameraGpuEffect(CameraEffect.PREVIEW,
                        effect.executor(),effect,err->showFailure(err)));
            }
            camera=provider.bindToLifecycle(this,front?CameraSelector.DEFAULT_FRONT_CAMERA:
                    CameraSelector.DEFAULT_BACK_CAMERA,group.build());
            status.setText(front?"전면 카메라":"후면 카메라");
        }catch(Exception error){showFailure(error);}
    }
    private static final class CameraGpuEffect extends CameraEffect {
        CameraGpuEffect(int flags,Executor executor,SurfaceProcessor effect,
                androidx.core.util.Consumer<Throwable> errors){
            super(flags,executor,effect,errors);
        }
    }
    private void flash(){
        if(camera==null||videoMode){toast("현재 촬영 모드에서 플래시를 사용할 수 없습니다.");return;}
        if(!camera.getCameraInfo().hasFlashUnit()){toast("이 카메라에는 플래시가 없습니다.");return;}
        int mode=still.getFlashMode();
        still.setFlashMode(mode==ImageCapture.FLASH_MODE_OFF?
                ImageCapture.FLASH_MODE_ON:ImageCapture.FLASH_MODE_OFF);
        toast(still.getFlashMode()==ImageCapture.FLASH_MODE_ON?"플래시 켜짐":"플래시 꺼짐");
    }
    private void shutter(){
        if(saving)return;
        if(recording!=null){recording.stop();return;}
        if(timerSeconds>0){
            int seconds=timerSeconds;timerSeconds=0;
            status.setText(seconds+"초 후 촬영");
            ui.postDelayed(()->captureNow(),seconds*1000L);
        }else captureNow();
    }
    private void captureNow(){if(videoMode)startVideo();else takePhoto();}
    private void takePhoto(){
        if(still==null){toast("카메라 준비 중");return;}
        saving=true;shutter.setEnabled(false);status.setText("사진 보정 및 저장 중");
        still.takePicture(workers,new ImageCapture.OnImageCapturedCallback(){
            @Override public void onCaptureSuccess(@NonNull ImageProxy image){
                Bitmap source=null,output=null;
                try{
                    if(image.getFormat()!=android.graphics.ImageFormat.JPEG)
                        throw new IllegalStateException("JPEG 사진 포맷이 아닙니다");
                    ByteBuffer data=image.getPlanes()[0].getBuffer();
                    byte[] bytes=new byte[data.remaining()];data.get(bytes);
                    source=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                    if(source==null)throw new IllegalStateException("사진 디코딩 실패");
                    int rotation=image.getImageInfo().getRotationDegrees();
                    if(rotation!=0){
                        Matrix matrix=new Matrix();matrix.postRotate(rotation);
                        Bitmap upright=Bitmap.createBitmap(source,0,0,
                                source.getWidth(),source.getHeight(),matrix,true);
                        if(upright!=source)source.recycle();
                        source=upright;
                    }
                    // A real still GPU export follows the same mesh and shader recipe.
                    try(PhotoRenderer renderer=new PhotoRenderer()){
                        output=renderer.render(source,state,lastFace);
                    }
                    Uri uri=savePhoto(output);
                    runOnUiThread(()->{saving=false;shutter.setEnabled(true);
                        status.setText("사진 저장 완료 · Pictures/Xoda");
                        toast("사진을 저장했습니다");});
                }catch(Throwable error){
                    runOnUiThread(()->{saving=false;shutter.setEnabled(true);showFailure(error);});
                }finally{
                    if(output!=null)output.recycle();
                    if(source!=null)source.recycle();
                    image.close();
                }
            }
            @Override public void onError(@NonNull ImageCaptureException error){
                runOnUiThread(()->{saving=false;shutter.setEnabled(true);showFailure(error);});
            }
        });
    }
    private Uri savePhoto(Bitmap b)throws Exception{
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,
                "Xoda_"+System.currentTimeMillis()+".jpg");
        values.put(MediaStore.MediaColumns.MIME_TYPE,"image/jpeg");
        if(Build.VERSION.SDK_INT>=29){
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/Xoda/");
            values.put(MediaStore.MediaColumns.IS_PENDING,1);
        }
        Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        if(uri==null)throw new IOException("MediaStore image URI unavailable");
        try(OutputStream out=getContentResolver().openOutputStream(uri)){
            if(out==null||!b.compress(Bitmap.CompressFormat.JPEG,94,out))
                throw new IOException("JPEG write failed");
        }catch(Exception error){getContentResolver().delete(uri,null,null);throw error;}
        if(Build.VERSION.SDK_INT>=29){
            ContentValues committed=new ContentValues();
            committed.put(MediaStore.MediaColumns.IS_PENDING,0);
            getContentResolver().update(uri,committed,null,null);
        }
        return uri;
    }
    private void startVideo(){
        if(videoCapture==null){toast("영상 카메라 준비 중");return;}
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)
                !=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},12);
            return;
        }
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,
                "Xoda_"+System.currentTimeMillis()+".mp4");
        values.put(MediaStore.MediaColumns.MIME_TYPE,"video/mp4");
        if(Build.VERSION.SDK_INT>=29)
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,"Movies/Xoda/");
        MediaStoreOutputOptions output=new MediaStoreOutputOptions.Builder(
                getContentResolver(),MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(values).build();
        PendingRecording pending=videoCapture.getOutput().prepareRecording(this,output);
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)
                ==PackageManager.PERMISSION_GRANTED)pending=pending.withAudioEnabled();
        recording=pending.start(ContextCompat.getMainExecutor(this),event->{
            if(event instanceof VideoRecordEvent.Start){
                shutter.setText("■");status.setText("● 영상 촬영 중");
            }else if(event instanceof VideoRecordEvent.Finalize){
                VideoRecordEvent.Finalize done=(VideoRecordEvent.Finalize)event;
                recording=null;shutter.setText("⬤");
                if(done.hasError())showFailure(
                        new IllegalStateException("영상 저장 실패: "+done.getError()));
                else{status.setText("영상 저장 완료 · Movies/Xoda");toast("영상 저장 완료");}
            }
        });
    }
    private void importPhoto(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent,221);
    }
    @Override protected void onActivityResult(int code,int result,Intent intent){
        super.onActivityResult(code,result,intent);
        if(code!=221||result!=RESULT_OK||intent==null||intent.getData()==null)return;
        Uri uri=intent.getData();
        workers.execute(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                Bitmap source=BitmapFactory.decodeStream(in);
                if(source==null)throw new IOException("이미지 형식을 읽을 수 없습니다");
                Bitmap edited;
                try(PhotoRenderer renderer=new PhotoRenderer()){
                    edited=renderer.render(source,state,lastFace);
                }
                source.recycle();
                savePhoto(edited);edited.recycle();
                runOnUiThread(()->toast("사진 편집 및 저장 완료"));
            }catch(Throwable error){runOnUiThread(()->showFailure(error));}
        });
    }
    private void settingsDialog(){
        new AlertDialog.Builder(this).setTitle("Xoda 설정")
                .setMessage("새로 제작한 독립형 뷰티 카메라 개발판입니다.\n"
                        +"3D 표기 항목은 독립적인 2D 근사이며, SODA 상용 3D 알고리즘은 아닙니다.\n"
                        +"촬영·얼굴 인식은 기기에서 수행됩니다. 광고와 로그인 기능은 없습니다.")
                .setPositiveButton("확인",null).show();
    }
    private void showFailure(Throwable error){
        if(stopped)return;
        String message=error.getMessage();
        if(message==null||message.length()==0)message=error.getClass().getSimpleName();
        status.setText("카메라 처리 오류");
        new AlertDialog.Builder(this).setTitle("촬영 또는 보정 오류")
                .setMessage(message).setPositiveButton("확인",null).show();
    }
    private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();}
    @Override public void onRequestPermissionsResult(int req,@NonNull String[] p,@NonNull int[] results){
        super.onRequestPermissionsResult(req,p,results);
        if(req==11&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)openCamera();
        else if(req==11)toast("카메라 권한이 필요합니다.");
        if(req==12){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)
                startVideo();
            else toast("마이크 권한 없이 영상 녹화를 사용할 수 없습니다.");
        }
    }
    @Override protected void onStop(){
        super.onStop();state.persist(getSharedPreferences("xoda_face_focus",MODE_PRIVATE));
    }
    @Override protected void onDestroy(){
        stopped=true;
        if(recording!=null)recording.stop();
        if(provider!=null)provider.unbindAll();
        if(effect!=null)effect.close();
        if(tracker!=null)tracker.close();
        workers.shutdown();analysis.shutdown();
        super.onDestroy();
    }

    /** Original in-app line art. Never loads, reproduces or extracts SODA icons. */
    private final class FaceGlyph extends View {
        final Paint paint=new Paint(3); final String name;final String category;
        FaceGlyph(String name,String category){super(RebornActivity.this);
            this.name=name;this.category=category;
            setContentDescription(name);
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float cx=getWidth()*.5f,cy=getHeight()*.48f;
            c.save();c.translate(cx,cy);
            float k=dp(1);c.scale(k,k);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(INK);
            if(name.contains("nose")||name.equals("narrow")||name.equals("alar")||name.equals("tip")){
                Path path=new Path();path.moveTo(0,-12);path.cubicTo(-1,-2,-6,7,0,9);
                path.cubicTo(6,9,7,5,9,5);c.drawPath(path,paint);
            } else if(name.contains("eye")||name.equals("enlarge")||name.equals("span")||
                    name.equals("clarity")||name.equals("pupil")){
                c.drawOval(-14,-8,14,8,paint);c.drawCircle(0,0,4.5f,paint);
            } else if(name.contains("mouth")||name.contains("lip")||name.equals("smile")||
                    name.equals("teeth")){
                Path path=new Path();path.moveTo(-13,0);
                path.cubicTo(-7,-7,-2,-3,0,-4);path.cubicTo(5,-7,10,-5,13,0);
                path.cubicTo(5,8,-6,8,-13,0);c.drawPath(path,paint);
            }else if(name.equals("smooth")||name.equals("balance")||name.equals("clear")){
                c.drawCircle(0,0,13,paint);
                for(int i=0;i<7;i++)c.drawPoint((i%3-1)*5f,(i/3-1)*5f,paint);
            }else{
                c.drawOval(-10,-13,10,13,paint);
                c.drawArc(-5,2,5,8,0,150,false,paint);
            }
            c.restore();
        }
    }
}
