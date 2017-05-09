package tridoo.ksiegowy;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import static tridoo.ksiegowy.Config.INCOME_TAX;
import static tridoo.ksiegowy.Config.VAT;


public class Dao {
    private Context context;
    private int incomeTax;
    private int vat;
    private boolean isTaxesSaved;

    public Dao(Context aContext) {
        context = aContext;
        getData();
    }

    private void getData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.contains(VAT)) {
            isTaxesSaved = true;
            vat = prefs.getInt(VAT, 100);
            incomeTax = prefs.getInt(INCOME_TAX, 18);
        } else isTaxesSaved = false;
    }

    public void saveTaxes(int vat, int incomeTax) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(VAT, vat);
        editor.putInt(INCOME_TAX, incomeTax);
        editor.apply();
    }


    public int getIncomeTax() {
        return incomeTax;
    }

    public int getVat() {
        return vat;
    }

    public boolean isTaxesSaved() {
        return isTaxesSaved;
    }

}
