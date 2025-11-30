package com.scriptshot.script.api;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
public final class ImgApi {

    private final Context appContext;

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
        return success;
    }

    public boolean delete(String path) {
        File source = resolveFile(path);
        if (!source.exists()) {
            return false;
        }

        boolean deletedOnDisk = source.delete();
        removeFromMediaStore(source.getAbsolutePath());
        return deletedOnDisk;
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
