package me.m3ly.hotfix;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/**
 * Build-only physical-device validator for the M3LY Android pairing/logout hotfix.
 * It is deliberately a separate package and is not the production messenger.
 */
public final class HotfixActivity extends Activity {
    private static final int BG=Color.rgb(8,7,11),SURFACE=Color.rgb(18,13,20),TEXT=Color.rgb(248,246,249),MUTED=Color.rgb(174,158,172),NEON=Color.rgb(255,42,140);
    private static final String PREFS="m3ly-hotfix",PAIR_REF="pairing_ref";
    private final PairingClient client=new PairingClient();
    private DeviceVault vault; private DeviceVault.Credentials credentials; private String pairingRef; private volatile boolean busy;
    private TextView status;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        vault=new DeviceVault(this);
        pairingRef=getSharedPreferences(PREFS,MODE_PRIVATE).getString(PAIR_REF,null);
        try{credentials=vault.read();}catch(Exception ignored){credentials=null;}
        setContentView(buildConnectView());
    }

    private View buildConnectView(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(dp(22),dp(48),dp(22),dp(30));root.setBackgroundColor(BG);scroll.addView(root);
        TextView brand=text("m3ly",38,TEXT);brand.setGravity(Gravity.CENTER);brand.setTypeface(brand.getTypeface(),android.graphics.Typeface.BOLD);root.addView(brand,matchWrap());
        TextView tag=text("HOTFIX VALIDATION · NOT PRODUCTION",10,NEON);tag.setGravity(Gravity.CENTER);tag.setLetterSpacing(.12f);tag.setPadding(0,dp(6),0,dp(28));root.addView(tag,matchWrap());
        TextView title=text("Connect this Android device",27,TEXT);title.setGravity(Gravity.CENTER);title.setTypeface(title.getTypeface(),android.graphics.Typeface.BOLD);root.addView(title,matchWrap());
        TextView description=text("Tests the real M3LY browser pairing route and real device revocation endpoint without replacing the production app.",14,MUTED);description.setGravity(Gravity.CENTER);description.setPadding(dp(8),dp(10),dp(8),dp(24));root.addView(description,matchWrap());
        Button connect=button("Continue in browser",true);connect.setOnClickListener(v->beginPairing());root.addView(connect,new LinearLayout.LayoutParams(-1,dp(54)));
        status=text(pairingRef==null?"Ready to start a fresh secure pairing.":"A pairing request is waiting. Return after browser approval.",12,MUTED);status.setGravity(Gravity.CENTER);status.setPadding(dp(4),dp(18),dp(4),0);root.addView(status,matchWrap());
        return scroll;
    }

    private View buildLoggedInView(String identityLine){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(12),dp(12),dp(20));root.setBackgroundColor(BG);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),dp(6),dp(6),dp(6));bar.setBackgroundColor(SURFACE);
        TextView brand=text("M3LY",18,TEXT);brand.setTypeface(brand.getTypeface(),android.graphics.Typeface.BOLD);bar.addView(brand,new LinearLayout.LayoutParams(-2,dp(44)));
        TextView line=text(identityLine,12,MUTED);line.setSingleLine(true);line.setPadding(dp(10),0,dp(8),0);bar.addView(line,new LinearLayout.LayoutParams(0,dp(44),1));
        Button settings=button("Settings",false);settings.setOnClickListener(v->openBrowser("https://m3ly.me/settings.html"));bar.addView(settings,new LinearLayout.LayoutParams(dp(82),dp(42)));
        Button logout=button("Logout",false);logout.setContentDescription("Log out of this Android validation device");logout.setOnClickListener(v->confirmLogout());bar.addView(logout,new LinearLayout.LayoutParams(dp(78),dp(42)));
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        TextView ok=text("✓ Device pairing verified",22,NEON);ok.setGravity(Gravity.CENTER);ok.setTypeface(ok.getTypeface(),android.graphics.Typeface.BOLD);ok.setPadding(dp(12),dp(90),dp(12),dp(12));root.addView(ok,matchWrap());
        TextView copy=text("This proves the production pairing route accepted this device. Use the Logout button in the header to verify server revocation and local credential deletion.",14,MUTED);copy.setGravity(Gravity.CENTER);copy.setPadding(dp(22),0,dp(22),dp(18));root.addView(copy,matchWrap());
        status=text("Pairing complete. Logout is ready to test.",12,TEXT);status.setGravity(Gravity.CENTER);root.addView(status,matchWrap());
        return root;
    }

    private void beginPairing(){
        if(busy)return;busy=true;status.setText("Creating a secure device pairing…");
        new Thread(()->{try{
            credentials=vault.loadOrCreate(new SecureRandom());
            PairingClient.Start start=client.start(credentials);
            pairingRef=start.pairingRef;
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(PAIR_REF,pairingRef).apply();
            runOnUiThread(()->{busy=false;status.setText("Approve this device in your browser, then return here.");openBrowser(start.browserUrl);});
        }catch(Exception error){runOnUiThread(()->{busy=false;status.setText("Pairing could not start: "+safe(error));});}}).start();
    }

    @Override protected void onResume(){
        super.onResume();
        if(pairingRef==null||credentials==null||busy)return;
        busy=true;
        new Thread(()->{try{
            PairingClient.Bound claimed=client.claim(credentials,pairingRef);
            PairingClient.Bound verified=client.me(credentials);
            if(!claimed.principalRef.equals(verified.principalRef)||!claimed.deviceRef.equals(verified.deviceRef))throw new IOException("binding_verification_mismatch");
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().remove(PAIR_REF).apply();pairingRef=null;
            runOnUiThread(()->{busy=false;setContentView(buildLoggedInView(verified.identityLine));});
        }catch(IOException waiting){runOnUiThread(()->{busy=false;if(status!=null)status.setText("Waiting for browser approval. Approve the device there, then return to M3LY.");});}
        catch(Exception error){runOnUiThread(()->{busy=false;if(status!=null)status.setText("Binding verification failed: "+safe(error));});}}).start();
    }

    private void confirmLogout(){
        new AlertDialog.Builder(this).setTitle("Log out of this M3LY device?").setMessage("The live M3LY backend will revoke this validation device, then its local bearer credential will be deleted.").setNegativeButton("Cancel",null).setPositiveButton("Logout",(d,w)->performLogout()).show();
    }

    private void performLogout(){
        if(busy||credentials==null)return;busy=true;status.setText("Revoking this device…");
        new Thread(()->{try{
            try{client.logout(credentials);}catch(IOException e){if(!"unauthorized".equals(e.getMessage()))throw e;}
            vault.delete();credentials=null;pairingRef=null;getSharedPreferences(PREFS,MODE_PRIVATE).edit().clear().apply();
            runOnUiThread(()->{busy=false;setContentView(buildConnectView());status.setText("✓ Logout verified. Server device revoked and local credential deleted.");});
        }catch(Exception error){runOnUiThread(()->{busy=false;status.setText("Logout failed safely: "+safe(error));});}}).start();
    }

    private void openBrowser(String url){try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url));i.addCategory(Intent.CATEGORY_BROWSABLE);startActivity(i);}catch(ActivityNotFoundException e){status.setText("No browser is available.");}}
    private Button button(String value,boolean primary){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextColor(TEXT);b.setTextSize(12);b.setMinWidth(0);b.setPadding(dp(6),0,dp(6),0);android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setCornerRadius(dp(12));g.setColor(primary?NEON:SURFACE);b.setBackground(g);return b;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(-1,-2);}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static String safe(Exception e){String m=e.getMessage();return m!=null&&m.matches("^[A-Za-z0-9_ .:-]{1,100}$")?m:"operation_failed";}

    static final class PairingClient {
        static final class Start {final String pairingRef,browserUrl;Start(String r,String u){pairingRef=r;browserUrl=u;}}
        static final class Bound {final String principalRef,identityLine,deviceRef;Bound(String p,String l,String d){principalRef=p;identityLine=l;deviceRef=d;}}
        Start start(DeviceVault.Credentials c)throws IOException{
            try{
                JSONObject body=new JSONObject().put("device_ref",c.deviceRef).put("device_secret",c.deviceSecret).put("platform","android");
                JSONObject out=request("POST",BuildConfig.M3LY_API_BASE+"/v1/device-pairings",body,null,201);
                String ref=out.optString("pairing_ref","");String proof=out.optString("pairing_proof","");
                if(!ref.matches("^[0-9a-fA-F-]{36}$")||!proof.matches("^[A-Za-z0-9_-]{43}$"))throw new IOException("invalid_pairing_response");
                return new Start(ref,"https://m3ly.me/pair.html#pairing_ref="+ref+"&pairing_proof="+proof);
            }catch(JSONException e){throw new IOException("invalid_pairing_json",e);}
        }
        Bound claim(DeviceVault.Credentials c,String ref)throws IOException{return bound(request("POST",BuildConfig.M3LY_API_BASE+"/v1/device-pairings/"+ref+"/claim",new JSONObject(),c.deviceSecret,200));}
        Bound me(DeviceVault.Credentials c)throws IOException{return bound(request("GET",BuildConfig.M3LY_API_BASE+"/v1/me",null,c.deviceSecret,200));}
        void logout(DeviceVault.Credentials c)throws IOException{request("POST",BuildConfig.M3LY_DEVICE_LOGOUT_URL,new JSONObject(),c.deviceSecret,200);}
        private Bound bound(JSONObject out)throws IOException{String line=out.optString("identity_line","");String principal=out.optString("principal_ref","");String device=out.optString("device_ref","");if(!line.startsWith("m3ly:")||!principal.startsWith("p_")||!device.startsWith("d_"))throw new IOException("invalid_identity_response");return new Bound(principal,line,device);}
        private JSONObject request(String method,String target,JSONObject body,String bearer,int expected)throws IOException{
            URL url=new URL(target);if(!"https".equalsIgnoreCase(url.getProtocol()))throw new IOException("cleartext_forbidden");HttpURLConnection c=(HttpURLConnection)url.openConnection();
            try{c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setUseCaches(false);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Cache-Control","no-store");if(bearer!=null)c.setRequestProperty("Authorization","Bearer "+bearer);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}int status=c.getResponseCode();InputStream in=status>=200&&status<300?c.getInputStream():c.getErrorStream();String text=read(in);JSONObject result;try{result=new JSONObject(text);}catch(JSONException e){throw new IOException("invalid_json_response",e);}if(status!=expected)throw new IOException(result.optString("error","http_"+status));return result;}finally{c.disconnect();}
        }
        private static String read(InputStream in)throws IOException{if(in==null)return"{}";try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n,total=0;while((n=source.read(b))!=-1){total+=n;if(total>65536)throw new IOException("response_too_large");out.write(b,0,n);}return out.toString(StandardCharsets.UTF_8.name());}}
    }

    static final class DeviceVault {
        static final class Credentials {final String deviceRef,deviceSecret;Credentials(String r,String s){deviceRef=r;deviceSecret=s;}}
        private static final String KS="AndroidKeyStore",ALIAS="m3ly.hotfix.device.v1",CIPHER="AES/GCM/NoPadding";private static final byte[] AAD="m3ly:hotfix-device:v1".getBytes(StandardCharsets.UTF_8);private final AtomicFile file;
        DeviceVault(Context context){File dir=new File(context.getNoBackupFilesDir(),"m3ly-hotfix");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("vault_unavailable");file=new AtomicFile(new File(dir,"device.v1"));}
        Credentials loadOrCreate(SecureRandom random)throws Exception{Credentials existing=read();if(existing!=null)return existing;byte[] r=new byte[24],s=new byte[32];random.nextBytes(r);random.nextBytes(s);try{Credentials c=new Credentials("d_"+url(r),url(s));write(c);return c;}finally{Arrays.fill(r,(byte)0);Arrays.fill(s,(byte)0);}}
        Credentials read()throws Exception{if(!file.getBaseFile().isFile())return null;try(DataInputStream in=new DataInputStream(new BufferedInputStream(file.openRead()))){int n=in.readInt();if(n!=12)throw new GeneralSecurityException("vault_invalid");byte[] iv=new byte[n];in.readFully(iv);n=in.readInt();if(n<16||n>512)throw new GeneralSecurityException("vault_invalid");byte[] ct=new byte[n];in.readFully(ct);try{Cipher cipher=Cipher.getInstance(CIPHER);cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,iv));cipher.updateAAD(AAD);byte[] plain=cipher.doFinal(ct);try(DataInputStream p=new DataInputStream(new ByteArrayInputStream(plain))){return new Credentials(p.readUTF(),p.readUTF());}finally{Arrays.fill(plain,(byte)0);}}finally{Arrays.fill(iv,(byte)0);Arrays.fill(ct,(byte)0);}}}
        void write(Credentials c)throws Exception{ByteArrayOutputStream buf=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(buf)){out.writeUTF(c.deviceRef);out.writeUTF(c.deviceSecret);}byte[] plain=buf.toByteArray();Cipher cipher=Cipher.getInstance(CIPHER);cipher.init(Cipher.ENCRYPT_MODE,key(true));cipher.updateAAD(AAD);byte[] iv=cipher.getIV(),ct=cipher.doFinal(plain);FileOutputStream stream=null;try{stream=file.startWrite();DataOutputStream out=new DataOutputStream(new BufferedOutputStream(stream));out.writeInt(iv.length);out.write(iv);out.writeInt(ct.length);out.write(ct);out.flush();file.finishWrite(stream);stream=null;}finally{if(stream!=null)file.failWrite(stream);Arrays.fill(plain,(byte)0);Arrays.fill(iv,(byte)0);Arrays.fill(ct,(byte)0);}}
        void delete()throws Exception{file.delete();KeyStore store=KeyStore.getInstance(KS);store.load(null);if(store.containsAlias(ALIAS))store.deleteEntry(ALIAS);}
        private SecretKey key(boolean create)throws Exception{KeyStore store=KeyStore.getInstance(KS);store.load(null);Key found=store.getKey(ALIAS,null);if(found instanceof SecretKey)return(SecretKey)found;if(!create)throw new GeneralSecurityException("vault_key_unavailable");KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,KS);gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());return gen.generateKey();}
        private static String url(byte[] value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    }
}
