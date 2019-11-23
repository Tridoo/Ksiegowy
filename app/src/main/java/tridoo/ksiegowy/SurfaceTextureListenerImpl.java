package tridoo.ksiegowy;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.view.TextureView;

public class SurfaceTextureListenerImpl implements TextureView.SurfaceTextureListener {

    private ScreenController screenController;
    private MainActivity mainActivity;


    public SurfaceTextureListenerImpl(ScreenController screenController, MainActivity mainActivity) {
        this.screenController = screenController;
        this.mainActivity = mainActivity;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        screenController.resizeElements(width, height);
        Matrix matrix = new Matrix();
        matrix.setScale(Config.SCALE_X, Config.SCALE_Y, width / 2, height / 2);
        screenController.getTextureView().setTransform(matrix);

        mainActivity.setupCamera();
        if (mainActivity.isCameraPermission()) mainActivity.connectCamera();
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

}
