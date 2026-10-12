package local.meli.morphe;

import android.content.Context;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.lang.ref.WeakReference;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.json.JSONArray;

/** Normalizes native tab definitions before ML builds views and registers routes. */
public final class NavigationRuntime {
    private static volatile Context application;
    private static volatile boolean available;
    private static volatile boolean enabled=true;
    private static final List<WeakReference<Object>> stockResponses=new ArrayList<>();
    private NavigationRuntime() {}

    public static void rememberContext(Context context) {
        if(context != null && !available) {
            application=context.getApplicationContext();
            applyPreference(context);available=true;
        }
    }

    public static boolean isAvailable() { return available; }
    public static boolean isEnabled() { return enabled; }
    public static boolean requestedEnabled(Context context) {
        return ThemeRuntime.preferences(context).getBoolean("purchases_center",true);
    }
    public static void applyPreference(Context context) { enabled=requestedEnabled(context); }

    public static void normalizeResponse(Object response) {
        if(response == null || !enabled) return;
        try {
            Class<?> responseClass=response.getClass();
            List<?> definitions=(List<?>)responseClass.getMethod("getDefinitions").invoke(response);
            if(definitions == null || definitions.isEmpty()) return;
            Object purchases=null;
            int videosCount=0, purchasesCount=0;
            for(Object definition:definitions) {
                if(definition == null) return;
                String id=id(definition);
                if("videos".equals(id)) videosCount++;
                if("purchases".equals(id)) { purchases=definition; purchasesCount++; }
            }
            // Ambiguous remote configurations remain intact rather than selecting a guessed tab.
            if(videosCount>1 || purchasesCount>1 || (videosCount==0 && purchasesCount==0)) return;
            boolean stock=purchases==null;
            if(stock) purchases=stockPurchases(responseClass);
            if(purchases==null) return;
            List<Object> reordered=new ArrayList<>();
            for(Object definition:definitions) {
                String id=id(definition);
                if(!"videos".equals(id) && !"purchases".equals(id)) reordered.add(definition);
            }
            reordered.add((reordered.size()+1)/2,purchases);
            responseClass.getMethod("setDefinitions",List.class).invoke(response,reordered);
            if(stock) synchronized(stockResponses) { stockResponses.add(new WeakReference<>(response)); }
        } catch(Exception failure) {
            // Leave the original response in place if a future model/asset differs.
            Log.w("MorpheNavigation","Tab customization skipped: "+failure.getClass().getSimpleName());
        }
    }

    public static void finishItems(java.util.Collection<?> items,Object response) {
        boolean stock=false;
        synchronized(stockResponses) {
            for(java.util.Iterator<WeakReference<Object>> it=stockResponses.iterator();it.hasNext();) {
                Object value=it.next().get();
                if(value==null) it.remove(); else if(value==response) stock=true;
            }
        }
        if(!stock || items==null) return;
        try {
            for(Object item:items) if("purchases".equals(item.getClass().getMethod("getId").invoke(item))) {
                // e.d drops Definitions.presentationMode in this APK. Restore
                // push only for our bundled fallback, never a server-supplied tab.
                item.getClass().getMethod("setPresentationMode",String.class).invoke(item,"push");
            }
        } catch(Exception failure) {
            Log.w("MorpheNavigation","Tab presentation skipped: "+failure.getClass().getSimpleName());
        }
    }

    private static String id(Object definition) throws Exception {
        return (String)definition.getClass().getMethod("getTabId").invoke(definition);
    }

    private static Object stockPurchases(Class<?> responseClass) throws Exception {
        Context context=application;
        if(context==null) return null;
        // Read the same per-user menu preferences through ML's own accessor.
        // Never persist, synthesize a commerce URL, or remove its query parameters.
        Class<?> menus=Class.forName("com.mercadolibre.android.ui_sections.utils.b");
        SharedPreferences preferences=(SharedPreferences)menus.getMethod("a",Context.class).invoke(null,context);
        String menu=preferences.getString("navigation_menu_ml",null);
        String destination=menu==null?null:purchaseLink(new JSONObject(menu));
        if(destination==null) return null;
        byte[] data;
        try(InputStream input=context.getAssets().open("tabbar_config_default.json");
            ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096]; int count;
            while((count=input.read(buffer))!=-1) output.write(buffer,0,count);
            data=output.toByteArray();
        }
        // Use ML's existing parser so its model, field naming, icon and destination
        // match the native bundled purchases definition. This does not persist any changes.
        Class<?> parser=Class.forName("com.mercadolibre.android.navigation_manager.tabbar.e");
        Object defaults=parser.getMethod("e",String.class).invoke(null,new String(data,"UTF-8"));
        if(defaults==null) return null;
        List<?> definitions=(List<?>)responseClass.getMethod("getDefinitions").invoke(defaults);
        if(definitions!=null) for(Object definition:definitions) if("purchases".equals(id(definition))) {
            // ML's native push presentation launches SafeIntent and keeps the
            // normal deep-link/authentication handler, like the menu entry.
            // The bundled definition otherwise embeds a guest WebView directly.
            definition.getClass().getMethod("setPresentationMode",String.class).invoke(definition,"push");
            definition.getClass().getMethod("setRootDeeplink",String.class).invoke(definition,destination);
            return definition;
        }
        return null;
    }

    private static String purchaseLink(Object node) throws Exception {
        if(node instanceof JSONObject) {
            JSONObject object=(JSONObject)node;
            if("purchases".equals(object.optString("id")) && "deeplink".equals(object.optString("type"))) {
                JSONObject data=object.optJSONObject("data");
                if(data==null || !"push".equals(data.optString("mode")) || data.optBoolean("external",false)) return null;
                String link=data.optString("url");
                android.net.Uri uri=android.net.Uri.parse(link);
                if("meli".equals(uri.getScheme()) && ("webview".equals(uri.getHost()) || "my_purchases".equals(uri.getHost()))) return link;
                return null;
            }
            java.util.Iterator<String> keys=object.keys();
            while(keys.hasNext()) { String found=purchaseLink(object.opt(keys.next())); if(found!=null) return found; }
        } else if(node instanceof JSONArray) {
            JSONArray array=(JSONArray)node;
            for(int i=0;i<array.length();i++) { String found=purchaseLink(array.opt(i)); if(found!=null) return found; }
        }
        return null;
    }
}
