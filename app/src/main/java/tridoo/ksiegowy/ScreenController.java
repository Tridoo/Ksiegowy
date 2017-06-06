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

        ((ImageButton) activity.findViewById(R.id.btn_scan)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.capture();
            }
        });

        swPreview.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean isChecked) {
                if (!isChecked){
                    activity.closeCamera();
                    activity.stopBackgroundThread();
                    curtain.setVisibility(View.VISIBLE);
                }else{
                    activity.startBackgroundThread();
                    activity.setupCamera(activity.getTextureView().getWidth(), activity.getTextureView().getHeight());
                    if(activity.isCameraPermission() )activity.connectCamera();
                    else activity.checkPermisionns();
                    curtain.setVisibility(View.INVISIBLE);
                }

            }
        });

        ((activity.findViewById(R.id.btn_edit))).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layParameters.setVisibility(View.VISIBLE);
                laySummary.setVisibility(View.GONE);
            }
        });

        ((activity.findViewById(R.id.btn_up))).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layParameters.setVisibility(View.GONE);
                laySummary.setVisibility(View.VISIBLE);
                setupParameters();
            }
        });

        ((RadioGroup)activity.findViewById(R.id.gr_income)).setOnCheckedChangeListener(new CheckedChangeListener());
        ((RadioGroup)activity.findViewById(R.id.gr_vat)).setOnCheckedChangeListener(new CheckedChangeListener());
        ((RadioGroup)activity.findViewById(R.id.gr_vat2)).setOnCheckedChangeListener(new CheckedChangeListener());
//wyrównanie RB grup
         //http://stackoverflow.com/questions/2381560/how-to-group-a-3x3-grid-of-radio-buttons


        TextWatcher watcher= new TextWatcher() {
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
                if (heightDiff > dpToPx(context, 200)) { //todo poprawic
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

    public static float dpToPx(Context context, float valueInDp) {
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

    public void readCheckedParameters(){
        activity.setVatRelief(getVatRelief());
        activity.setIncomeTax(getIncomeTax());
        activity.setArticleVat(getArticleVat());
    }

    public float getArticleVat(){
        if(((RadioButton)activity.findViewById(R.id.rb_p_0)).isChecked()) return 0f;
        if(((RadioButton)activity.findViewById(R.id.rb_p_5)).isChecked()) return 0.05f;
        if(((RadioButton)activity.findViewById(R.id.rb_p_8)).isChecked()) return 0.08f;
        return 0.23f;
    }

    public float getVatRelief(){
        if(((RadioButton)activity.findViewById(R.id.rb_0)).isChecked()) return 0f;
        if(((RadioButton)activity.findViewById(R.id.rb_50)).isChecked()) return 0.5f;
        return 1;
    }

    public float getIncomeTax(){
        if(((RadioButton)activity.findViewById(R.id.rb_18)).isChecked()) return 0.18f;
        else return 0.19f;
    }

    public float getGross(){
        String gross=eGross.getText().toString();
        return gross.isEmpty() ? 0 : Float.valueOf(gross.replace(",", "."));
    }

    public void setupParameters(){
        ((TextView)activity.findViewById(R.id.tv_income_tax)).setText(activity.getIncomeTax() *100+"%");
        ((TextView)activity.findViewById(R.id.tv_vat)).setText(activity.getVatRelief() *100+"%");

        if (activity.getIncomeTax() == 0.18f) {
            ((RadioButton) activity.findViewById(R.id.rb_18)).setChecked(true);
        } else {
            ((RadioButton) activity.findViewById(R.id.rb_19)).setChecked(true);
        }

        if (activity.getVatRelief() == 0f) {
            ((RadioButton) activity.findViewById(R.id.rb_0)).setChecked(true);

        } else if (activity.getVatRelief() == 0.5f) {
            ((RadioButton) activity.findViewById(R.id.rb_50)).setChecked(true);

        } else  {
            ((RadioButton) activity.findViewById(R.id.rb_100)).setChecked(true);
        }
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

    private class CheckedChangeListener implements RadioGroup.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            readCheckedParameters();
            activity.calculate();
            activity.saveParameters();
        }
    }
}
