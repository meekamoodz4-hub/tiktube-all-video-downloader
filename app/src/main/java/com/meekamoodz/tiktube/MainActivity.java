package com.meekamoodz.tiktube;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.ffmpeg.FFmpeg;

import java.io.File;

import kotlin.Unit;

public class MainActivity extends Activity {
    private EditText linkInput;
    private TextView status;
    private Button downloadButton;
    private boolean engineReady = false;
    private volatile boolean downloading = false;

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

    private void addSpace(LinearLayout box, int height) {
        View spacer = new View(this);
        box.addView(spacer, new LinearLayout.LayoutParams(1, dp(height)));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 18, 35));
        getWindow().setNavigationBarColor(Color.rgb(13, 18, 35));

        try {
            YoutubeDL.getInstance().init(getApplication());
            FFmpeg.getInstance().init(getApplication());
            engineReady = true;
        } catch (Exception e) {
            engineReady = false;
        }

        Intent incoming = getIntent();
        String shared = incoming.getStringExtra(Intent.EXTRA_TEXT);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(13, 18, 35));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(30), dp(22), dp(28));
        scroll.addView(page);

        page.addView(label("TIKTUBE", 30, Color.WHITE, true));
        page.addView(label("ALL VIDEO DOWNLOADER", 12, Color.rgb(91, 213, 190), true));
        addSpace(page, 28);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(20), dp(18), dp(20));
        card.setBackground(rounded(Color.rgb(26, 34, 57), 20));
        page.addView(card, new LinearLayout.LayoutParams(-1, -2));

        card.addView(label("Download a video", 22, Color.WHITE, true));
        addSpace(card, 10);
        card.addView(label("Paste a public video link, or share a link to Tiktube from another app.", 14, Color.rgb(190, 199, 219), false));
        addSpace(card, 18);

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
        if (shared != null) linkInput.setText(shared);

        addSpace(card, 14);
        downloadButton = new Button(this);
        downloadButton.setText("DOWNLOAD VIDEO");
        downloadButton.setTextColor(Color.rgb(13, 18, 35));
        downloadButton.setAllCaps(false);
        downloadButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        downloadButton.setBackground(rounded(Color.rgb(91, 213, 190), 14));
        card.addView(downloadButton, new LinearLayout.LayoutParams(-1, dp(52)));

        addSpace(page, 22);
        page.addView(label("SUPPORTED PLATFORMS", 13, Color.rgb(160, 174, 201), true));
        addSpace(page, 10);
        page.addView(label("TikTok  •  Instagram  •  Facebook  •  YouTube", 15, Color.WHITE, true));
        addSpace(page, 20);

        status = label(engineReady ? "Downloader engine ready. Paste a link to begin." : "Downloader engine could not start. Check your internet connection and reinstall the latest build.", 13, Color.rgb(190, 199, 219), false);
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        status.setBackground(rounded(Color.rgb(26, 34, 57), 12));
        page.addView(status, new LinearLayout.LayoutParams(-1, -2));

        addSpace(page, 18);
        TextView note = label("Files are saved in your phone's Download/TikTube folder. Only save content you own or have permission to download. Some links may not work due to platform restrictions or updates.", 12, Color.rgb(145, 157, 180), false);
        page.addView(note);

        downloadButton.setOnClickListener(v -> startDownload());
        setContentView(scroll);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (linkInput != null && shared != null) linkInput.setText(shared);
    }

    private void startDownload() {
        if (downloading) return;
        final String url = linkInput.getText().toString().trim();
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            Toast.makeText(this, "Please paste a valid https video link.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!engineReady) {
            status.setText("The downloader engine did not initialize. Please rebuild and install the newest APK.");
            return;
        }

        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File folder = new File(base, "TikTube");
        if (!folder.exists() && !folder.mkdirs()) {
            status.setText("Could not create Download/TikTube. Check available phone storage.");
            return;
        }

        downloading = true;
        downloadButton.setEnabled(false);
        downloadButton.setText("DOWNLOADING…");
        status.setText("Starting download… keep Tiktube open until it finishes.");

        new Thread(() -> {
            try {
                YoutubeDLRequest request = new YoutubeDLRequest(url);
                request.addOption("--no-playlist");
                request.addOption("--no-mtime");
                request.addOption("-f", "best[ext=mp4]/best");
                request.addOption("--merge-output-format", "mp4");
                request.addOption("-o", new File(folder, "%(title).80s.%(ext)s").getAbsolutePath());

                YoutubeDL.getInstance().execute(request, "TiktubeDownload", (progress, etaInSeconds, line) -> {
                    runOnUiThread(() -> status.setText("Downloading: " + Math.max(0, Math.min(100, Math.round(progress))) + "%\n" + line + "\nEstimated time left: " + etaInSeconds + " seconds"));
                    return Unit.INSTANCE;
                });

                runOnUiThread(() -> {
                    status.setText("Download finished. Check your phone's Download/TikTube folder.");
                    Toast.makeText(MainActivity.this, "Download complete!", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                final String message = e.getMessage() == null ? "This link could not be downloaded." : e.getMessage();
                runOnUiThread(() -> status.setText("Download failed. The link may be private, unsupported, or blocked by the platform. Details: " + message));
            } finally {
                downloading = false;
                runOnUiThread(() -> {
                    downloadButton.setEnabled(true);
                    downloadButton.setText("DOWNLOAD VIDEO");
                });
            }
        }).start();
    }
}
