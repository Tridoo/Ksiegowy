package tridoo.ksiegowy;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.widget.Toast;

public class PreviewCaptureCallback extends CameraCaptureSession.CaptureCallback {

    private MainActivity activity;

    public PreviewCaptureCallback(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
        super.onCaptureCompleted(session, request, result);
        process(result);
    }

    private void process(CaptureResult captureResult) {
        switch (activity.getCaptureState()) {
            case Config.STATE_PREVIEW:
                // Do nothing
                break;
            case Config.STATE_WAIT_LOCK:
                activity.setCaptureState(Config.STATE_PREVIEW);
                Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                        || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
                        || afState == CaptureResult.CONTROL_AE_ANTIBANDING_MODE_OFF) {
                    Toast.makeText(activity.getApplicationContext(), activity.getString(R.string.scanning), Toast.LENGTH_SHORT).show();
                    activity.startCaptureRequest();
                }
                break;
        }
    }

}
