package tridoo.ksiegowy;


import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.vision.text.TextRecognizer;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {

    private static final int DEFAULT_INCOME_TAX_PERCENT = 19;
    private static final int DEFAULT_VAT_RELIEF_PERCENT = 100;

    private int captureState;
    private TextRecognizer textRecognizer;
    private int incomeTaxPercent, vatReliefPercent, articleVatPercent;
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
        context = getApplicationContext();

        screenController = new ScreenController(this);
        screenController.postConstruct();
        init();

        if (dao.isTaxesSaved()) {
            readSavedParameters();
            screenController.setLayParametersVisible(false);
        } else {
            incomeTaxPercent = DEFAULT_INCOME_TAX_PERCENT;
            vatReliefPercent = DEFAULT_VAT_RELIEF_PERCENT;
            screenController.setLaySummaryVisible(false);
        }
        screenController.setParametersOnViews();

        //showAds();
    }

    private void init() {
        captureState = Config.STATE_PREVIEW;

        surfaceTextureListener = new SurfaceTextureListenerImpl(screenController, this);
        cameraDeviceStateCallback = new CameraDeviceStateCallback(this);

        onImageAvailableListener = reader -> backgroundHandler.post(new ImageProcessor(reader.acquireLatestImage(), textRecognizer, screenController));

        previewCaptureCallback = new PreviewCaptureCallback(this);

        dao = new Dao(context);
        textRecognizer = new TextRecognizer.Builder(context).build();
    }

    public void calculate() {
        BigDecimal grossAmount = new BigDecimal(screenController.getGrossAsString());
        BigDecimal netAmount = grossAmount.divide(BigDecimal.valueOf(100 + screenController.getArticleVat())
                .divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_HALF_EVEN), 4, BigDecimal.ROUND_HALF_EVEN);

        BigDecimal vatAmount = grossAmount.subtract(netAmount);

        BigDecimal vatReliefAmount = vatAmount.multiply(BigDecimal.valueOf(screenController.getVatRelief()))
                .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_EVEN);

        BigDecimal incomeTaxAmount = grossAmount.subtract(vatReliefAmount)
                .multiply(BigDecimal.valueOf(screenController.getIncomeTax()))
                .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_EVEN);

        BigDecimal reliefAmount = vatReliefAmount.add(incomeTaxAmount);

        BigDecimal expense = grossAmount.subtract(reliefAmount);

        screenController.setCalculatedValues(vatReliefAmount, incomeTaxAmount, expense);
    }

    private void showAds() {
        AdView adView = (AdView) findViewById(R.id.baner);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
    }

    private void readSavedParameters() {
        incomeTaxPercent = dao.getIncomeTax();
        vatReliefPercent = dao.getVat();
    }

    public void saveParameters() {
        dao.saveTaxes(screenController.getVatRelief(), screenController.getIncomeTax());
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
        if (screenController.getTextureView().isAvailable()) {
            setupCamera();
            if (isCameraPermission) {
                connectCamera();
            }
        } else {
            screenController.getTextureView().setSurfaceTextureListener(surfaceTextureListener);
        }
        screenController.hideKeyboard();
    }

    public void checkPermisionns() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                isCameraPermission = true;
            } else {
                if (shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                    screenController.setSwitchState(false);
                    Toast.makeText(this, getString(R.string.no_permission), Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{android.Manifest.permission.CAMERA}, Config.REQUEST_CAMERA_PERMISSION_RESULT);
            }
        } else {
            isCameraPermission = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Config.REQUEST_CAMERA_PERMISSION_RESULT) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                screenController.setSwitchState(false);
                Toast.makeText(context, getString(R.string.no_permission), Toast.LENGTH_SHORT).show();
            } else {
                screenController.setSwitchState(true);
                isCameraPermission = true;
            }
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    public void setupCamera() {
        int width = screenController.getTextureView().getWidth();
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

    public boolean connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cameraId == null) {
            screenController.setSwitchState(false);
            Toast.makeText(context, R.string.camera_error, Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            cameraManager.openCamera(cameraId, cameraDeviceStateCallback, backgroundHandler);
        } catch (CameraAccessException | SecurityException e) {
            e.printStackTrace();
        }
        return true;
    }

    public void startPreview() {
        SurfaceTexture surfaceTexture = screenController.getTextureView().getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            StateCallbackImpl stateCallback = new StateCallbackImpl(captureRequestBuilder, backgroundHandler, this);
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, imageReader.getSurface()), stateCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void startCaptureRequest() {
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);

            CameraCaptureSession.CaptureCallback stillCaptureCallback = new StillCaptureCallback();

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
        if (backgroundHandlerThread == null) return;
        backgroundHandlerThread.quitSafely();
        try {
            backgroundHandlerThread.join();
            backgroundHandlerThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static Size prevSize(Size[] choices, int width) {
        for (int i = 1; i < choices.length; i++) {
            if (choices[i].getWidth() < width) return choices[i - 1];
        }
        return choices[0];
    }

    private static Size chooseImageSize(Size[] choices) {
        return Collections.max(Arrays.asList(choices), new CompareSizeByArea());
    }

    public void capture() {
        if (!isCameraPermission) {
            checkPermisionns();
            return;
        }
        if (backgroundHandler == null) {
            Toast.makeText(context, R.string.camera_off, Toast.LENGTH_SHORT).show();
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

    public void setIncomeTax(int incomeTax) {
        incomeTaxPercent = incomeTax;
    }

    public void setVatRelief(int vatRelief) {
        vatReliefPercent = vatRelief;
    }

    public void setArticleVat(int articleVat) {
        articleVatPercent = articleVat;
    }

    public int getIncomeTaxPercent() {
        return incomeTaxPercent;
    }

    public int getVatReliefPercent() {
        return vatReliefPercent;
    }

    public int getArticleVatPercent() {
        return articleVatPercent;
    }

    public boolean isCameraPermission() {
        return isCameraPermission;
    }

    public void setCameraDevice(CameraDevice camera) {
        cameraDevice = camera;
    }

    public void setPreviewCaptureSession(CameraCaptureSession previewCaptureSession) {
        this.previewCaptureSession = previewCaptureSession;
    }

    public int getCaptureState() {
        return captureState;
    }

    public void setCaptureState(int captureState) {
        this.captureState = captureState;
    }
}
