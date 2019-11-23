package tridoo.ksiegowy;

import android.text.Editable;
import android.text.TextWatcher;

public class TextWatcherImpl implements TextWatcher {

    private MainActivity activity;

    public TextWatcherImpl(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {    }

    @Override
    public void afterTextChanged(Editable s) {
        activity.calculate();
    }
}
