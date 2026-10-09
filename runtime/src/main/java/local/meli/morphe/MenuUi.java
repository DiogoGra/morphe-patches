package local.meli.morphe;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Reuses the original Andes menu layout, typography, dimensions and ML vector icon. */
public final class MenuUi {
    private static final java.util.Set<View> watched=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View,Boolean>());
    private MenuUi() {}
    public static int resource(Context c, String type, String name) {
        int id=c.getResources().getIdentifier(name,type,c.getPackageName());
        if(id==0) throw new IllegalStateException("Missing ML resource: "+type+"/"+name);
        return id;
    }
    public static int dp(Context c, int n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    public static int primary() {return ThemeRuntime.isDark()?0xfff2f3f5:0xff333333;}
    public static int secondary() {return ThemeRuntime.isDark()?0xffc7cbd2:0xff666666;}
    public static int surface() {return ThemeRuntime.isDark()?0xff1b1e25:0xffffffff;}
    public static int background() {return ThemeRuntime.isDark()?0xff111318:0xffeeeeee;}
    public static View row(Context c, ViewGroup parent, String label, String icon) {
        View row=LayoutInflater.from(c).inflate(resource(c,"layout","ui_sections_menu_row_tag"),parent,false);
        TextView text=row.findViewById(resource(c,"id","drawer_navigation_tag_text"));
        text.setText(label); text.setTextColor(primary());
        ImageView image=row.findViewById(resource(c,"id","drawer_navigation_tag_icon"));
        image.setImageResource(resource(c,"drawable",icon));
        image.setImageTintList(ColorStateList.valueOf(secondary()));
        row.setBackgroundColor(surface());
        return row;
    }
    public static Typeface font(Context c) {
        View row=row(c,new LinearLayout(c),"", "amount_screen_ic_gear");
        return ((TextView)row.findViewById(resource(c,"id","drawer_navigation_tag_text"))).getTypeface();
    }
    public static TextView text(Context c,String value,float size,int color) {
        TextView view=new TextView(c);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setTypeface(font(c));
        return view;
    }
    public static void divider(LinearLayout parent) {
        Context c=parent.getContext(); View line=new View(c);
        line.setBackgroundColor(ThemeRuntime.isDark()?0xff353a45:0xffdddddd);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(c,1));
        params.setMargins(dp(c,20),dp(c,12),dp(c,20),dp(c,8));parent.addView(line,params);
    }
    public static void heading(LinearLayout parent,String label) {
        Context c=parent.getContext();TextView title=text(c,label,14,secondary());
        title.setPadding(dp(c,20),dp(c,8),dp(c,20),dp(c,8));parent.addView(title);
    }
    public static void attach(LinearLayout list) {
        if(list==null || list.getId()!=resource(list.getContext(),"id","section_menu_container_list"))return;
        if(list.findViewWithTag("morphe-menu-section")!=null)return;
        Context c=list.getContext();LinearLayout section=new LinearLayout(c);
        section.setOrientation(LinearLayout.VERTICAL);section.setTag("morphe-menu-section");section.setBackgroundColor(surface());
        divider(section);heading(section,"Morphe");
        View entry=row(c,section,"Ajustes de Morphe","amount_screen_ic_gear");
        entry.setContentDescription("Ajustes de Morphe");
        entry.setOnClickListener(v->c.startActivity(new Intent(c,SettingsActivity.class)));
        section.addView(entry);
        // After account header + Inicio/Ayuda, before the existing section divider.
        int index=list.getChildCount();
        for(int i=0;i<list.getChildCount();i++) {
            if(list.getChildAt(i).findViewById(resource(c,"id","drawer_navigation_tag_text"))!=null) {
                index=i+1;
                if(i+1<list.getChildCount() && list.getChildAt(i+1).findViewById(resource(c,"id","drawer_navigation_tag_text"))!=null)index=i+2;
                break;
            }
        }
        list.addView(section,index,new LinearLayout.LayoutParams(-1,-2));
    }
    public static void watch(android.app.Activity activity) {
        final View decor=activity.getWindow().getDecorView();
        if(!watched.add(decor))return;
        final int menuId=resource(activity,"id","section_menu_container_list");
        decor.getViewTreeObserver().addOnPreDrawListener(()->{
            attachVisible(decor,menuId);
            return true;
        });
    }
    private static void attachVisible(View view,int menuId) {
        if(!view.isShown())return;
        if(view.getId()==menuId && view instanceof LinearLayout){attach((LinearLayout)view);return;}
        // The legacy hidden drawer and the visible Más menu can share this resource ID.
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)attachVisible(group.getChildAt(i),menuId);}
    }
}
