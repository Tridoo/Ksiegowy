package tridoo.ksiegowy;


import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import java.math.BigDecimal;

public class ScreenController {
    private MainActivity activity;
    private GridLayout layParameters;
    private LinearLayout laySummary;
    private EditText eGross;
    private View curtain;
    private Switch swPreview;

    public ScreenController(MainActivity activity){
        this.activity=activity;
    }

    public void setupButtons(){
        layParameters=(GridLayout)activity.findViewById(R.id.lay_parameters);
        laySummary=(LinearLayout)activity.findViewById(R.id.lay_summary);
        eGross = (EditText) activity.findViewById(R.id.e_gross);
        curtain= activity.findViewById(R.id.tv_curtain);
        swPreview=((Switch) activity.findViewById(R.id.sw_preview));

        ((ImageButton) activity.findViewById(R.id.btn_scan)).setOnClickListener(v -> activity.capture());

        swPreview.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean isChecked) {
                if (!isChecked) {
                    activity.closeCamera();
                    activity.stopBackgroundThread();
                    curtain.setVisibility(View.VISIBLE);
                } else {
                    activity.startBackgroundThread();
                    activity.setupCamera(activity.getTextureView().getWidth(), activity.getTextureView().getHeight());
                    if (activity.isCameraPermission()) {
                        if (activity.connectCamera()) curtain.setVisibility(View.INVISIBLE);
                    } else activity.checkPermisionns();
                }
            }
        });

        ((activity.findViewById(R.id.btn_edit))).setOnClickListener(v -> {
            layParameters.setVisibility(View.VISIBLE);
            laySummary.setVisibility(View.GONE);
        });

        ((activity.findViewById(R.id.btn_up))).setOnClickListener(v -> {
            layParameters.setVisibility(View.GONE);
            laySummary.setVisibility(View.VISIBLE);
            setupParameters();
        });

        ((RadioGroup)activity.findViewById(R.id.gr_income)).setOnCheckedChangeListener(new CheckedChangeListener());
        ((RadioGroup)activity.findViewById(R.id.gr_vat)).setOnCheckedChangeListener(new CheckedChangeListener());
        ((RadioGroup)activity.findViewById(R.id.gr_vat2)).setOnCheckedChangeListener(new CheckedChangeListener());


        TextWatcher watcher = new TextWatcher() {
            public void afterTextChanged(Editable s) {
                activity.calculate();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //Do something or nothing.
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //Do something or nothing
            }
        };
        eGross.addTextChangedListener(watcher);
    }

    public void keyboardObserver(final Context context){
        final View activityRootView = activity.findViewById(R.id.lay_root);
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int heightDiff = activityRootView.getRootView().getHeight() - activityRootView.getHeight();
                int width=activity.getTextureView().getWidth();
                if (heightDiff > dpToPx(context, 200)) { //todo inaczej?
                    activity.findViewById(R.id.lay_buttons).setVisibility(View.GONE);
                    resizeElements(width,10); //nie chować textureview !!
                    curtain.setVisibility(View.VISIBLE);
                }
                else {
                    activity.findViewById(R.id.lay_buttons).setVisibility(View.VISIBLE);
                    resizeElements(width);
                    if (swPreview.isChecked()) curtain.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    private static float dpToPx(Context context, float valueInDp) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valueInDp, metrics);
    }

    public void resizeElements(int ... param){
        int width = param.length > 0 ? param[0] : 0;
        int height = param.length > 1 ? param[1] : (int)(width*0.5f);
        activity.getTextureView().getLayoutParams().height= height;
        ViewGroup.LayoutParams params= activity.findViewById(R.id.frame).getLayoutParams();
        params.width=width/2;
        params.height=height/2;
    }

    public void hideKeyboard(){
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    private void readCheckedParameters(){
        activity.setVatRelief(getVatRelief());
        activity.setIncomeTax(getIncomeTax());
        activity.setArticleVat(getArticleVat());
    }

    public int getArticleVat(){
        if(((RadioButton)activity.findViewById(R.id.rb_p_0)).isChecked()) return 0;
        if(((RadioButton)activity.findViewById(R.id.rb_p_5)).isChecked()) return 5;
        if(((RadioButton)activity.findViewById(R.id.rb_p_8)).isChecked()) return 8;
        return 23;
    }

    public int getVatRelief(){
        if(((RadioButton)activity.findViewById(R.id.rb_0)).isChecked()) return 0;
        if(((RadioButton)activity.findViewById(R.id.rb_50)).isChecked()) return 50;
        return 100;
    }

    public int getIncomeTax(){
        if(((RadioButton)activity.findViewById(R.id.rb_18)).isChecked()) return 18;
        else return 19;
    }

    public String getGross() {
        String gross = eGross.getText().toString();
        return gross.isEmpty() ? "0" : gross;
    }

    public void setupParameters(){
        ((TextView) activity.findViewById(R.id.tv_income_tax)).setText(colvertToPercent(activity.getIncomeTaxPercent()));
        ((TextView) activity.findViewById(R.id.tv_vat)).setText(colvertToPercent(activity.getVatReliefPercent()));

        if (activity.getIncomeTaxPercent() == 18) {
            ((RadioButton) activity.findViewById(R.id.rb_18)).setChecked(true);
        } else {
            ((RadioButton) activity.findViewById(R.id.rb_19)).setChecked(true);
        }

        if (activity.getVatReliefPercent() == 0) {
            ((RadioButton) activity.findViewById(R.id.rb_0)).setChecked(true);

        } else if (activity.getVatReliefPercent() == 50) {
            ((RadioButton) activity.findViewById(R.id.rb_50)).setChecked(true);

        } else  {
            ((RadioButton) activity.findViewById(R.id.rb_100)).setChecked(true);
        }
    }

    private String colvertToPercent(int value) {
        return value + "%";
    }

    public GridLayout getLayParameters() {
        return layParameters;
    }

    public LinearLayout getLaySummary() {
        return laySummary;
    }

    public EditText geteGross() {
        return eGross;
    }

    public void setSwitchState(boolean state){
        swPreview.setChecked(state);
    }

    public void setCalculatedValues(BigDecimal vatReliefAmount, BigDecimal incomeTaxAmount, BigDecimal expense) {
        ((TextView) activity.findViewById(R.id.tv_relief_vat)).setText(vatReliefAmount.toString());
        ((TextView) activity.findViewById(R.id.tv_relief_inc)).setText(incomeTaxAmount.toString());
        ((TextView) activity.findViewById(R.id.tv_cost)).setText(expense.toString());
    }

    private class CheckedChangeListener implements RadioGroup.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            readCheckedParameters();
            activity.calculate();
            activity.saveParameters();
        }
    }
}
