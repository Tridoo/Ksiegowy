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

    private int captureState;
    private TextureView textureView;
    private TextRecognizer textRecognizer;
    private float incomeTax, vatRelief, articleVat;
    private Dao dao;

    private TextureView.SurfaceTextureListener surfaceTextureListener;
    private CameraDevice cameraDevice;
    private CameraDevice.StateCallback cameraDeviceStateCallback;
    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;
    private String cameraId;
    private Size previewSize;
    private Size imageSize;
    private ImageReader imageReader;
    private ImageReader.OnImageAvailableListener onImageAvailableListener;

    private CameraCaptureSession previewCaptureSession;
    private CameraCaptureSession.CaptureCallback previewCaptureCallback;

    private CaptureRequest.Builder captureRequestBuilder;

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
            screenController.getLayParameters().setVisibility(View.GONE);
        }else{
            incomeTax = 0.19f;
            vatRelief = 1f;
            screenController.getLaySummary().setVisibility(View.GONE);
        }
        screenController.setupParameters();

        showAds();
    }

    private void init(){
        captureState = Config.STATE_PREVIEW;

        surfaceTextureListener = new TextureView.SurfaceTextureListener() {
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

        cameraDeviceStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                cameraDevice = camera;
                startPreview();
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                camera.close();
                cameraDevice = null;
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                camera.close();
                cameraDevice = null;
            }
        };

        onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                backgroundHandler.post(new ImageProcessor(reader.acquireLatestImage()));
            }
        };

        previewCaptureCallback = new CameraCaptureSession.CaptureCallback() {
                    private void process(CaptureResult captureResult) {
                        switch (captureState) {
                            case Config.STATE_PREVIEW:
                                // Do nothing
                                break;
                            case Config.STATE_WAIT_LOCK:
                                captureState = Config.STATE_PREVIEW;
                                Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                                if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
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
        AdView adView = (AdView) findViewById(R.id.baner);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
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
        //nie sprawdzac uprawnien, ich zmiana zatrzymuje całkonicie aplikacje
        if (textureView.isAvailable()) {
            setupCamera(textureView.getWidth(), textureView.getHeight());
            if (isCameraPermission) {
                connectCamera();
            }
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
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
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                previewSize = prevSize(map.getOutputSizes(SurfaceTexture.class), width);
                imageSize = chooseImageSize(map.getOutputSizes(ImageFormat.JPEG));
                imageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(), ImageFormat.JPEG, 10);
                imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
                this.cameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try {
            cameraManager.openCamera(cameraId, cameraDeviceStateCallback, backgroundHandler);
        } catch (CameraAccessException | SecurityException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            previewCaptureSession = session;
                            try {
                                previewCaptureSession.setRepeatingRequest(captureRequestBuilder.build(),
                                        null, backgroundHandler);
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
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            captureRequestBuilder.addTarget(imageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);

            CameraCaptureSession.CaptureCallback stillCaptureCallback = new
                    CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                        }
                    };

                previewCaptureSession.capture(captureRequestBuilder.build(), stillCaptureCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void closeCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    public void startBackgroundThread() {
        backgroundHandlerThread = new HandlerThread("Ksiegowy");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
    }

    public void stopBackgroundThread() {
        if (backgroundHandlerThread ==null) return;
        backgroundHandlerThread.quitSafely();
        try {
            backgroundHandlerThread.join();
            backgroundHandlerThread = null;
            backgroundHandler = null;
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
        if(backgroundHandler ==null){
            Toast.makeText(context, R.string.camera_off,Toast.LENGTH_SHORT).show();
            return;
        }
        captureState = Config.STATE_WAIT_LOCK;
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            previewCaptureSession.capture(captureRequestBuilder.build(), previewCaptureCallback, backgroundHandler);

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

    private class ImageProcessor implements Runnable {

        private final Image image;

        public ImageProcessor(Image image) {
            this.image = image;
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
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
