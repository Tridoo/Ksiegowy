package tridoo.ksiegowy;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;

public class StillCaptureCallback extends CameraCaptureSession.CaptureCallback {

    @Override
    public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
        super.onCaptureStarted(session, request, timestamp, frameNumber);
    }
}
