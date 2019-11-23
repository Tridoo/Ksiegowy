package tridoo.ksiegowy;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;

public class StateCallbackImpl extends CameraCaptureSession.StateCallback {

    private CaptureRequest.Builder captureRequestBuilder;
    private Handler backgroundHandler;
    private MainActivity activity;

    public StateCallbackImpl(CaptureRequest.Builder captureRequestBuilder, Handler backgroundHandler, MainActivity activity) {
        this.captureRequestBuilder = captureRequestBuilder;
        this.backgroundHandler = backgroundHandler;
        this.activity = activity;
    }

    @Override
    public void onConfigured(CameraCaptureSession session) {
        try {
            session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
            activity.setPreviewCaptureSession(session);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConfigureFailed(CameraCaptureSession session) {

    }
}
