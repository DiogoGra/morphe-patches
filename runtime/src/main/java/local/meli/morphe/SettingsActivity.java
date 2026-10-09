package local.meli.morphe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;
import java.lang.reflect.Proxy;

/** Local-only settings screen. No network, account or transaction dependencies. */
public final class SettingsActivity extends Activity {
    private TextView summary;
    @Override protected void attachBaseContext(Context c){super.attachBaseContext(ThemeRuntime.wrap(c));}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(0xffffe600);
        getWindow().setNavigationBarColor(MenuUi.background());
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            |(ThemeRuntime.isDark()?0:View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(MenuUi.background());
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setBackgroundColor(0xffffe600);
        ImageView back=new ImageView(this);back.setImageResource(MenuUi.resource(this,"drawable","abc_ic_ab_back_material"));
        back.setImageTintList(android.content.res.ColorStateList.valueOf(0xff333333));back.setPadding(MenuUi.dp(this,16),MenuUi.dp(this,16),MenuUi.dp(this,16),MenuUi.dp(this,16));back.setContentDescription("Volver");
        header.addView(back,new LinearLayout.LayoutParams(MenuUi.dp(this,56),MenuUi.dp(this,56)));back.setOnClickListener(v->finish());
        TextView title=MenuUi.text(this,"Morphe",20,0xff333333);header.addView(title,new LinearLayout.LayoutParams(-1,-2));root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(MenuUi.background());LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setBackgroundColor(MenuUi.surface());
        MenuUi.heading(content,"Apariencia");
        LinearLayout preference=new LinearLayout(this);preference.setGravity(Gravity.CENTER_VERTICAL);
        preference.setPadding(MenuUi.dp(this,20),MenuUi.dp(this,16),MenuUi.dp(this,20),MenuUi.dp(this,16));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(MenuUi.text(this,"Tema oscuro",16,MenuUi.primary()));
        summary=MenuUi.text(this,"",14,MenuUi.secondary());summary.setPadding(0,MenuUi.dp(this,6),0,0);labels.addView(summary);
        preference.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        try {
            Class<?> switchClass=Class.forName("com.mercadolibre.android.andesui.switchandes.AndesSwitch");
            Class<?> statusClass=Class.forName("com.mercadolibre.android.andesui.switchandes.status.AndesSwitchStatus");
            Class<?> listenerClass=Class.forName("com.mercadolibre.android.andesui.switchandes.a");
            Object status=statusClass.getField(ThemeRuntime.requestedDark(this)?"CHECKED":"UNCHECKED").get(null);
            Class<?> alignClass=Class.forName("com.mercadolibre.android.andesui.switchandes.align.AndesSwitchAlign");
            Class<?> typeClass=Class.forName("com.mercadolibre.android.andesui.switchandes.type.AndesSwitchType");
            View toggle=(View)switchClass.getConstructor(Context.class,String.class,alignClass,typeClass,statusClass)
                .newInstance(this,"",switchClass.getField("q").get(null),switchClass.getField("r").get(null),status);
            toggle.setContentDescription("Tema oscuro");
            Object listener=Proxy.newProxyInstance(listenerClass.getClassLoader(),new Class<?>[]{listenerClass},(p,m,args)->{
                if(m.getName().equals("onStatusChange")){
                    boolean selected=args[0].toString().equals("CHECKED");
                    if(!ThemeRuntime.preferences(this).edit().putBoolean("dark_theme",selected).commit())throw new IllegalStateException("Unable to save theme");
                    updateSummary();showRestart();return null;
                }
                if(m.getName().equals("hashCode"))return System.identityHashCode(p);
                if(m.getName().equals("equals"))return p==args[0];
                if(m.getName().equals("toString"))return "Morphe theme listener";
                return null;
            });
            switchClass.getMethod("setOnStatusChangeListener",listenerClass).invoke(toggle,listener);
            preference.addView(toggle,new LinearLayout.LayoutParams(-2,-2));
            preference.setOnClickListener(v->{try {
                boolean next=!ThemeRuntime.requestedDark(this);
                switchClass.getMethod("setStatus",statusClass).invoke(toggle,statusClass.getField(next?"CHECKED":"UNCHECKED").get(null));
                // Andes programmatic setters do not guarantee listener dispatch.
                if(ThemeRuntime.requestedDark(this)!=next){ThemeRuntime.preferences(this).edit().putBoolean("dark_theme",next).commit();updateSummary();showRestart();}
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}});
        } catch(ReflectiveOperationException e){throw new IllegalStateException("ML Andes Switch API changed",e);}
        content.addView(preference);updateSummary();MenuUi.divider(content);
        MenuUi.heading(content,"Información");
        TextView info=MenuUi.text(this,"Mercado Libre · Morphe\nTema oscuro y ajustes locales",14,MenuUi.secondary());
        info.setPadding(MenuUi.dp(this,20),MenuUi.dp(this,8),MenuUi.dp(this,20),MenuUi.dp(this,24));content.addView(info);
        scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));setContentView(root);
    }
    private void updateSummary(){boolean next=ThemeRuntime.requestedDark(this);summary.setText(next==ThemeRuntime.isDark()?(next?"Activado":"Desactivado"):"Se aplicará al reiniciar la app");}
    private void showRestart(){
        LinearLayout message=new LinearLayout(this);message.setOrientation(LinearLayout.VERTICAL);
        message.setPadding(MenuUi.dp(this,24),MenuUi.dp(this,24),MenuUi.dp(this,24),MenuUi.dp(this,8));
        message.addView(MenuUi.text(this,"Reinicio requerido",20,MenuUi.primary()));
        TextView body=MenuUi.text(this,"Reinicia la app para que los cambios sean aplicados",16,MenuUi.secondary());
        body.setPadding(0,MenuUi.dp(this,16),0,MenuUi.dp(this,16));message.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(message)
            .setNegativeButton("Cancelar",(d,w)->{}).setPositiveButton("Reiniciar",(d,w)->restart()).create();
        dialog.setOnShowListener(d->{GradientDrawable bg=new GradientDrawable();bg.setColor(MenuUi.surface());bg.setCornerRadius(MenuUi.dp(this,6));dialog.getWindow().setBackgroundDrawable(bg);
            for(int button:new int[]{AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_POSITIVE}){dialog.getButton(button).setAllCaps(false);dialog.getButton(button).setTextColor(0xff3483fa);dialog.getButton(button).setTypeface(MenuUi.font(this));}});
        dialog.show();
    }
    private void restart(){
        Intent launcher=getPackageManager().getLaunchIntentForPackage(getPackageName());
        if(launcher==null)throw new IllegalStateException("ML launcher missing");
        launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // Close the current task; next Activity/context and all paint dispatchers use the saved mode.
        finishAffinity();ThemeRuntime.applyPreference(getApplicationContext());startActivity(launcher);
    }
}
