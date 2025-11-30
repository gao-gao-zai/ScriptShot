package com.scriptshot.script.api;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.scriptshot.R;

import java.io.File;

/**
 * Exposes simple sharing helpers to automation scripts.
 */
public final class ShareApi {

    private final Context appContext;
    private final String authority;

    public ShareApi(Context context) {
        this.appContext = context.getApplicationContext();
        this.authority = this.appContext.getPackageName() + ".fileprovider";
    }

    public void image(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path is required");
        }
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalArgumentException("File not found: " + path);
        }
        Uri uri = FileProvider.getUriForFile(appContext, authority, file);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(shareIntent, appContext.getString(R.string.share_chooser_title));
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        appContext.startActivity(chooser);
    }
}
