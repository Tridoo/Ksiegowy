package tridoo.ksiegowy;

import android.hardware.camera2.CameraDevice;

public class CameraDeviceStateCallback extends CameraDevice.StateCallback {

    private MainActivity activity;

    public CameraDeviceStateCallback(MainActivity mainActivity) {
        activity = mainActivity;
    }

    @Override
    public void onOpened(CameraDevice camera) {
        activity.setCameraDevice(camera);
        activity.startPreview();
    }

    @Override
    public void onDisconnected(CameraDevice camera) {
        camera.close();
        activity.setCameraDevice(null);
    }

    @Override
    public void onError(CameraDevice camera, int error) {
        camera.close();
        activity.setCameraDevice(null);
    }
}
