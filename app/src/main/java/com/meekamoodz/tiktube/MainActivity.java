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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

    private EditText linkInput;
    private TextView status;
    private Button downloadButton;
    private LinearLayout contentContainer;
    private AdView bannerAdView;
    private InterstitialAd interstitialAd;
    private AppOpenAd appOpenAd;
    private boolean engineReady = false;
    private boolean adsInitialized = false;
    private boolean downloading = false;
    private boolean waitingForInterstitial = false;
    private boolean interstitialGateResolved = false;
    private boolean activityResumed = false;
    private boolean appOpenShowing = false;
    private boolean appOpenShownThisResume = false;
    private boolean appOpenEligible = false;
    private String pendingDownloadUrl = null;
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

        buildInterface();
        handleIncomingIntent(getIntent());
        initializeConsentAndAds();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(13, 18, 35));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(22), dp(20), dp(22));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView brand = label("TIKTUBE", 28, Color.WHITE, true);
        brandRow.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        brandRow.addView(label("VIDEO TOOLKIT", 10, Color.rgb(91, 213, 190), true));
        page.addView(brandRow);
        addSpace(page, 4);
        page.addView(label("ALL VIDEO DOWNLOADER", 12, Color.rgb(91, 213, 190), true));
        addSpace(page, 18);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button home = actionButton("Downloader", true);
        Button library = actionButton("My Downloads", false);
        nav.addView(home, new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams libParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        libParams.leftMargin = dp(10);
        nav.addView(library, libParams);
        page.addView(nav);
        home.setOnClickListener(v -> showDownloader());
        library.setOnClickListener(v -> showDownloads());
        addSpace(page, 18);

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        page.addView(contentContainer, new LinearLayout.LayoutParams(-1, -2));
        showDownloader();

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER);
        Button privacy = actionButton("Privacy Policy", false);
        Button consent = actionButton("Ad Privacy Settings", false);
        footer.addView(privacy, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams consentParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        consentParams.leftMargin = dp(8);
        footer.addView(consent, consentParams);
        privacy.setOnClickListener(v -> openPrivacyPolicy());
        consent.setOnClickListener(v -> showAdPrivacyOptions());
        addSpace(page, 18);
        page.addView(footer);

        bannerAdView = new AdView(this);
        bannerAdView.setAdUnitId(BANNER_ID);
        bannerAdView.setAdSize(AdSize.BANNER);
        bannerAdView.setBackgroundColor(Color.rgb(13, 18, 35));
        root.addView(bannerAdView, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void showDownloader() {
        if (contentContainer == null) return;
        contentContainer.removeAllViews();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(20), dp(18), dp(20));
        card.setBackground(rounded(Color.rgb(26, 34, 57), 20));
        contentContainer.addView(card, new LinearLayout.LayoutParams(-1, -2));

        card.addView(label("Download a video", 22, Color.WHITE, true));
        addSpace(card, 8);
        card.addView(label("Paste a video link or share a link from another app. Downloads are saved to your Download/TikTube folder.", 14, Color.rgb(190, 199, 219), false));
        addSpace(card, 16);

        linkInput = new EditText(this);
        linkInput.setSingleLine(false);
        linkInput.setMinLines(2);
        linkInput.setTextSize(15);
        linkInput.setHint("Paste TikTok, Instagram, Facebook or YouTube URL");
        linkInput.setHintTextColor(Color.rgb(130, 143, 167));
        linkInput.setTextColor(Color.WHITE);
        linkInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        linkInput.setBackground(rounded(Color.rgb(15, 22, 41), 12));
        card.addView(linkInput, new LinearLayout.LayoutParams(-1, -2));

        addSpace(card, 14);
        downloadButton = actionButton("DOWNLOAD VIDEO", true);
        card.addView(downloadButton, new LinearLayout.LayoutParams(-1, dp(52)));
        downloadButton.setOnClickListener(v -> requestDownload());

        addSpace(contentContainer, 18);
        LinearLayout platformCard = new LinearLayout(this);
        platformCard.setOrientation(LinearLayout.VERTICAL);
        platformCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        platformCard.setBackground(rounded(Color.rgb(21, 29, 49), 16));
        contentContainer.addView(platformCard, new LinearLayout.LayoutParams(-1, -2));
        platformCard.addView(label("SUPPORTED PLATFORMS", 12, Color.rgb(160, 174, 201), true));
        addSpace(platformCard, 8);
        platformCard.addView(label("TikTok  •  Instagram  •  Facebook  •  YouTube", 14, Color.WHITE, true));

        addSpace(contentContainer, 16);
        status = label(engineReady ? "Downloader ready. Paste a link to begin." : "Downloader engine could not start. Check your connection and reinstall the latest build.", 13, Color.rgb(190, 199, 219), false);
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        status.setBackground(rounded(Color.rgb(26, 34, 57), 12));
        contentContainer.addView(status, new LinearLayout.LayoutParams(-1, -2));

        addSpace(contentContainer, 14);
        contentContainer.addView(label("Only download content you own or have permission to save. Availability depends on each service's rules and technical restrictions.", 12, Color.rgb(145, 157, 180), false));
        if (getIntent() != null) {
            String shared = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null && !shared.trim().isEmpty()) linkInput.setText(shared.trim());
        }
    }

    private void showDownloads() {
        if (contentContainer == null) return;
        contentContainer.removeAllViews();

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
        File[] found = folder.listFiles(file -> file.isFile() && isVideoFile(file.getName()));
        if (found == null || found.length == 0) {
            TextView empty = label("No downloaded videos found yet. Download a video first, then tap Refresh.", 14, Color.rgb(190, 199, 219), false);
            empty.setPadding(dp(16), dp(18), dp(16), dp(18));
            empty.setBackground(rounded(Color.rgb(26, 34, 57), 16));
            list.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        List<File> files = new ArrayList<>(Arrays.asList(found));
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
        pendingDownloadUrl = null;
        if (url != null) startDownloadActual(url);
    }

    private void startDownloadActual(final String url) {
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
                request.addOption("-f", "best[ext=mp4]/best");
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
                final String message = e.getMessage() == null ? "This link could not be downloaded." : e.getMessage();
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
        AppOpenAd.load(this, APP_OPEN_ID, new AdRequest.Builder().build(), new com.google.android.gms.ads.appopen.AppOpenAdLoadCallback() {
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
