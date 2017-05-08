package tridoo.ksiegowy;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;


public class Dao {
    private Context context;
    private int prPodDoch;
    private int prVat;
    private boolean czySaDane;

    public Dao(Context aContext) {
        context = aContext;
        pobierzDane();
    }

    private void pobierzDane(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if(prefs.contains("vat")){
            czySaDane=true;
            prVat = prefs.getInt("vat",100);
            prPodDoch = prefs.getInt("podDoch",18);
        }else czySaDane=false;
    }

    public void zapiszDane(int vat, int podDoch) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putInt("vat", vat);
        editor.putInt("podDoch", podDoch);
        editor.apply();
    }


    public int getPrPodDoch() {
        return prPodDoch;
    }


    public int getPrVat() {
        return prVat;
    }

    public boolean isCzySaDane() {
        return czySaDane;
    }


}
