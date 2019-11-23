package tridoo.ksiegowy;


import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
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
    private CompoundButton.OnCheckedChangeListener onCheckedChangeListener;
    private GridLayout layParameters;
    private LinearLayout laySummary;
    private LinearLayout layButtons;

    private EditText eGross;
    private View curtain;
    private Switch swPreview;

    private ImageButton buttonScan;
    private ImageButton buttonEdit;
    private ImageButton buttonUp;
    private RadioGroup groupIncome;
    private RadioGroup groupVat;
    private RadioGroup groupVat2;
    private View frame;
    private RadioButton rbArticleVat0;
    private RadioButton rbArticleVat5;
    private RadioButton rbArticleVat8;
    private RadioButton rbVatRelif0;
    private RadioButton rbVatRelif50;
    private RadioButton rbIncomeTax1;
    private RadioButton rbIncomeTax2;
    private RadioButton rbVatRelif100;
    private TextView tvIncomeTax;
    private TextView tvVat;
    private TextView tvVatRelief;
    private TextView tvIncomeRelief;
    private TextView tvCost;
    private View activityRootView;

    private TextureView textureView;

    private int vat_relief_100;
    private int vat_relief_50;
    private int vat_relief_0;

    private int income_tax_1;
    private int income_tax_2;

    private int article_vat_23;
    private int article_vat_8;
    private int article_vat_5;
    private int article_vat_0;


    public ScreenController(MainActivity activity) {
        this.activity = activity;
    }

    public void postConstruct() {
        getConstants();
        assignButtons();
        setupClickListeners();
    }

    private void getConstants() {
        vat_relief_100 = activity.getApplicationContext().getResources().getInteger(R.integer.vat_relief_100);
        vat_relief_50 = activity.getApplicationContext().getResources().getInteger(R.integer.vat_relief_50);
        vat_relief_0 = activity.getApplicationContext().getResources().getInteger(R.integer.vat_relief_0);

        income_tax_1 = activity.getApplicationContext().getResources().getInteger(R.integer.income_I);
        income_tax_2 = activity.getApplicationContext().getResources().getInteger(R.integer.income_II);

        article_vat_23 = activity.getApplicationContext().getResources().getInteger(R.integer.article_vat_23);
        article_vat_8 = activity.getApplicationContext().getResources().getInteger(R.integer.article_vat_8);
        article_vat_5 = activity.getApplicationContext().getResources().getInteger(R.integer.article_vat_5);
        article_vat_0 = activity.getApplicationContext().getResources().getInteger(R.integer.article_vat_0);
    }

    private void assignButtons() {
        activityRootView = activity.findViewById(R.id.lay_root);
        layParameters = (GridLayout) activity.findViewById(R.id.lay_parameters);
        laySummary = (LinearLayout) activity.findViewById(R.id.lay_summary);
        layButtons = (LinearLayout) activity.findViewById(R.id.lay_buttons);

        eGross = (EditText) activity.findViewById(R.id.e_gross);
        curtain = activity.findViewById(R.id.tv_curtain);
        swPreview = ((Switch) activity.findViewById(R.id.sw_preview));

        buttonScan = (ImageButton) activity.findViewById(R.id.btn_scan);
        buttonEdit = (ImageButton) activity.findViewById(R.id.btn_edit);
        buttonUp = (ImageButton) activity.findViewById(R.id.btn_up);

        groupIncome = ((RadioGroup) activity.findViewById(R.id.gr_income));
        groupVat = ((RadioGroup) activity.findViewById(R.id.gr_vat));
        groupVat2 = ((RadioGroup) activity.findViewById(R.id.gr_vat2));

        frame = activity.findViewById(R.id.frame);

        rbArticleVat0 = ((RadioButton) activity.findViewById(R.id.rb_p_0));
        rbArticleVat5 = ((RadioButton) activity.findViewById(R.id.rb_p_5));
        rbArticleVat8 = ((RadioButton) activity.findViewById(R.id.rb_p_8));
        rbVatRelif0 = ((RadioButton) activity.findViewById(R.id.rb_0));
        rbVatRelif50 = ((RadioButton) activity.findViewById(R.id.rb_50));
        rbVatRelif100 = ((RadioButton) activity.findViewById(R.id.rb_100));
        rbIncomeTax1 = ((RadioButton) activity.findViewById(R.id.rb_18));
        rbIncomeTax2 = ((RadioButton) activity.findViewById(R.id.rb_19));

        tvIncomeTax = ((TextView) activity.findViewById(R.id.tv_income_tax));
        tvVat = ((TextView) activity.findViewById(R.id.tv_vat));
        tvVatRelief = ((TextView) activity.findViewById(R.id.tv_relief_vat));
        tvIncomeRelief = ((TextView) activity.findViewById(R.id.tv_relief_inc));
        tvCost = ((TextView) activity.findViewById(R.id.tv_cost));
        textureView = (TextureView) activity.findViewById(R.id.textureView);
    }

    private void setupClickListeners() {
        onCheckedChangeListener = new OnCheckedChangeListenerImpl(activity, curtain);

        buttonScan.setOnClickListener(v -> onScanClick());
        buttonEdit.setOnClickListener(v -> onEditClick());
        buttonUp.setOnClickListener(v -> onUpClick());

        groupIncome.setOnCheckedChangeListener(new CheckedChangeListener());
        groupVat.setOnCheckedChangeListener(new CheckedChangeListener());
        groupVat2.setOnCheckedChangeListener(new CheckedChangeListener());

        eGross.addTextChangedListener(new TextWatcherImpl(activity));

        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> onGlobalLayClick());

        swPreview.setOnCheckedChangeListener((button, isChecked) -> onCheckedChangeListener.onCheckedChanged(button, isChecked));
    }

    private static float dpToPx(DisplayMetrics metrics, float valueInDp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valueInDp, metrics);
    }

    public void resizeElements(int... param) {
        int width = param.length > 0 ? param[0] : 0;
        int height = param.length > 1 ? param[1] : (int) (width * 0.5f);
        textureView.getLayoutParams().height = height;
        ViewGroup.LayoutParams params = frame.getLayoutParams();
        params.width = width / 2;
        params.height = height / 2;
    }

    public void hideKeyboard() {
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    private void setReadedParameters() {
        activity.setVatRelief(getVatRelief());
        activity.setIncomeTax(getIncomeTax());
        activity.setArticleVat(getArticleVat());
    }

    public int getArticleVat() {
        if (rbArticleVat0.isChecked()) return article_vat_0;
        if (rbArticleVat5.isChecked()) return article_vat_5;
        if (rbArticleVat8.isChecked()) return article_vat_8;
        return article_vat_23;
    }

    public int getVatRelief() {
        if (rbVatRelif0.isChecked()) return vat_relief_0;
        if (rbVatRelif50.isChecked()) return vat_relief_50;
        return vat_relief_100;
    }

    public int getIncomeTax() {
        if (rbIncomeTax1.isChecked()) return income_tax_1;
        return income_tax_2;
    }

    public String getGrossAsString() {
        String gross = eGross.getText().toString();
        return gross.isEmpty() ? "0" : gross;
    }

    public void setGrossString(String text) {
        eGross.post(() -> eGross.setText(text));
    }

    public void setParametersOnViews() {
        tvIncomeTax.setText(colvertToPercent(activity.getIncomeTaxPercent()));
        tvVat.setText(colvertToPercent(activity.getVatReliefPercent()));

        if (activity.getIncomeTaxPercent() == income_tax_1) {
            rbIncomeTax1.setChecked(true);
        } else {
            rbIncomeTax2.setChecked(true);
        }

        if (activity.getVatReliefPercent() == vat_relief_0) {
            rbVatRelif0.setChecked(true);
        } else if (activity.getVatReliefPercent() == vat_relief_50) {
            rbVatRelif50.setChecked(true);
        } else {
            rbVatRelif100.setChecked(true);
        }
    }

    private String colvertToPercent(int value) {
        return value + "%";
    }

    public void setSwitchState(boolean state) {
        swPreview.setChecked(state);
    }

    public void setCalculatedValues(BigDecimal vatReliefAmount, BigDecimal incomeTaxAmount, BigDecimal expense) {
        tvVatRelief.setText(vatReliefAmount.toString());
        tvIncomeRelief.setText(incomeTaxAmount.toString());
        tvCost.setText(expense.toString());
    }

    public void setLayParametersVisible(boolean isVisible) {
        layParameters.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    public void setLaySummaryVisible(boolean isVisible) {
        laySummary.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private void onScanClick() {
        activity.capture();
    }

    private void onEditClick() {
        setLayParametersVisible(true);
        setLaySummaryVisible(false);
    }

    private void onUpClick() {
        setLayParametersVisible(false);
        setLaySummaryVisible(true);
        setParametersOnViews();
    }

    private void onGlobalLayClick() {
        DisplayMetrics metrics = activity.getApplicationContext().getResources().getDisplayMetrics();
        int heightDiff = activityRootView.getRootView().getHeight() - activityRootView.getHeight();
        int width = textureView.getWidth();
        if (heightDiff > dpToPx(metrics, 200)) { //todo inaczej?
            layButtons.setVisibility(View.GONE);
            resizeElements(width, 10); //nie chować textureview !!
            curtain.setVisibility(View.VISIBLE);
        } else {
            layButtons.setVisibility(View.VISIBLE);
            resizeElements(width);
            if (swPreview.isChecked()) curtain.setVisibility(View.INVISIBLE);
        }
    }

    public TextureView getTextureView() {
        return textureView;
    }

    private class CheckedChangeListener implements RadioGroup.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            setReadedParameters();
            activity.calculate();
            activity.saveParameters();
        }
    }
}
