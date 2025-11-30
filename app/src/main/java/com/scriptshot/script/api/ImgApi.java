package com.scriptshot.script.api;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

import com.scriptshot.core.root.RootUtils;
import com.scriptshot.script.api.ShellApi.ShellResult;
public final class ImgApi {

    private static final String TAG = "ImgApi";
    private static final String PUBLIC_FOLDER = "ScriptShot";

    private final Context appContext;
    private volatile String lastOutputPath;

    public ImgApi(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public ImageInfo load(String path) throws IOException {
        File source = resolveFile(path);
        if (!source.exists()) {
            throw new IOException("Image not found: " + path);
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), options);

        return new ImageInfo(
            options.outWidth,
            options.outHeight,
            source.length(),
            options.outMimeType
        );
    }

    public String toBase64(String path) throws IOException {
        File source = resolveFile(path);
        if (!source.exists()) {
            throw new IOException("Image not found: " + path);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        FileInputStream inputStream = new FileInputStream(source);
        byte[] buffer = new byte[8 * 1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        inputStream.close();
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
    }

    public boolean compress(String path, int quality, String outPath) throws IOException {
        File source = resolveFile(path);
        Bitmap bitmap = BitmapFactory.decodeFile(source.getAbsolutePath());
        if (bitmap == null) {
            return false;
        }

        File destination = resolveFile(outPath);
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        FileOutputStream outputStream = new FileOutputStream(destination);
        boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, Math.max(0, Math.min(quality, 100)), outputStream);
        outputStream.flush();
        outputStream.close();
        bitmap.recycle();
        if (success) {
            lastOutputPath = destination.getAbsolutePath();
        }
        return success;
    }

    public boolean delete(String path) {
        File source = resolveFile(path);
        if (!source.exists()) {
            return false;
        }

        boolean deletedOnDisk = source.delete();
        removeFromMediaStore(source.getAbsolutePath());
        lastOutputPath = null;
        return deletedOnDisk;
    }

    public boolean rotate(String path, int degrees) throws IOException {
        if (degrees % 360 == 0) {
            File noop = resolveFile(path);
            lastOutputPath = noop.getAbsolutePath();
            return true;
        }
        File source = resolveFile(path);
        if (!source.exists()) {
            throw new IOException("Image not found: " + path);
        }
        Bitmap bitmap = BitmapFactory.decodeFile(source.getAbsolutePath());
        if (bitmap == null) {
            return false;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.recycle();

        ImageInfo info = load(path);
        Bitmap.CompressFormat format = chooseFormat(info.mime);
        File temp = createTempFile(source.getName(), format);
        if (temp == null) {
            rotated.recycle();
            return false;
        }
        try {
            writeBitmap(rotated, temp, format);
            if (RootUtils.isRootAvailable() && copyWithRoot(temp, source)) {
                lastOutputPath = source.getAbsolutePath();
                return true;
            }
            String publicPath = saveToPublicGallery(temp, info.mime != null ? info.mime : guessMimeFromFormat(format), format, source.getName());
            if (publicPath != null) {
                lastOutputPath = publicPath;
                return true;
            }
            return false;
        } finally {
            rotated.recycle();
            if (temp.exists() && !temp.delete()) {
                Log.w(TAG, "Unable to delete temp file " + temp.getAbsolutePath());
            }
        }
    }

    public String getLastOutputPath() {
        return lastOutputPath;
    }

    private void removeFromMediaStore(String absolutePath) {
        ContentResolver resolver = appContext.getContentResolver();
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        resolver.delete(uri, MediaStore.Images.Media.DATA + "=?", new String[]{absolutePath});
    }

    private File resolveFile(String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(appContext.getFilesDir(), path);
    }

    private Bitmap.CompressFormat chooseFormat(String mime) {
        if (mime == null) {
            return Bitmap.CompressFormat.JPEG;
        }
        String lower = mime.toLowerCase();
        if (lower.contains("png")) {
            return Bitmap.CompressFormat.PNG;
        }
        if (lower.contains("webp")) {
            return Bitmap.CompressFormat.WEBP;
        }
        return Bitmap.CompressFormat.JPEG;
    }

    private void writeBitmap(Bitmap bitmap, File destination, Bitmap.CompressFormat format) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create directory: " + parent.getAbsolutePath());
        }
        FileOutputStream outputStream = new FileOutputStream(destination, false);
        try {
            if (!bitmap.compress(format, 100, outputStream)) {
                throw new IOException("Bitmap compression failed");
            }
            outputStream.flush();
        } finally {
            outputStream.close();
        }
    }

    private File createTempFile(String originalName, Bitmap.CompressFormat format) {
        File cacheDir = appContext.getCacheDir();
        if (cacheDir == null) {
            return null;
        }
        String baseName = stripExtension(originalName);
        if (baseName.isEmpty()) {
            baseName = "screenshot";
        }
        String extension = extensionForFormat(format, originalName);
        String fileName = baseName + "-rotated-" + System.currentTimeMillis();
        try {
            return File.createTempFile(fileName, extension, cacheDir);
        } catch (IOException e) {
            Log.e(TAG, "Unable to create temp file", e);
            return null;
        }
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return name;
        }
        return name.substring(0, dot);
    }

    private String extensionForFormat(Bitmap.CompressFormat format, String originalName) {
        String original = null;
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0 && dot < originalName.length() - 1) {
            original = originalName.substring(dot).toLowerCase();
        }
        if (original != null && !original.isEmpty()) {
            return original;
        }
        switch (format) {
            case PNG:
                return ".png";
            case WEBP:
                return ".webp";
            case JPEG:
            default:
                return ".jpg";
        }
    }

    private String guessMimeFromFormat(Bitmap.CompressFormat format) {
        switch (format) {
            case PNG:
                return "image/png";
            case WEBP:
                return "image/webp";
            case JPEG:
            default:
                return "image/jpeg";
        }
    }

    private boolean copyWithRoot(File temp, File destination) {
        ShellApi shell = new ShellApi();
        String cmd = String.format(Locale.US,
            "cp '%s' '%s' && chmod 664 '%s'",
            temp.getAbsolutePath(),
            destination.getAbsolutePath(),
            destination.getAbsolutePath()
        );
        ShellResult result;
        try {
            result = shell.sudo(cmd);
        } catch (IOException e) {
            Log.e(TAG, "Root copy command failed", e);
            return false;
        }
        if (result.getCode() == 0) {
            MediaScannerConnection.scanFile(
                appContext,
                new String[]{destination.getAbsolutePath()},
                null,
                null
            );
            return true;
        }
        Log.w(TAG, "Root copy failed: code=" + result.getCode() + " stderr=" + result.getStderr());
        return false;
    }

    private String saveToPublicGallery(File temp, String mimeType, Bitmap.CompressFormat format, String originalName) {
        ContentResolver resolver = appContext.getContentResolver();
        ContentValues values = new ContentValues();
        String displayName = stripExtension(originalName);
        if (displayName.isEmpty()) {
            displayName = "screenshot";
        }
        displayName = displayName + "-rotated-" + System.currentTimeMillis() + extensionForFormat(format, originalName);
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        String relativePath = Environment.DIRECTORY_PICTURES + "/" + PUBLIC_FOLDER;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, relativePath);
        }
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            Log.e(TAG, "Failed to insert into MediaStore");
            return null;
        }
        try (OutputStream outputStream = resolver.openOutputStream(uri, "w")) {
            if (outputStream == null) {
                Log.e(TAG, "Unable to open output stream for uri " + uri);
                return null;
            }
            copyFile(temp, outputStream);
        } catch (IOException e) {
            Log.e(TAG, "Unable to write rotated image to MediaStore", e);
            resolver.delete(uri, null, null);
            return null;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            File publicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), PUBLIC_FOLDER);
            if (!publicDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                publicDir.mkdirs();
            }
            File path = new File(publicDir, displayName);
            MediaScannerConnection.scanFile(appContext, new String[]{path.getAbsolutePath()}, new String[]{mimeType}, null);
            return path.getAbsolutePath();
        }
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/Pictures/" + PUBLIC_FOLDER + "/" + displayName;
    }

    private void copyFile(File source, OutputStream destination) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(source)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                destination.write(buffer, 0, read);
            }
            destination.flush();
        }
    }

    /**
     * Value object describing the dimensions and metadata of an image.
     */
    public static final class ImageInfo {
        public final int width;
        public final int height;
        public final long bytes;
        public final String mime;

        ImageInfo(int width, int height, long bytes, String mime) {
            this.width = width;
            this.height = height;
            this.bytes = bytes;
            this.mime = mime;
        }
    }
}
