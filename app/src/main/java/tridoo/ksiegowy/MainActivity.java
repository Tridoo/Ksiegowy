package tridoo.ksiegowy;


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
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private int mCaptureState;
    private TextureView textureView;
    private TextRecognizer textRecognizer;
    private float incomeTax, vatRelief, articleVat;
    private Dao dao;

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

    private ScreenController screenController;
    private Context context;
    private boolean isCameraPermission;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ksiegowy);
        context=getApplicationContext();

        init();
        screenController=new ScreenController(this);
        screenController.setupButtons();
        screenController.keyboardObserver(context);

        if (dao.isTaxesSaved()){
            readSavedParameters();
            screenController.setupParameters();
            screenController.getLayParameters().setVisibility(View.GONE);
        }else{
            //todo ustawic podsumowanie, po zwinieciu sa 0%
            screenController.getLaySummary().setVisibility(View.GONE);
        }

        showAds();
    }

    private void init(){
        mCaptureState = Config.STATE_PREVIEW;

        mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Matrix matrix = new Matrix();
                matrix.setScale(Config.SCALE_X, Config.SCALE_Y,width/2,height/2);
                textureView.setTransform(matrix);

                setupCamera(width, height);
                if (isCameraPermission) connectCamera();
                screenController.resizeElements(width, height);
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
                                    Toast.makeText(context, getString(R.string.scanning), Toast.LENGTH_SHORT).show();
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

        dao=new Dao(context);

        textureView = (TextureView) findViewById(R.id.textureView);
        textRecognizer=new TextRecognizer.Builder(context).build();
        textRecognizer.setProcessor(new Detector.Processor<TextBlock>() { //potrzebne?
            @Override
            public void release() {
            }

            @Override
            public void receiveDetections(Detector.Detections<TextBlock> detections) {
                final SparseArray<TextBlock> items=detections.getDetectedItems();
/*                if (items.size() != 0) {
                    eGross.post(new Runnable() {
                        @Override
                        public void run() {
                            StringBuilder value = new StringBuilder();
                            for (int i = 0; i < items.size(); ++i) {
                                TextBlock item = items.valueAt(i);
                                value.append(item.getValue());
                                value.append("\n");
                            }
                            //update text block content to TextView
                            eGross.setText(value.toString());
                        }
                    });
                }*/
            }
        });
    }

    public void calculate(){
        float reliefAmount;
        float grossAmount = screenController.getGross();
        float vatAmount=grossAmount-grossAmount/(1+ screenController.getArticleVat());
        float vatReliefAmount=vatAmount* screenController.getVatRelief();
        float incomeTaxAmount=(grossAmount-vatReliefAmount)* screenController.getIncomeTax();

        reliefAmount=vatReliefAmount+incomeTaxAmount;
        ((TextView)findViewById(R.id.tv_relief)).setText(String.format("%1$,.2f", reliefAmount));
        ((TextView)findViewById(R.id.tv_cost)).setText(String.format("%1$,.2f",grossAmount-reliefAmount));

    }

    private void showAds(){
        AdView mAdView = (AdView) findViewById(R.id.baner);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
    }

    private void readSavedParameters(){
        incomeTax = (float) (dao.getIncomeTax()*0.01);
        vatRelief = (float) (dao.getVat()*0.01);
    }

    public void saveParameters(){
        dao.saveTaxes((int)(screenController.getVatRelief() *100),(int)(screenController.getIncomeTax() *100));
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkPermisionns();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();

        if (textureView.isAvailable()) {
            setupCamera(textureView.getWidth(), textureView.getHeight());
            if (isCameraPermission) connectCamera(); //todo sprawdzic czy kamera nie jest juz podlaczona w init, czy potrzebne
            //nie sprawdzac uprawnien, ich zmiana zatrzymuje całkonicie aplikacje
        } else {
            textureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
        screenController.hideKeyboard();
    }

    public void checkPermisionns(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                isCameraPermission=true;
            } else {
                if (shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                    Toast.makeText(this, getString(R.string.no_permission), Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{android.Manifest.permission.CAMERA}, Config.REQUEST_CAMERA_PERMISSION_RESULT);
            }
        } else {
            isCameraPermission=true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Config.REQUEST_CAMERA_PERMISSION_RESULT) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, getString(R.string.no_permission), Toast.LENGTH_SHORT).show();
            } else isCameraPermission=true;
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    public void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                mPreviewSize = prevSize(map.getOutputSizes(SurfaceTexture.class), width);
                mImageSize = chooseImageSize(map.getOutputSizes(ImageFormat.JPEG));
                mImageReader = ImageReader.newInstance(mImageSize.getWidth(), mImageSize.getHeight(), ImageFormat.JPEG, 10);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try {
            cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
        } catch (CameraAccessException | SecurityException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
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
                        }
                    };

                mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    public void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("Camera2VideoImage");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    public void stopBackgroundThread() {
        if (mBackgroundHandlerThread==null) return;
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static Size prevSize(Size[] choices, int width){
        for (int i=1;i<choices.length;i++){
            if(choices[i].getWidth()<width)return choices[i-1];
        }
        return  choices[0];
    }

    private static Size chooseImageSize(Size[] choices) {
        return Collections.max(Arrays.asList(choices), new CompareSizeByArea());
    }

    public void capture() {
        if(mBackgroundHandler==null){
            Toast.makeText(context, R.string.camera_off,Toast.LENGTH_SHORT).show();
            return;
        }
        mCaptureState = Config.STATE_WAIT_LOCK;
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), mPreviewCaptureCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void setIncomeTax(float incomeTax) {
        this.incomeTax = incomeTax;
    }

    public void setVatRelief(float vatRelief) {
        this.vatRelief = vatRelief;
    }

    public void setArticleVat(float articleVat) {
        this.articleVat = articleVat;
    }

    public float getIncomeTax() {
        return incomeTax;
    }

    public float getVatRelief() {
        return vatRelief;
    }

    public float getArticleVat() {
        return articleVat;
    }

    public boolean isCameraPermission() {
        return isCameraPermission;
    }

    public TextureView getTextureView() {
        return textureView;
    }

    private Bitmap cropBitmap(Bitmap bitmap){
        Bitmap result;
        int width=bitmap.getWidth();
        int height=bitmap.getHeight();

        int scaledWidth = width/ Config.SCALE_X;
        int scaledHeight = height / Config.SCALE_Y;

        int dx = (int)((width-scaledWidth)*0.5);
        int dy =(int)((height-scaledHeight)*0.5);
        dx=dx+scaledWidth/4;//wycinek podgladu

        result=Bitmap.createBitmap(bitmap, dx,dy, scaledWidth/2, scaledHeight/2); // rozpoznanie gornej polowki

        return result;
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

            Bitmap tmp=BitmapFactory.decodeByteArray(bytes,0,bytes.length);

            Frame frame = new Frame.Builder().setBitmap(cropBitmap(tmp)).build();
            SparseArray<TextBlock> textBlocks = textRecognizer.detect(frame);
            String text="";
            for(int i = 0; i < textBlocks.size(); i++) {
                if (textBlocks.get(i) == null) continue;
                text += textBlocks.get(i).getValue();
            }

            final Pattern pattern = Pattern.compile(Config.REGEXP);
            Matcher matcher = pattern.matcher(text);
            if(matcher.find()){
                final String finalText = matcher.group(0);
                screenController.geteGross().post(new Runnable() {
                    @Override
                    public void run() {
                        screenController.geteGross().setText(finalText);
                    }
                });

            }
        }
    }

}
