package com.ychen9.pwa;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.huawei.agconnect.config.AGConnectServicesConfig;
import com.huawei.hmf.tasks.OnFailureListener;
import com.huawei.hmf.tasks.OnSuccessListener;
import com.huawei.hmf.tasks.Task;
import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.aaid.entity.AAIDResult;
import com.huawei.hms.ads.AdListener;
import com.huawei.hms.ads.AdParam;
import com.huawei.hms.ads.AudioFocusType;
import com.huawei.hms.ads.HwAds;
import com.huawei.hms.ads.banner.BannerView;
import com.huawei.hms.ads.splash.SplashAdDisplayListener;
import com.huawei.hms.ads.splash.SplashView;
import com.huawei.hms.analytics.HiAnalytics;
import com.huawei.hms.analytics.HiAnalyticsInstance;
import com.huawei.hms.analytics.HiAnalyticsTools;
import com.huawei.hms.common.ApiException;

import org.json.*;

import java.io.*;
import java.util.Arrays;

import static com.huawei.hms.analytics.type.HAEventType.SUBMITSCORE;
import static com.huawei.hms.analytics.type.HAParamType.SCORE;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private WebView myWebView;
    private View mCustomView;
    private FrameLayout customViewContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private PwaWebViewClient mWebViewClient;
    private myWebChromeClient mWebChromeClient;

    //geoloationPermissions
    private String mGeolocationOrigin;
    private GeolocationPermissions.Callback mGeolocationCallback;
    private static final int REQUEST_FINE_LOCATION=0;

    //HMS ADS
    private BannerView defaultBannerView;
    private static final int REFRESH_TIME = 30;
    private static final int AD_TIMEOUT = 5000;
    private static final int MSG_AD_TIMEOUT = 1001;
    private boolean hasPaused = false;
    private SplashView splashView;
    
//    private boolean enableSplash = false;
//    private boolean enableBannerTop = false;
//    private boolean enableBannerBot = false;
    private boolean enableSplash = false;
    private boolean enableBannerTop = false;
    private boolean enableBannerBot = false;
    private boolean chromeClientEnable = true;

    //HMS ANALYTICS
    HiAnalyticsInstance instance;

    private Handler timeoutHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (MainActivity.this.hasWindowFocus()) {
                jump();
            }
            return false;
        }
    });

    private SplashView.SplashAdLoadListener splashAdLoadListener = new SplashView.SplashAdLoadListener() {
        @Override
        public void onAdLoaded() {
            // Call this method when an ad is successfully loaded.
            Log.i(TAG, "SplashAdLoadListener onAdLoaded.");
//            Toast.makeText(MainActivity.this, getString(R.string.status_load_ad_success), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onAdFailedToLoad(int errorCode) {
            // Call this method when an ad fails to be loaded.
            Log.i(TAG, "SplashAdLoadListener onAdFailedToLoad, errorCode: " + errorCode);
//            Toast.makeText(MainActivity.this, getString(R.string.status_load_ad_fail) + errorCode, Toast.LENGTH_SHORT).show();
            jump();
        }

        @Override
        public void onAdDismissed() {
            // Call this method when the ad display is complete.
            Log.i(TAG, "SplashAdLoadListener onAdDismissed.");
//            Toast.makeText(MainActivity.this, getString(R.string.status_ad_dismissed), Toast.LENGTH_SHORT).show();
            jump();
        }
    };

    private SplashAdDisplayListener adDisplayListener = new SplashAdDisplayListener() {
        @Override
        public void onAdShowed() {
            // Call this method when an ad is displayed.
            Log.i(TAG, "SplashAdDisplayListener onAdShowed.");
        }

        @Override
        public void onAdClick() {
            // Call this method when an ad is clicked.
            Log.i(TAG, "SplashAdDisplayListener onAdClick.");
        }
    };

    private AdListener adListener = new AdListener() {
        @Override
        public void onAdLoaded() {
            // Called when an ad is loaded successfully.
//            showToast("Ad loaded.");
        }

        @Override
        public void onAdFailed(int errorCode) {
            // Called when an ad fails to be loaded.
//            showToast(String.format(Locale.ROOT, "Ad failed to load with error code %d.", errorCode));
        }

        @Override
        public void onAdOpened() {
            // Called when an ad is opened.
//            showToast(String.format("Ad opened "));
        }

        @Override
        public void onAdClicked() {
            // Called when a user taps an ad.
//            showToast("Ad clicked");
        }

        @Override
        public void onAdLeave() {
            // Called when a user has left the app.
//            showToast("Ad Leave");
        }

        @Override
        public void onAdClosed() {
            // Called when an ad is closed.
//            showToast("Ad closed");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        HwAds.init(this);

        HiAnalyticsTools.enableLog();
        instance = HiAnalytics.getInstance(this);

//        getSupportActionBar().hide();
        loadManifest();
        setDisplay(this);
        setOrientation(this);
        setName(this);
        setContentView(R.layout.activity_main);
        customViewContainer = (FrameLayout) findViewById(R.id.customViewContainer);
        myWebView = (WebView) this.findViewById(R.id.webView);
        setWebView(myWebView, savedInstanceState);
        getToken();

        if(enableBannerTop || enableBannerBot){
            loadDefaultBannerAd();
        }
        if(enableSplash){
            loadSplashAd();
        }
//        testEvent();
    }

    public boolean inCustomView() {
        return (mCustomView != null);
    }

    public void hideCustomView() {
        mWebChromeClient.onHideCustomView();
    }

    private void setDisplay(Activity activity) {
        if (this.manifestObject.optString("display").equals("fullscreen")) {
            activity.setTheme(R.style.FullscreenTheme);
        } else {
            activity.setTheme(R.style.AppTheme);
        }
    }

    private void setName(Activity activity) {
        String name = this.manifestObject.optString("name");
        if (!name.isEmpty()) {
            activity.setTitle(name);
        }
    }

    private static final String ANY = "any";
    private static final String NATURAL = "natural";
    private static final String PORTRAIT_PRIMARY = "portrait-primary";
    private static final String PORTRAIT_SECONDARY = "portrait-secondary";
    private static final String LANDSCAPE_PRIMARY = "landscape-primary";
    private static final String LANDSCAPE_SECONDARY = "landscape-secondary";
    private static final String PORTRAIT = "portrait";
    private static final String LANDSCAPE = "landscape";

    private void setOrientation(Activity activity) {
        String orientation = this.manifestObject.optString("orientation");
        switch (orientation) {
            case LANDSCAPE_PRIMARY:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            case PORTRAIT_PRIMARY:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case LANDSCAPE:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                break;
            case PORTRAIT:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                break;
            case LANDSCAPE_SECONDARY:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                break;
            case PORTRAIT_SECONDARY:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                break;
            case ANY:
            case NATURAL:
            default:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                break;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setWebView(WebView myWebView, Bundle savedInstanceState) {
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        myWebView.setWebContentsDebuggingEnabled(true);
        myWebView.setKeepScreenOn(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
//        webSettings.setBuiltInZoomControls(true);
        webSettings.setSaveFormData(true);
//        webSettings.setDomStorageEnabled(true);
        webSettings.setGeolocationEnabled(true);
        webSettings.setGeolocationDatabasePath( this.getFilesDir().getPath() );

        String start_url = this.manifestObject.optString("start_url");
        String scope = this.manifestObject.optString("scope");
        mWebViewClient = new PwaWebViewClient(start_url, scope);
        myWebView.setWebViewClient(mWebViewClient);
//        mWebViewClient = new myWebViewClient();
//        myWebView.setWebViewClient(mWebViewClient);

        //chromeClientReplace
        chromeClientEnable = true;
        if(chromeClientEnable){
            mWebChromeClient = new myWebChromeClient();
            myWebView.setWebChromeClient(mWebChromeClient);
        }

//        myWebView.loadUrl(start_url);
        if (savedInstanceState == null){
            myWebView.loadUrl(start_url);
        }else{
//            myWebView.restoreState(savedInstanceState);
            loadURL();
        }
    }

    private static final String DEFAULT_MANIFEST_FILE = "manifest.json";
    private JSONObject manifestObject;

    private void loadManifest() {
        if (this.assetExists((DEFAULT_MANIFEST_FILE))) {
            try {
                this.manifestObject = this.loadLocalManifest();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean assetExists(String asset) {
        final AssetManager assetManager = this.getResources().getAssets();
        try {
            return Arrays.asList(assetManager.list("")).contains(asset);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    private JSONObject loadLocalManifest() throws JSONException {
        try {
            InputStream inputStream = this.getResources().getAssets().open(DEFAULT_MANIFEST_FILE);
            int size = inputStream.available();
            byte[] bytes = new byte[size];
            int readBytes = inputStream.read(bytes);
            inputStream.close();
            if (readBytes > 0) {
                String jsonString = new String(bytes, "UTF-8");
                return new JSONObject(jsonString);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void loadDefaultBannerAd() {
        // Obtain BannerView based on the configuration in layout/activity_main.xml.
        //enableTopBanner
        if(enableBannerTop){
            defaultBannerView = findViewById(R.id.top_banner);
        }else{
            defaultBannerView = findViewById(R.id.bot_banner);
        }

        defaultBannerView.setAdListener(adListener);
        defaultBannerView.setBannerRefresh(REFRESH_TIME);

        AdParam adParam = new AdParam.Builder().build();
        defaultBannerView.loadAd(adParam);
    }

    private void showToast(String message) {
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
    }

    private void loadSplashAd() {
        Log.i(TAG, "Start to load ad");

        AdParam adParam = new AdParam.Builder().build();
        splashView = findViewById(R.id.splash_ad_view);
        splashView.setAdDisplayListener(adDisplayListener);

        // Set a default app launch image.
        splashView.setSloganResId(R.drawable.default_slogan);

        // Set a logo image.
        splashView.setLogoResId(R.mipmap.ic_launcher);
        // Set logo description.
        splashView.setMediaNameResId(R.string.media_name);
        // Set the audio focus type for a video splash ad.
        splashView.setAudioFocusType(AudioFocusType.NOT_GAIN_AUDIO_FOCUS_WHEN_MUTE);

        splashView.load(getString(R.string.ad_id_splash), ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, adParam, splashAdLoadListener);
        Log.i(TAG, "End to load ad");

        // Remove the timeout message from the message queue.
        timeoutHandler.removeMessages(MSG_AD_TIMEOUT);
        // Send a delay message to ensure that the app home screen can be displayed when the ad display times out.
        timeoutHandler.sendEmptyMessageDelayed(MSG_AD_TIMEOUT, AD_TIMEOUT);
    }

    private void jump() {
        Log.i(TAG, "jump hasPaused: " + hasPaused);
        if (!hasPaused) {
            hasPaused = true;
            Log.i(TAG, "jump into application");
            if(splashView!=null){
                splashView.destroyView();
                splashView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Set this parameter to true when exiting the app to ensure that the app home screen is not displayed.
     */
    @Override
    protected void onStop() {
        Log.i(TAG, "SplashActivity onStop.");
        syncWebViewCookies();
        saveURL();
        // Remove the timeout message from the message queue.
        timeoutHandler.removeMessages(MSG_AD_TIMEOUT);
        hasPaused = true;
        super.onStop();
        if (inCustomView()) {
            hideCustomView();
        }
    }

    /**
     * Call this method when returning to the splash ad screen from another screen to access the app home screen.
     */
    @Override
    protected void onRestart() {
        Log.i(TAG, "SplashActivity onRestart.");
        syncWebViewCookies();
        saveURL();
        super.onRestart();
        hasPaused = false;
        jump();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "SplashActivity onDestroy.");
        syncWebViewCookies();
        saveURL();
        super.onDestroy();
        if (splashView != null) {
            splashView.destroyView();
        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "SplashActivity onPause.");
        syncWebViewCookies();
        saveURL();
        super.onPause();
        myWebView.onPause();
        if (splashView != null) {
            splashView.pauseView();
        }
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "SplashActivity onResume.");
        syncWebViewCookies();
        super.onResume();
        myWebView.onResume();
        if (splashView != null) {
            splashView.resumeView();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState )
    {
        myWebView.saveState(outState);
        super.onSaveInstanceState(outState);

    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState)
    {
        myWebView.restoreState(savedInstanceState);
        super.onRestoreInstanceState(savedInstanceState);
    }

//    private void testEvent() {
//        // Report score by using SUBMITSCORE Event
//        // Initiate Parameters
//        Log.d(TAG, "testEvent");
//        Bundle bundle = new Bundle();
//        bundle.putString("TEST_BUNDLE_KEY", "TEST_BUNDLE_VALUE");
//
//        // Report a preddefined Event
//        instance.onEvent("TEST_ONEVENT", bundle);
//
//        Bundle bundle2 = new Bundle();
//        bundle2.putLong(SCORE, 123456789);
//
//        // Report a preddefined Event
//        instance.onEvent(SUBMITSCORE, bundle2);
//    }

    private void getToken() {
        new Thread() {
            @Override
            public void run() {
                try {
                    // read from agconnect-services.json
//                    String appId = "103400103";
                    String appId = AGConnectServicesConfig.fromContext(MainActivity.this).getString("client/app_id");
                    String token = HmsInstanceId.getInstance(MainActivity.this).getToken(appId, "HCM");
                    Log.i(TAG, "get token:" + token);
//                    if(!TextUtils.isEmpty(token)) {
//                        sendRegTokenToServer(token);
//                    }
                } catch (ApiException e) {
                    Log.e(TAG, "get token failed, " + e);
                }
            }
        }.start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && myWebView.canGoBack()) {
            myWebView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    class myWebChromeClient extends WebChromeClient {
        private Bitmap mDefaultVideoPoster;
        private View mVideoProgressView;
        private static final String TAG = "WebChromeClient";

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin,
                                                       GeolocationPermissions.Callback callback) {
            // location permission coming from this manifest will be valid
            // for devices with API_VERSION < 23.
            // For API 23 and above, check for permissions and ask if not granted

            String perm = android.Manifest.permission.ACCESS_FINE_LOCATION;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                    ContextCompat.checkSelfPermission(MainActivity.this, perm) == PackageManager.PERMISSION_GRANTED) {
                // we're on SDK < 23 OR user has already granted permission
                callback.invoke(origin, true, false);
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, perm)) {
                    // ask the user for permission
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] {perm}, REQUEST_FINE_LOCATION);

                    // we will use these when user responds
                    mGeolocationOrigin = origin;
                    mGeolocationCallback = callback;
                }
            }
        }

        @Override
        public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {
            hideSystemUI();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            onShowCustomView(view, callback);    //To change body of overridden methods use File | Settings | File Templates.
        }

        @Override
        public void onShowCustomView(View view,CustomViewCallback callback) {
            Log.d(TAG, "onShowCustomView");

            hideSystemUI();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            // if a view already exists then immediately terminate the new one
            if (mCustomView != null) {
                callback.onCustomViewHidden();
                return;
            }
            mCustomView = view;
            myWebView.setVisibility(View.GONE);
            customViewContainer.setVisibility(View.VISIBLE);
            customViewContainer.addView(view);
            customViewCallback = callback;


        }

        @Override
        public View getVideoLoadingProgressView() {

            if (mVideoProgressView == null) {
                LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
                mVideoProgressView = inflater.inflate(R.layout.video_progress, null);
            }
            return mVideoProgressView;
        }

        @Override
        public void onHideCustomView() {
            Log.d(TAG, "onHideCustomView");
            super.onHideCustomView();    //To change body of overridden methods use File | Settings | File Templates.
            if (mCustomView == null)
                return;

            showSystemUI();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            myWebView.setVisibility(View.VISIBLE);
            customViewContainer.setVisibility(View.GONE);

            // Hide the custom view.
            mCustomView.setVisibility(View.GONE);

            // Remove the custom view from its container.
            customViewContainer.removeView(mCustomView);
            customViewCallback.onCustomViewHidden();

            mCustomView = null;
        }
        public Bitmap getDefaultVideoPoster()
        {
            if (mCustomView == null) {
                return null;
            }
            return BitmapFactory.decodeResource(getApplicationContext().getResources(), 2130837573);
        }

        private void hideSystemUI() {
            // Enables regular immersive mode.
            // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
            // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE
                            // Set the content to appear under the system bars so that the
                            // content doesn't resize when the system bars hide and show.
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            // Hide the nav bar and status bar
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }

        // Shows the system bars by removing all the flags
        // except for the ones that make the content appear under the system bars.
        private void showSystemUI() {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            Log.d("CONSOLE", consoleMessage.message() + " -- From line "
                    + consoleMessage.lineNumber() + " of "
                    + consoleMessage.sourceId());
            return super.onConsoleMessage(consoleMessage);
        }

    }

    private void syncWebViewCookies(){
        if(mWebViewClient!=null){
            try{
                mWebViewClient.syncCookies();
            }catch(Exception e){
                Log.d("syncWebViewCookies", "error >> " + e);
            }
        }
    }

    private void saveURL(){
        SharedPreferences prefs = this.getApplicationContext().
                getSharedPreferences(this.getPackageName(), Activity.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString("lastUrl",myWebView.getUrl());
        edit.commit();   // can use edit.apply() but in this case commit is better
    }

    private void loadURL(){
        if(myWebView != null) {
            SharedPreferences prefs = this.getApplicationContext().
                    getSharedPreferences(this.getPackageName(), Activity.MODE_PRIVATE);
            String s = prefs.getString("lastUrl","");
            if(!s.equals("")) {
                myWebView.loadUrl(s);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_FINE_LOCATION:
                boolean allow = false;
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // user has allowed this permission
                    allow = true;
                }
                if (mGeolocationCallback != null) {
                    // call back to web chrome client
                    mGeolocationCallback.invoke(mGeolocationOrigin, allow, false);
                }
                break;
        }
    }
}
