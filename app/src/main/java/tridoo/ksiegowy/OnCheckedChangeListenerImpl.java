package tridoo.ksiegowy;

import android.view.View;
import android.widget.CompoundButton;

public class OnCheckedChangeListenerImpl implements CompoundButton.OnCheckedChangeListener {

    private MainActivity activity;
    private View curtain;

    public OnCheckedChangeListenerImpl( MainActivity activity, View curtain) {
        this.activity = activity;
        this.curtain = curtain;
    }

    @Override
    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
        if (!isChecked) {
            activity.closeCamera();
            activity.stopBackgroundThread();
            curtain.setVisibility(View.VISIBLE);
        } else {
            activity.startBackgroundThread();
            activity.setupCamera();
            if (activity.isCameraPermission()) {
                if (activity.connectCamera()) curtain.setVisibility(View.INVISIBLE);
            } else activity.checkPermisionns();
        }
    }
}
