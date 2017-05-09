package tridoo.ksiegowy;


import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private int mCaptureState;
    private TextureView mTextureView;
    private ToggleButton btnPreview;
    private TextRecognizer textRecognizer;
    private EditText textBlockContent;
    ContentResolver contentResolver;
    private float prPodDoch, prVat, prVatProduktu;
    private Dao dao;
    int SX=2;
    int SY=4;

    private TextureView.SurfaceTextureListener mSurfaceTextureListener ;
    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraDeviceStateCallback ;
    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private String mCameraId;
    private Size mPreviewSize;
    private Size mImageSize;
    private ImageReader mImageReader;
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener ;

    private CameraCaptureSession mPreviewCaptureSession;
    private CameraCaptureSession.CaptureCallback mPreviewCaptureCallback ;

    private CaptureRequest.Builder mCaptureRequestBuilder;

    private Button mStillImageButton;

    private File mImageFolder;
    private String mImageFileName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ksiegowy);

        init();
        setupButtons();

        createImageFolder();

        if (dao.isCzySaDane()){
            odczytajZapisaneDane();
            ustawParametry();
            findViewById(R.id.lay_parametry).setVisibility(View.GONE);
        }else{
            findViewById(R.id.lay_zestawienie).setVisibility(View.GONE);
        }


        //showAds();
    }

    private void init(){
        mCaptureState = Config.STATE_PREVIEW;

        mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Matrix matrix = new Matrix();

                matrix.setScale(SX, SY,width/2,height/2); //todo jaki zoom?
                mTextureView.setTransform(matrix);

                setupCamera(width, height);
                connectCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        };

        mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                mCameraDevice = camera;
                startPreview();
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                camera.close();
                mCameraDevice = null;
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                camera.close();
                mCameraDevice = null;
            }
        };

        mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                mBackgroundHandler.post(new ImageSaver(reader.acquireLatestImage()));
            }
        };

        mPreviewCaptureCallback = new CameraCaptureSession.CaptureCallback() {
                    private void process(CaptureResult captureResult) {
                        switch (mCaptureState) {
                            case Config.STATE_PREVIEW:
                                // Do nothing
                                break;
                            case Config.STATE_WAIT_LOCK:
                                mCaptureState = Config.STATE_PREVIEW;
                                Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                                if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                        afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                    Toast.makeText(getApplicationContext(), "AF Locked!", Toast.LENGTH_SHORT).show();
                                    startStillCaptureRequest();
                                }
                                break;
                        }
                    }

                    @Override
                    public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);

                        process(result);
                    }
                };


        contentResolver=this.getContentResolver();
        dao=new Dao(getApplicationContext());

        mTextureView = (TextureView) findViewById(R.id.textureView);

        textRecognizer=new TextRecognizer.Builder(getApplicationContext()).build();
        textRecognizer.setProcessor(new Detector.Processor<TextBlock>() { //potrzebne?
            @Override
            public void release() {
            }

            @Override
            public void receiveDetections(Detector.Detections<TextBlock> detections) {
                final SparseArray<TextBlock> items=detections.getDetectedItems();
/*                if (items.size() != 0) {
                    textBlockContent.post(new Runnable() {
                        @Override
                        public void run() {
                            StringBuilder value = new StringBuilder();
                            for (int i = 0; i < items.size(); ++i) {
                                TextBlock item = items.valueAt(i);
                                value.append(item.getValue());
                                value.append("\n");
                            }
                            //update text block content to TextView
                            textBlockContent.setText(value.toString());
                        }
                    });
                }*/
            }
        });

        TextWatcher watcher= new TextWatcher() {
            public void afterTextChanged(Editable s) {
                if (!textBlockContent.getText().toString().equals("")) {
                    oblicz();
                }
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //Do something or nothing.
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //Do something or nothing
            }
        };

        textBlockContent = (EditText) findViewById(R.id.e_brutto);
        textBlockContent.addTextChangedListener(watcher);
    }

    private void setupButtons(){
        mStillImageButton = (Button) findViewById(R.id.btn_skanuj);
        mStillImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkWriteStoragePermission();
                lockFocus();
            }
        });

        btnPreview =(ToggleButton) findViewById(R.id.btn_podglad);
        btnPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (((ToggleButton)v).isChecked()){
                    closeCamera();
                    stopBackgroundThread();
                    findViewById(R.id.zaslona).setVisibility(View.VISIBLE);
                }else{
                    startBackgroundThread();
                    setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
                    connectCamera();
                    findViewById(R.id.zaslona).setVisibility(View.INVISIBLE);
                }
            }
        });

        ((TextView)(findViewById(R.id.btn_edit))).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.lay_parametry).setVisibility(View.VISIBLE);
                findViewById(R.id.lay_zestawienie).setVisibility(View.GONE);
            }
        });


        ((Button)(findViewById(R.id.btn_up))).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.lay_parametry).setVisibility(View.GONE);
                findViewById(R.id.lay_zestawienie).setVisibility(View.VISIBLE);
                ustawParametry();
            }
        });

        RadioGroup grupa1=(RadioGroup)findViewById(R.id.group1);
        RadioGroup grupa2=(RadioGroup)findViewById(R.id.group2);
        RadioGroup grupa3=(RadioGroup)findViewById(R.id.group3);
        grupa1.setOnCheckedChangeListener(new CheckedChangeListener());
        grupa2.setOnCheckedChangeListener(new CheckedChangeListener());
        grupa3.setOnCheckedChangeListener(new CheckedChangeListener());

    }

    private void odczytajWybraneParametry(){
        prVat=podajProcentVatOdliczanego();
        prPodDoch=podajProcentPodDoch();

    }

    private void oblicz(){
        float odliczenia;
        String bruttoS=textBlockContent.getText().toString();
        float brutto = bruttoS.isEmpty() ? 0 : Float.valueOf(bruttoS.replace(",", "."));
        float prVatProduktu=podajProcentVatProduktu();
        float vatProduktu=brutto-brutto/(1+prVatProduktu);
        float vatOdliczony=vatProduktu*prVat;
        float kwotaPodDoch=(brutto-vatOdliczony)*prPodDoch;

        odliczenia=vatOdliczony+kwotaPodDoch;
        ((TextView)findViewById(R.id.tv_odliczenia)).setText(String.format("%1$,.2f", odliczenia));
        ((TextView)findViewById(R.id.tv_koszt)).setText(String.format("%1$,.2f",brutto-odliczenia));

    }

    private float podajProcentVatProduktu(){
        if(((RadioButton)findViewById(R.id.rb_p_0)).isChecked()) return 0f;
        if(((RadioButton)findViewById(R.id.rb_p_5)).isChecked()) return 0.05f;
        if(((RadioButton)findViewById(R.id.rb_p_8)).isChecked()) return 0.08f;
        return 0.23f;
    }

    private float podajProcentVatOdliczanego(){
        if(((RadioButton)findViewById(R.id.rb_0)).isChecked()) return 0f;
        if(((RadioButton)findViewById(R.id.rb_50)).isChecked()) return 0.5f;
        return 1;
    }

    private float podajProcentPodDoch(){
        if(((RadioButton)findViewById(R.id.rb_18)).isChecked()) return 0.18f;
        else return 0.19f;
    }

    private void showAds(){
        AdView mAdView = (AdView) findViewById(R.id.baner);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
    }

    private void odczytajZapisaneDane(){
        prPodDoch= (float) (dao.getPrPodDoch()*0.01);
        prVat= (float) (dao.getPrVat()*0.01);
    }

    private void ustawParametry(){
        TextView tvPD=(TextView)findViewById(R.id.tv_pd);
        TextView tvVat=(TextView)findViewById(R.id.tv_vat);
        tvPD.setText(prPodDoch*100+"%");
        tvVat.setText(prVat*100+"%");

        if (prPodDoch == 0.18f) {
            ((RadioButton) findViewById(R.id.rb_18)).setChecked(true);
        } else {
            ((RadioButton) findViewById(R.id.rb_19)).setChecked(true);
        }

        if (prVat == 0f) {
            ((RadioButton) findViewById(R.id.rb_0)).setChecked(true);

        } else if (prVat == 0.5f) {
            ((RadioButton) findViewById(R.id.rb_50)).setChecked(true);

        } else  {
            ((RadioButton) findViewById(R.id.rb_100)).setChecked(true);
        }
    }

    private void zapiszParametry(){
        dao.zapiszDane((int)(prVat*100),(int)(prPodDoch*100));
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
            connectCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Config.REQUEST_CAMERA_PERMISSION_RESULT) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Application will not run without camera services", Toast.LENGTH_SHORT).show();
            }
            if (grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Application will not have audio on record", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == Config.REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                        "Permission successfully granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "App needs to save video to run", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocas) {
        super.onWindowFocusChanged(hasFocas);
        View decorView = getWindow().getDecorView();
        if (1==11){ //todo
        //if (hasFocas) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );
        }
    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                mPreviewSize = prevSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                mImageSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG), width, height);
                mImageReader = ImageReader.newInstance(mImageSize.getWidth(), mImageSize.getHeight(), ImageFormat.JPEG, 10);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
                } else {
                    if (shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                        Toast.makeText(this,
                                "Video app required access to camera", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO
                    }, Config.REQUEST_CAMERA_PERMISSION_RESULT);
                }

            } else {
                cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            mPreviewCaptureSession = session;
                            try {
                                mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),
                                        null, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {

                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startStillCaptureRequest() {
        try {

            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);

            CameraCaptureSession.CaptureCallback stillCaptureCallback = new
                    CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);

                            try {
                                createImageFileName();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    };

                mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("Camera2VideoImage");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private static Size prevSize(Size[] choices, int width, int height){
        for (int i=1;i<choices.length;i++){ //0 na pewno za duze, pasuje tylko na fullscreen
            if(choices[i].getWidth()<width)return choices[i-1];
        }
        return  choices[0];
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            if(option.getHeight()>height) return choices[2]; //todo 0,1 nie dziala zbadać

            if (option.getHeight() >= option.getWidth() * height / width &&   option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else {
            return choices[0];
        }
    }


    private void createImageFolder() { //todo potrzebne?
        File imageFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mImageFolder = new File(imageFile, "camera2VideoImage");
        if (!mImageFolder.exists()) {
            mImageFolder.mkdirs();
        }
    }

    private File createImageFileName() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prepend = "IMAGE_";// + timestamp + "_";
        File imageFile = File.createTempFile(prepend, ".jpg", mImageFolder);
        mImageFileName = imageFile.getAbsolutePath();
        return imageFile;
    }

    private void checkWriteStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {

            } else {
                if (shouldShowRequestPermissionRationale(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, "app needs to be able to save videos", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, Config.REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT);
            }
        } else {

        }
    }


    private void lockFocus() {
        if(mBackgroundHandler==null)return;
        mCaptureState = Config.STATE_WAIT_LOCK;
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), mPreviewCaptureCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private static class CompareSizeByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) (lhs.getWidth() * lhs.getHeight()) -
                    (long) (rhs.getWidth() * rhs.getHeight()));
        }
    }

    private class ImageSaver implements Runnable {

        private final Image mImage;

        public ImageSaver(Image image) {
            mImage = image;
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            //Bitmap photoReducedSizeBitmp=null;
            Bitmap tmp=BitmapFactory.decodeByteArray(bytes,0,bytes.length);

/*            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(mImageFileName);

                //cropBitmap(tmp);
                //tmp.compress(Bitmap.CompressFormat.JPEG, 85, fileOutputStream);
                fileOutputStream.write(bytes);
                //photoReducedSizeBitmp=getBmp(mImage);

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                File file = new File(mImageFileName);

                Intent mediaStoreUpdateIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);

                mediaStoreUpdateIntent.setData(Uri.fromFile(file));
                sendBroadcast(mediaStoreUpdateIntent);
                boolean deleted = file.delete();

                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }*/

            Frame frame = new Frame.Builder().setBitmap(cropBitmap(tmp)).build();
            //Frame frame = new Frame.Builder().setBitmap(photoReducedSizeBitmp).build();
            SparseArray<TextBlock> textBlocks = textRecognizer.detect(frame);
            String text="";
            for(int i = 0; i < textBlocks.size(); i++) {
                if (textBlocks.get(i) == null) continue;
                text += textBlocks.get(i).getValue();
            }

            final String regExp = "[0-9]+([,.][0-9]{1,2})?";
            final Pattern pattern = Pattern.compile(regExp);
            Matcher matcher = pattern.matcher(text);
            System.out.println(text);
            //if (1==1){
            if(matcher.find()){
                final String finalText = matcher.group(0);
                //final String finalText = text;
                textBlockContent.post(new Runnable() {
                    @Override
                    public void run() {
                        textBlockContent.setText(finalText);
                    }
                });

            }
            }
        }

    private Bitmap getBmp( Image image){
            Bitmap result;
            int targetImageViewWidth = image.getWidth();
            int targetImageViewHeight = image.getHeight();

            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(mImageFileName, bmOptions);
            int cameraImageWidth = bmOptions.outWidth;
            int cameraImageHeight = bmOptions.outHeight;

            int scaleFactor = Math.min(cameraImageWidth/targetImageViewWidth, cameraImageHeight/targetImageViewHeight);
            bmOptions.inSampleSize = scaleFactor;
            bmOptions.inJustDecodeBounds = false;

            Bitmap photoReducedSizeBitmp = BitmapFactory.decodeFile(mImageFileName, bmOptions);

            int width=photoReducedSizeBitmp.getWidth();
            int height=photoReducedSizeBitmp.getHeight();

            int scaledWidth = width/SX ;
            int scaledHeight = height / SY;

            int dx = (int)((width-scaledWidth)*0.5);
            int dy =(int)((height-scaledHeight)*0.5);
            dx=dx+scaledWidth/4;//wycinek podgladu

            result=Bitmap.createBitmap(photoReducedSizeBitmp, dx,dy, scaledWidth/2, scaledHeight/2); // rozpoznanie gornej polowki

            return result;
        }

    private Bitmap cropBitmap(Bitmap bitmap){
        Bitmap result;
        int width=bitmap.getWidth();
        int height=bitmap.getHeight();

        int scaledWidth = width/SX ;
        int scaledHeight = height / SY;

        int dx = (int)((width-scaledWidth)*0.5);
        int dy =(int)((height-scaledHeight)*0.5);
        dx=dx+scaledWidth/4;//wycinek podgladu

        result=Bitmap.createBitmap(bitmap, dx,dy, scaledWidth/2, scaledHeight/2); // rozpoznanie gornej polowki

        return result;
    }

    private class CheckedChangeListener implements RadioGroup.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            odczytajWybraneParametry();
            oblicz();
            zapiszParametry();
        }
    }
}
