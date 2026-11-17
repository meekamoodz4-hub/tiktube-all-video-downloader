package com.meekamoodz.tiktube;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import kotlin.Unit;

public class MainActivity extends Activity {
    private static final String PRIVACY_URL = "https://sites.google.com/view/droptechdevelopers/spacex-zoom-privacy-policy?authuser=5";
    private static final String BANNER_ID = "ca-app-pub-1083885756946416/2521634741";
    private static final String INTERSTITIAL_ID = "ca-app-pub-1083885756946416/7310926990";
    private static final String APP_OPEN_ID = "ca-app-pub-1083885756946416/1130416042";
    private static final long AD_WAIT_TIMEOUT_MS = 5000L;
    private static final long APP_OPEN_COOLDOWN_MS = 4L * 60L * 60L * 1000L;
    private static final long DOWNLOADER_UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private String selectedPlatform = "";
    private EditText linkInput;
    private Spinner qualitySpinner;
    private TextView status;
    private Button downloadButton;
    private LinearLayout contentContainer;
    private AdView bannerAdView;
    private InterstitialAd interstitialAd;
    private AppOpenAd appOpenAd;
    private boolean engineReady = false;
    private boolean updatingDownloader = false;
    private boolean adsInitialized = false;
    private boolean downloading = false;
    private boolean waitingForInterstitial = false;
    private boolean interstitialGateResolved = false;
    private boolean activityResumed = false;
    private boolean appOpenShowing = false;
    private boolean appOpenShownThisResume = false;
    private boolean appOpenEligible = false;
    private String pendingDownloadUrl = null;
    private String pendingDownloadFormat = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable adTimeoutRunnable = () -> {
        if (waitingForInterstitial && !interstitialGateResolved) {
            Toast.makeText(MainActivity.this, "Ad unavailable. Continuing download.", Toast.LENGTH_SHORT).show();
            continuePendingDownload();
        }
    };

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button actionButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.rgb(13, 18, 35) : Color.WHITE);
        button.setBackground(rounded(primary ? Color.rgb(91, 213, 190) : Color.rgb(44, 55, 82), 14));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        return button;
    }

    private void addSpace(LinearLayout box, int height) {
        View spacer = new View(this);
        box.addView(spacer, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private File downloadsFolder() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TikTube");
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.rgb(13, 18, 35));
        getWindow().setNavigationBarColor(Color.rgb(13, 18, 35));

        SharedPreferences prefs = getSharedPreferences("tiktube_ads", MODE_PRIVATE);
        int priorLaunches = prefs.getInt("launch_count", 0);
        appOpenEligible = priorLaunches > 0;
        prefs.edit().putInt("launch_count", priorLaunches + 1).apply();

        try {
            YoutubeDL.getInstance().init(getApplication());
            FFmpeg.getInstance().init(getApplication());
            engineReady = true;
        } catch (Exception e) {
            engineReady = false;
        }

        showSplash();
        handler.postDelayed(() -> { buildInterface(); handleIncomingIntent(getIntent()); }, 1500L);
        handler.postDelayed(this::checkForDownloaderUpdateAutomatically, 2000L);
        initializeConsentAndAds();
    }

    private void showSplash() {
        LinearLayout splash = new LinearLayout(this);
        splash.setOrientation(LinearLayout.VERTICAL); splash.setGravity(Gravity.CENTER);
        splash.setPadding(dp(24),dp(24),dp(24),dp(24));
        splash.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(55,32,112),Color.rgb(10,13,27),Color.rgb(8,42,62)}));
        TextView logo=label("▶",58,Color.rgb(116,244,221),true); logo.setGravity(Gravity.CENTER);
        GradientDrawable orb=new GradientDrawable(); orb.setShape(GradientDrawable.OVAL); orb.setColor(Color.rgb(31,47,79)); orb.setStroke(dp(2),Color.rgb(116,244,221));
        logo.setBackground(orb); splash.addView(logo,new LinearLayout.LayoutParams(dp(112),dp(112)));
        TextView name=label("TIKTUBE",32,Color.WHITE,true); name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2); np.topMargin=dp(20); splash.addView(name,np);
        TextView tag=label("YOUR VIDEO TOOLKIT",12,Color.rgb(116,244,221),true); tag.setGravity(Gravity.CENTER);
        splash.addView(tag); TextView loading=label("Getting everything ready…",13,Color.LTGRAY,false); loading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(48); splash.addView(loading,lp); setContentView(splash);
    }

    private void buildInterface() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(10,13,27));
        ScrollView scroll=new ScrollView(this); LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20),dp(22),dp(20),dp(22)); contentContainer=page; scroll.addView(page); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout brand=new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.addView(label("TIKTUBE",28,Color.WHITE,true),new LinearLayout.LayoutParams(0,-2,1f));
        brand.addView(label("✦ PREMIUM VIDEO TOOLS",10,Color.rgb(116,244,221),true)); page.addView(brand);
        addSpace(page,5); page.addView(label("DOWNLOAD YOUR WAY",12,Color.rgb(163,145,255),true)); addSpace(page,18);
        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(19),dp(19),dp(19),dp(19));
        GradientDrawable hg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(62,42,119),Color.rgb(24,54,88)}); hg.setCornerRadius(dp(22)); hero.setBackground(hg);
        hero.addView(label("Your videos. Your library.",22,Color.WHITE,true)); addSpace(hero,7);
        hero.addView(label("Choose a platform to open its own link screen. Paste, clear and manage your downloads in seconds.",14,Color.rgb(222,225,245),false));
        page.addView(hero); addSpace(page,22); page.addView(label("CHOOSE A PLATFORM",12,Color.rgb(165,176,205),true)); addSpace(page,10);
        addPlatformTile(page,"♪","TikTok","Short-form videos",Color.rgb(255,92,143));
        addPlatformTile(page,"▶","YouTube","Videos you are permitted to save",Color.rgb(255,83,83));
        addPlatformTile(page,"◎","Instagram","Reels and public media where permitted",Color.rgb(221,119,226));
        addPlatformTile(page,"f","Facebook","Public videos where permitted",Color.rgb(91,153,255));
        addSpace(page,12); Button library=actionButton("▣   MY DOWNLOADS",false); page.addView(library,new LinearLayout.LayoutParams(-1,dp(50))); library.setOnClickListener(v->showDownloads());
        addSpace(page,9); Button privacy=actionButton("PRIVACY POLICY",false); page.addView(privacy,new LinearLayout.LayoutParams(-1,dp(44))); privacy.setOnClickListener(v->openPrivacyPolicy());
        bannerAdView=new AdView(this); bannerAdView.setAdUnitId(BANNER_ID); bannerAdView.setAdSize(AdSize.BANNER); root.addView(bannerAdView,new LinearLayout.LayoutParams(-1,-2)); setContentView(root);
    }

    private void addPlatformTile(LinearLayout parent,String symbol,String name,String subtitle,int accent) {
        LinearLayout tile=new LinearLayout(this); tile.setGravity(Gravity.CENTER_VERTICAL); tile.setPadding(dp(14),dp(12),dp(14),dp(12)); tile.setBackground(rounded(Color.rgb(24,29,51),18));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(76)); p.bottomMargin=dp(10); parent.addView(tile,p);
        TextView icon=label(symbol,27,accent,true); icon.setGravity(Gravity.CENTER); icon.setBackground(rounded(Color.rgb(39,39,70),15)); tile.addView(icon,new LinearLayout.LayoutParams(dp(50),dp(50)));
        LinearLayout textBox=new LinearLayout(this); textBox.setOrientation(LinearLayout.VERTICAL); textBox.setPadding(dp(13),0,dp(5),0);
        textBox.addView(label(name,16,Color.WHITE,true)); addSpace(textBox,3); textBox.addView(label(subtitle,11,Color.rgb(164,175,202),false));
        tile.addView(textBox,new LinearLayout.LayoutParams(0,-2,1f)); tile.addView(label("›",29,Color.rgb(116,244,221),true)); tile.setOnClickListener(v->openPlatformScreenWithAd(name));
    }

    private void openPlatformScreenWithAd(String platform) {
        SharedPreferences prefs = getSharedPreferences("tiktube_ads", MODE_PRIVATE);
        int actions = prefs.getInt("platform_navigation_actions", 0) + 1;
        prefs.edit().putInt("platform_navigation_actions", actions).apply();

        // Keep interstitial frequency conservative: show on every second platform-entry action.
        if (actions % 2 != 0 || !adsInitialized || interstitialAd == null) {
            showPlatformScreen(platform);
            if (adsInitialized && interstitialAd == null) loadInterstitial();
            return;
        }

        InterstitialAd ad = interstitialAd;
        interstitialAd = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                showPlatformScreen(platform);
                loadInterstitial();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                showPlatformScreen(platform);
                loadInterstitial();
            }
        });
        try {
            ad.show(this);
        } catch (Exception e) {
            showPlatformScreen(platform);
            loadInterstitial();
        }
    }

    private void showPlatformScreen(String platform) {
        selectedPlatform=platform;
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(10,13,27));
        ScrollView scroll=new ScrollView(this); LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(20),dp(22),dp(20),dp(22)); scroll.addView(page); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        Button back=actionButton("‹   BACK TO PLATFORMS",false); page.addView(back,new LinearLayout.LayoutParams(-1,dp(44))); back.setOnClickListener(v->buildInterface());
        addSpace(page,22); page.addView(label(platform+" downloader",25,Color.WHITE,true)); addSpace(page,7);
        page.addView(label("Paste a link below to prepare your download.",14,Color.rgb(177,187,211),false)); addSpace(page,18);
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(17),dp(18),dp(17),dp(18)); card.setBackground(rounded(Color.rgb(24,29,51),20)); page.addView(card);
        card.addView(label("VIDEO LINK",12,Color.rgb(165,176,205),true)); addSpace(card,8);
        linkInput=new EditText(this); linkInput.setMinLines(3); linkInput.setTextSize(15); linkInput.setHint("Paste your "+platform+" link here…"); linkInput.setHintTextColor(Color.rgb(125,139,168)); linkInput.setTextColor(Color.WHITE); linkInput.setPadding(dp(14),dp(13),dp(14),dp(13)); linkInput.setBackground(rounded(Color.rgb(12,18,36),13)); card.addView(linkInput,new LinearLayout.LayoutParams(-1,-2));
        addSpace(card,10); LinearLayout tools=new LinearLayout(this); Button paste=actionButton("▣  PASTE",true),clear=actionButton("✕  CLEAR",false);
        tools.addView(paste,new LinearLayout.LayoutParams(0,dp(46),1f)); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(46),1f); cp.leftMargin=dp(9); tools.addView(clear,cp); card.addView(tools);
        paste.setOnClickListener(v->pasteClipboardLink()); clear.setOnClickListener(v->linkInput.setText(""));
        addSpace(card,17); card.addView(label("DOWNLOAD QUALITY",12,Color.rgb(165,176,205),true)); addSpace(card,6);
        qualitySpinner=new Spinner(this); String[] choices={"Best available","1080p (Full HD)","720p (HD)","480p (SD)","360p (Low data)"};
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,choices) {
            @Override public View getView(int pos,View cv,android.view.ViewGroup par){ TextView t=(TextView)super.getView(pos,cv,par); t.setTextColor(Color.WHITE); t.setPadding(dp(12),dp(9),dp(12),dp(9)); return t; }
            @Override public View getDropDownView(int pos,View cv,android.view.ViewGroup par){ TextView t=(TextView)super.getDropDownView(pos,cv,par); t.setTextColor(Color.WHITE); t.setBackgroundColor(Color.rgb(24,29,51)); return t; }
        }; adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); qualitySpinner.setAdapter(adapter); card.addView(qualitySpinner,new LinearLayout.LayoutParams(-1,dp(48)));
        addSpace(card,15); downloadButton=actionButton("↓   DOWNLOAD VIDEO",true); card.addView(downloadButton,new LinearLayout.LayoutParams(-1,dp(53))); downloadButton.setOnClickListener(v->requestDownload());
        addSpace(page,14); status=label(engineReady?"Downloader ready. Only download content you own or have permission to save.":"Downloader engine could not start. Check your connection.",13,Color.rgb(185,196,219),false);
        status.setPadding(dp(14),dp(14),dp(14),dp(14)); status.setBackground(rounded(Color.rgb(24,29,51),13)); page.addView(status);
        AdView ad=new AdView(this); ad.setAdUnitId(BANNER_ID); ad.setAdSize(AdSize.BANNER); root.addView(ad,new LinearLayout.LayoutParams(-1,-2)); setContentView(root); if(adsInitialized) ad.loadAd(new AdRequest.Builder().build());
    }

    private void pasteClipboardLink() {
        try {
            android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(cm!=null&&cm.hasPrimaryClip()){ android.content.ClipData data=cm.getPrimaryClip(); if(data!=null&&data.getItemCount()>0){ CharSequence value=data.getItemAt(0).coerceToText(this); if(value!=null){ linkInput.setText(value.toString().trim()); linkInput.setSelection(linkInput.length()); return; } } }
            Toast.makeText(this,"Your clipboard is empty.",Toast.LENGTH_SHORT).show();
        } catch(Exception e){ Toast.makeText(this,"Could not read the clipboard.",Toast.LENGTH_SHORT).show(); }
    }

    private void showDownloader() { buildInterface(); }

    private void showDownloads() {
        if (contentContainer == null) return;
        contentContainer.removeAllViews();
        Button backHome = actionButton("‹   BACK TO PLATFORMS", false);
        contentContainer.addView(backHome, new LinearLayout.LayoutParams(-1, dp(44)));
        backHome.setOnClickListener(v -> buildInterface());
        addSpace(contentContainer, 16);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("My Downloads", 22, Color.WHITE, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button refresh = actionButton("Refresh", false);
        titleRow.addView(refresh, new LinearLayout.LayoutParams(-2, dp(42)));
        contentContainer.addView(titleRow);
        addSpace(contentContainer, 8);
        contentContainer.addView(label("Videos saved in Download/TikTube appear here. Tap Play to open a video player.", 14, Color.rgb(190, 199, 219), false));
        addSpace(contentContainer, 14);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        contentContainer.addView(list, new LinearLayout.LayoutParams(-1, -2));
        refresh.setOnClickListener(v -> populateDownloads(list));
        populateDownloads(list);
    }

    private void populateDownloads(LinearLayout list) {
        list.removeAllViews();
        File folder = downloadsFolder();
        File downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        List<File> allFound = new ArrayList<>();
        File[] inAppFolder = folder.listFiles(file -> file.isFile() && isVideoFile(file.getName()));
        if (inAppFolder != null) allFound.addAll(Arrays.asList(inAppFolder));
        File[] inDownloadsRoot = downloadsRoot.listFiles(file -> file.isFile() && isVideoFile(file.getName()));
        if (inDownloadsRoot != null) allFound.addAll(Arrays.asList(inDownloadsRoot));
        if (allFound.isEmpty()) {
            TextView empty = label("No downloaded videos found yet. Download a video first, then tap Refresh.", 14, Color.rgb(190, 199, 219), false);
            empty.setPadding(dp(16), dp(18), dp(16), dp(18));
            empty.setBackground(rounded(Color.rgb(26, 34, 57), 16));
            list.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        List<File> files = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        for (File file : allFound) {
            try {
                String path = file.getCanonicalPath();
                if (seenPaths.add(path)) files.add(file);
            } catch (Exception ignored) {
                if (!files.contains(file)) files.add(file);
            }
        }
        Collections.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File file : files) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            row.setBackground(rounded(Color.rgb(26, 34, 57), 16));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(10);
            list.addView(row, rowParams);

            row.addView(label(file.getName(), 14, Color.WHITE, true));
            addSpace(row, 5);
            row.addView(label(readableSize(file.length()) + "  •  " + android.text.format.DateFormat.format("dd MMM yyyy, HH:mm", file.lastModified()), 12, Color.rgb(160, 174, 201), false));
            addSpace(row, 10);
            Button play = actionButton("PLAY VIDEO", true);
            row.addView(play, new LinearLayout.LayoutParams(-1, dp(44)));
            play.setOnClickListener(v -> playVideo(file));
        }
    }

    private boolean isVideoFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm")
                || lower.endsWith(".mov") || lower.endsWith(".m4v") || lower.endsWith(".avi");
    }

    private String readableSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private void playVideo(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No compatible video player was found.", Toast.LENGTH_LONG).show();
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null || linkInput == null) return;
        String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (shared != null && !shared.trim().isEmpty()) linkInput.setText(shared.trim());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showDownloader();
        handleIncomingIntent(intent);
    }

    private void requestDownload() {
        if (downloading || waitingForInterstitial) return;
        if (updatingDownloader) {
            Toast.makeText(this, "The downloader is updating. Please wait a moment.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String url = linkInput == null ? "" : linkInput.getText().toString().trim();
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            Toast.makeText(this, "Please paste a valid video link beginning with http:// or https://.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!engineReady) {
            status.setText("The downloader engine did not initialize. Check your internet connection and reinstall the latest build.");
            return;
        }

        pendingDownloadUrl = url;
        pendingDownloadFormat = selectedFormat();
        waitingForInterstitial = true;
        interstitialGateResolved = false;
        downloadButton.setEnabled(false);
        downloadButton.setText("PREPARING DOWNLOAD…");
        status.setText("Preparing your download…");

        handler.removeCallbacks(adTimeoutRunnable);
        handler.postDelayed(adTimeoutRunnable, AD_WAIT_TIMEOUT_MS);

        if (interstitialAd != null) {
            showPendingInterstitial();
        } else if (adsInitialized) {
            loadInterstitial();
        } else {
            continuePendingDownload();
        }
    }

    private void loadInterstitial() {
        if (!adsInitialized) return;
        InterstitialAd.load(this, INTERSTITIAL_ID, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialAd = ad;
                        if (waitingForInterstitial && !interstitialGateResolved) showPendingInterstitial();
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        interstitialAd = null;
                        if (waitingForInterstitial && !interstitialGateResolved) continuePendingDownload();
                    }
                });
    }

    private void showPendingInterstitial() {
        if (!waitingForInterstitial || interstitialGateResolved || interstitialAd == null) return;
        final InterstitialAd ad = interstitialAd;
        interstitialAd = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                continuePendingDownload();
                loadInterstitial();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                continuePendingDownload();
                loadInterstitial();
            }
        });
        try {
            ad.show(this);
        } catch (Exception e) {
            continuePendingDownload();
            loadInterstitial();
        }
    }

    private void continuePendingDownload() {
        if (!waitingForInterstitial || interstitialGateResolved) return;
        interstitialGateResolved = true;
        waitingForInterstitial = false;
        handler.removeCallbacks(adTimeoutRunnable);
        String url = pendingDownloadUrl;
        String format = pendingDownloadFormat;
        pendingDownloadUrl = null;
        pendingDownloadFormat = null;
        if (url != null) startDownloadActual(url, format);
    }

    private String selectedFormat() {
        int selected = qualitySpinner == null ? 0 : qualitySpinner.getSelectedItemPosition();
        switch (selected) {
            case 1:
                return "best[height<=1080][ext=mp4]/best[height<=1080]/best";
            case 2:
                return "best[height<=720][ext=mp4]/best[height<=720]/best";
            case 3:
                return "best[height<=480][ext=mp4]/best[height<=480]/best";
            case 4:
                return "best[height<=360][ext=mp4]/best[height<=360]/best";
            default:
                return "best[ext=mp4]/best";
        }
    }

    private void startDownloadActual(final String url, final String format) {
        if (downloading) return;
        final File folder = downloadsFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            status.setText("Could not create Download/TikTube. Check available phone storage.");
            resetDownloadButton();
            return;
        }

        final Set<String> filesBefore = new HashSet<>();
        File[] before = folder.listFiles();
        if (before != null) for (File file : before) filesBefore.add(file.getName());

        downloading = true;
        downloadButton.setEnabled(false);
        downloadButton.setText("DOWNLOADING…");
        status.setText("Download started. Keep Tiktube open until it finishes.");

        new Thread(() -> {
            try {
                YoutubeDLRequest request = new YoutubeDLRequest(url);
                request.addOption("--no-playlist");
                request.addOption("--no-mtime");
                request.addOption("-f", format == null ? "best[ext=mp4]/best" : format);
                request.addOption("--merge-output-format", "mp4");
                request.addOption("-o", new File(folder, "%(title).80s.%(ext)s").getAbsolutePath());

                YoutubeDL.getInstance().execute(request, "TiktubeDownload", (progress, etaInSeconds, line) -> {
                    runOnUiThread(() -> {
                        if (status != null) status.setText("Downloading: " + Math.max(0, Math.min(100, Math.round(progress))) + "%\n" + line + "\nEstimated time left: " + etaInSeconds + " seconds");
                    });
                    return Unit.INSTANCE;
                });

                File[] after = folder.listFiles();
                List<File> completed = new ArrayList<>();
                if (after != null) {
                    for (File file : after) {
                        if (file.isFile() && isVideoFile(file.getName()) && (!filesBefore.contains(file.getName()) || file.lastModified() >= System.currentTimeMillis() - 120000L)) {
                            completed.add(file);
                        }
                    }
                }
                if (completed.isEmpty() && after != null) {
                    for (File file : after) if (file.isFile() && isVideoFile(file.getName())) completed.add(file);
                }

                for (File file : completed) {
                    MediaScannerConnection.scanFile(getApplicationContext(),
                            new String[]{file.getAbsolutePath()},
                            new String[]{"video/*"}, (path, uri) -> { });
                }

                runOnUiThread(() -> {
                    if (status != null) status.setText("Download complete! Your video is in Download/TikTube and should appear in Gallery after scanning.");
                    Toast.makeText(MainActivity.this, "Download complete!", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                final String message = e.getMessage() == null || e.getMessage().trim().isEmpty()
                        ? e.getClass().getSimpleName() + ". Check the link, internet connection, and available storage."
                        : e.getMessage();
                runOnUiThread(() -> {
                    if (status != null) status.setText("Download failed. The link may be private, unsupported, or blocked by the platform. Details: " + message);
                });
            } finally {
                downloading = false;
                runOnUiThread(this::resetDownloadButton);
            }
        }).start();
    }

    private void resetDownloadButton() {
        if (downloadButton != null) {
            downloadButton.setEnabled(true);
            downloadButton.setText("DOWNLOAD VIDEO");
        }
    }

    private void initializeConsentAndAds() {
        ConsentInformation consentInformation = UserMessagingPlatform.getConsentInformation(this);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consentInformation.requestConsentInfoUpdate(this, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, formError -> {
                    if (consentInformation.canRequestAds()) initializeAdsOnce();
                }),
                requestError -> {
                    if (consentInformation.canRequestAds()) initializeAdsOnce();
                });
        if (consentInformation.canRequestAds()) initializeAdsOnce();
    }

    private void initializeAdsOnce() {
        if (adsInitialized) return;
        adsInitialized = true;
        MobileAds.initialize(this, initializationStatus -> runOnUiThread(() -> {
            if (bannerAdView != null) bannerAdView.loadAd(new AdRequest.Builder().build());
            loadInterstitial();
            loadAppOpenAd();
        }));
    }

    private void showAdPrivacyOptions() {
        ConsentInformation info = UserMessagingPlatform.getConsentInformation(this);
        if (info.getPrivacyOptionsRequirementStatus() == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            UserMessagingPlatform.showPrivacyOptionsForm(this, formError -> {
                if (formError != null) Toast.makeText(this, "Privacy settings could not be opened right now.", Toast.LENGTH_LONG).show();
            });
        } else {
            Toast.makeText(this, "No additional ad privacy choices are required for this session.", Toast.LENGTH_LONG).show();
        }
    }

    private void loadAppOpenAd() {
        if (!adsInitialized) return;
        AppOpenAd.load(this, APP_OPEN_ID, new AdRequest.Builder().build(), new AppOpenAd.AppOpenAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull AppOpenAd ad) {
                appOpenAd = ad;
                maybeShowAppOpenAd();
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                appOpenAd = null;
            }
        });
    }

    private void maybeShowAppOpenAd() {
        if (!activityResumed || !appOpenEligible || appOpenShownThisResume || appOpenShowing
                || appOpenAd == null || downloading || waitingForInterstitial) return;
        SharedPreferences prefs = getSharedPreferences("tiktube_ads", MODE_PRIVATE);
        long lastShown = prefs.getLong("last_app_open_ad", 0L);
        if (System.currentTimeMillis() - lastShown < APP_OPEN_COOLDOWN_MS) return;

        AppOpenAd ad = appOpenAd;
        appOpenAd = null;
        appOpenShowing = true;
        appOpenShownThisResume = true;
        prefs.edit().putLong("last_app_open_ad", System.currentTimeMillis()).apply();
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                appOpenShowing = false;
                loadAppOpenAd();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                appOpenShowing = false;
                loadAppOpenAd();
            }
        });
        try {
            ad.show(this);
        } catch (Exception e) {
            appOpenShowing = false;
            loadAppOpenAd();
        }
    }

    private void checkForDownloaderUpdateAutomatically() {
        if (!engineReady || updatingDownloader || downloading) return;

        SharedPreferences prefs = getSharedPreferences("tiktube_downloader", MODE_PRIVATE);
        long lastCheck = prefs.getLong("last_update_check", 0L);
        long now = System.currentTimeMillis();
        if (now - lastCheck < DOWNLOADER_UPDATE_CHECK_INTERVAL_MS) return;

        // Record the attempt first so a failed network check does not repeat on every launch.
        prefs.edit().putLong("last_update_check", now).apply();
        updatingDownloader = true;

        new Thread(() -> {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(
                        getApplicationContext(), YoutubeDL.UpdateChannel._STABLE);
            } catch (Exception ignored) {
                // Keep the currently installed downloader; try again at the next scheduled check.
            } finally {
                updatingDownloader = false;
            }
        }, "Tiktube-Auto-Downloader-Update").start();
    }

    private void openPrivacyPolicy() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open the privacy policy link.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        appOpenShownThisResume = false;
        maybeShowAppOpenAd();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(adTimeoutRunnable);
        if (bannerAdView != null) bannerAdView.destroy();
        super.onDestroy();
    }
}
