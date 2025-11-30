package com.scriptshot.script.api;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
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
import java.io.InputStream;
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

    private interface BitmapOperator {
        Bitmap apply(Bitmap source) throws IOException;
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

    public boolean cropCenter(String path, int targetWidth, int targetHeight, String outPath) throws IOException {
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Width and height must be > 0");
        }
        return applyTransform(path, outPath, bitmap -> {
            int width = Math.min(targetWidth, bitmap.getWidth());
            int height = Math.min(targetHeight, bitmap.getHeight());
            int startX = Math.max(0, (bitmap.getWidth() - width) / 2);
            int startY = Math.max(0, (bitmap.getHeight() - height) / 2);
            return Bitmap.createBitmap(bitmap, startX, startY, width, height);
        });
    }

    public boolean cropRelative(String path, float leftRatio, float topRatio, float rightRatio, float bottomRatio, String outPath) throws IOException {
        return applyTransform(path, outPath, bitmap -> {
            float clampedLeft = clampFloat(leftRatio, 0f, 1f);
            float clampedTop = clampFloat(topRatio, 0f, 1f);
            float clampedRight = clampFloat(rightRatio, 0f, 1f);
            float clampedBottom = clampFloat(bottomRatio, 0f, 1f);
            int left = Math.round(bitmap.getWidth() * Math.min(clampedLeft, clampedRight));
            int right = Math.round(bitmap.getWidth() * Math.max(clampedLeft, clampedRight));
            int top = Math.round(bitmap.getHeight() * Math.min(clampedTop, clampedBottom));
            int bottom = Math.round(bitmap.getHeight() * Math.max(clampedTop, clampedBottom));
            left = clamp(left, 0, bitmap.getWidth());
            right = clamp(right, 0, bitmap.getWidth());
            top = clamp(top, 0, bitmap.getHeight());
            bottom = clamp(bottom, 0, bitmap.getHeight());
            if (right - left <= 0 || bottom - top <= 0) {
                return bitmap;
            }
            return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
        });
    }

    public boolean resizeToMaxEdge(String path, int maxEdge, String outPath) throws IOException {
        if (maxEdge <= 0) {
            throw new IllegalArgumentException("maxEdge must be > 0");
        }
        File source = resolveFile(path);
        ImageInfo info = load(source.getAbsolutePath());
        int currentMax = Math.max(info.width, info.height);
        String normalizedOut = normalizeOutPath(outPath);
        if (currentMax <= maxEdge) {
            return handleNoopResult(source, normalizedOut);
        }
        final float scale = (float) maxEdge / currentMax;
        return applyTransform(path, normalizedOut, bitmap -> {
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        });
    }

    public boolean resizeToFit(String path, int maxWidth, int maxHeight, String outPath) throws IOException {
        if (maxWidth <= 0 || maxHeight <= 0) {
            throw new IllegalArgumentException("maxWidth and maxHeight must be > 0");
        }
        File source = resolveFile(path);
        ImageInfo info = load(source.getAbsolutePath());
        if (info.width <= maxWidth && info.height <= maxHeight) {
            return handleNoopResult(source, normalizeOutPath(outPath));
        }
        final float scale = Math.min((float) maxWidth / info.width, (float) maxHeight / info.height);
        return applyTransform(path, outPath, bitmap -> {
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        });
    }

    public boolean fillRect(String path, int left, int top, int right, int bottom, String color, String outPath) throws IOException {
        return applyTransform(path, outPath, bitmap -> {
            Rect rect = buildRect(left, top, right, bottom, bitmap.getWidth(), bitmap.getHeight());
            if (rect.isEmpty()) {
                return bitmap;
            }
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(parseColor(color));
            canvas.drawRect(rect, paint);
            return bitmap;
        });
    }

    public boolean drawRect(String path, int left, int top, int right, int bottom, String color, float strokeWidth, String outPath) throws IOException {
        return applyTransform(path, outPath, bitmap -> {
            Rect rect = buildRect(left, top, right, bottom, bitmap.getWidth(), bitmap.getHeight());
            if (rect.isEmpty()) {
                return bitmap;
            }
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, strokeWidth));
            paint.setColor(parseColor(color));
            canvas.drawRect(rect, paint);
            return bitmap;
        });
    }

    public boolean blurRect(String path, int left, int top, int right, int bottom, int radius, String outPath) throws IOException {
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be > 0");
        }
        return applyTransform(path, outPath, bitmap -> {
            Rect rect = buildRect(left, top, right, bottom, bitmap.getWidth(), bitmap.getHeight());
            if (rect.isEmpty()) {
                return bitmap;
            }
            Bitmap region = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height());
            Bitmap blurred = applyBoxBlur(region, radius);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawBitmap(blurred, rect.left, rect.top, null);
            region.recycle();
            blurred.recycle();
            return bitmap;
        });
    }

    public boolean watermarkText(String path, String text, String position, float textSize, String color, int paddingPx, String outPath) throws IOException {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        final String safeText = text;
        final String safePosition = position;
        final float safeSize = Math.max(10f, textSize);
        final int safePadding = Math.max(0, paddingPx);
        return applyTransform(path, outPath, bitmap -> {
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(parseColor(color));
            paint.setTextSize(safeSize);
            paint.setShadowLayer(2f, 1f, 1f, Color.argb(120, 0, 0, 0));
            float x;
            float y;
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float textHeight = metrics.descent - metrics.ascent;
            switch (resolvePosition(safePosition)) {
                case TOP_LEFT:
                    paint.setTextAlign(Paint.Align.LEFT);
                    x = safePadding;
                    y = safePadding - metrics.ascent;
                    break;
                case TOP_RIGHT:
                    paint.setTextAlign(Paint.Align.RIGHT);
                    x = bitmap.getWidth() - safePadding;
                    y = safePadding - metrics.ascent;
                    break;
                case BOTTOM_LEFT:
                    paint.setTextAlign(Paint.Align.LEFT);
                    x = safePadding;
                    y = bitmap.getHeight() - safePadding - metrics.descent;
                    break;
                case CENTER:
                    paint.setTextAlign(Paint.Align.CENTER);
                    x = bitmap.getWidth() / 2f;
                    y = bitmap.getHeight() / 2f - metrics.ascent / 2f;
                    break;
                case BOTTOM_RIGHT:
                default:
                    paint.setTextAlign(Paint.Align.RIGHT);
                    x = bitmap.getWidth() - safePadding;
                    y = bitmap.getHeight() - safePadding - metrics.descent;
                    break;
            }
            canvas.drawText(safeText, x, y, paint);
            return bitmap;
        });
    }

    public boolean watermarkImage(String path, String watermarkPath, String position, float scale, int paddingPx, String outPath) throws IOException {
        File overlayFile = resolveFile(watermarkPath);
        if (!overlayFile.exists()) {
            throw new IOException("Watermark image not found: " + watermarkPath);
        }
        Bitmap overlay = BitmapFactory.decodeFile(overlayFile.getAbsolutePath());
        if (overlay == null) {
            throw new IOException("Unable to decode watermark image: " + watermarkPath);
        }
        final Bitmap safeOverlay = overlay;
        final String safePosition = position;
        final float safeScale = scale <= 0f ? 0.25f : Math.min(scale, 1f);
        final int safePadding = Math.max(0, paddingPx);
        return applyTransform(path, outPath, bitmap -> {
            int baseWidth = bitmap.getWidth();
            float targetWidth = baseWidth * safeScale;
            float aspect = (float) safeOverlay.getWidth() / Math.max(1, safeOverlay.getHeight());
            int overlayWidth = (int) Math.max(1, targetWidth);
            int overlayHeight = (int) Math.max(1, overlayWidth / aspect);
            Bitmap scaled = Bitmap.createScaledBitmap(safeOverlay, overlayWidth, overlayHeight, true);
            Canvas canvas = new Canvas(bitmap);
            float x;
            float y;
            switch (resolvePosition(safePosition)) {
                case TOP_LEFT:
                    x = safePadding;
                    y = safePadding;
                    break;
                case TOP_RIGHT:
                    x = bitmap.getWidth() - overlayWidth - safePadding;
                    y = safePadding;
                    break;
                case BOTTOM_LEFT:
                    x = safePadding;
                    y = bitmap.getHeight() - overlayHeight - safePadding;
                    break;
                case CENTER:
                    x = (bitmap.getWidth() - overlayWidth) / 2f;
                    y = (bitmap.getHeight() - overlayHeight) / 2f;
                    break;
                case BOTTOM_RIGHT:
                default:
                    x = bitmap.getWidth() - overlayWidth - safePadding;
                    y = bitmap.getHeight() - overlayHeight - safePadding;
                    break;
            }
            canvas.drawBitmap(scaled, x, y, null);
            scaled.recycle();
            return bitmap;
        });
    }

    public boolean pad(String path, int left, int top, int right, int bottom, String color, String outPath) throws IOException {
        final int safeLeft = Math.max(0, left);
        final int safeTop = Math.max(0, top);
        final int safeRight = Math.max(0, right);
        final int safeBottom = Math.max(0, bottom);
        final int background = parseColor(color);
        return applyTransform(path, outPath, bitmap -> {
            int newWidth = bitmap.getWidth() + safeLeft + safeRight;
            int newHeight = bitmap.getHeight() + safeTop + safeBottom;
            Bitmap padded = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(padded);
            canvas.drawColor(background);
            canvas.drawBitmap(bitmap, safeLeft, safeTop, null);
            return padded;
        });
    }

    public boolean padToAspectRatio(String path, int targetWidth, int targetHeight, String color, String outPath) throws IOException {
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("targetWidth and targetHeight must be > 0");
        }
        File source = resolveFile(path);
        ImageInfo info = load(source.getAbsolutePath());
        float targetRatio = (float) targetWidth / targetHeight;
        float currentRatio = info.height == 0 ? targetRatio : (float) info.width / info.height;
        if (Math.abs(targetRatio - currentRatio) < 0.0001f) {
            return handleNoopResult(source, normalizeOutPath(outPath));
        }
        final int background = parseColor(color);
        return applyTransform(path, outPath, bitmap -> {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            float current = (float) width / height;
            int padLeft = 0;
            int padTop = 0;
            int padRight = 0;
            int padBottom = 0;
            if (targetRatio > current) {
                float desiredWidth = targetRatio * height;
                int totalPad = Math.max(0, Math.round(desiredWidth) - width);
                padLeft = totalPad / 2;
                padRight = totalPad - padLeft;
            } else {
                float desiredHeight = width / targetRatio;
                int totalPad = Math.max(0, Math.round(desiredHeight) - height);
                padTop = totalPad / 2;
                padBottom = totalPad - padTop;
            }
            Bitmap padded = Bitmap.createBitmap(width + padLeft + padRight, height + padTop + padBottom, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(padded);
            canvas.drawColor(background);
            canvas.drawBitmap(bitmap, padLeft, padTop, null);
            return padded;
        });
    }

    public boolean toGrayscale(String path, String outPath) throws IOException {
        return applyTransform(path, outPath, bitmap -> {
            Bitmap grayscale = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(grayscale);
            Paint paint = new Paint();
            android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
            matrix.setSaturation(0f);
            android.graphics.ColorMatrixColorFilter filter = new android.graphics.ColorMatrixColorFilter(matrix);
            paint.setColorFilter(filter);
            canvas.drawBitmap(bitmap, 0, 0, paint);
            return grayscale;
        });
    }

    public String getAverageColor(String path, int left, int top, int right, int bottom) throws IOException {
        File source = resolveFile(path);
        if (!source.exists()) {
            throw new IOException("Image not found: " + path);
        }
        Bitmap bitmap = BitmapFactory.decodeFile(source.getAbsolutePath());
        if (bitmap == null) {
            throw new IOException("Unable to decode bitmap: " + path);
        }
        try {
            Rect rect = buildRect(left, top, right, bottom, bitmap.getWidth(), bitmap.getHeight());
            if (rect.isEmpty()) {
                rect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            }
            long totalR = 0;
            long totalG = 0;
            long totalB = 0;
            long count = 0;
            for (int y = rect.top; y < rect.bottom; y++) {
                for (int x = rect.left; x < rect.right; x++) {
                    int pixel = bitmap.getPixel(x, y);
                    totalR += Color.red(pixel);
                    totalG += Color.green(pixel);
                    totalB += Color.blue(pixel);
                    count++;
                }
            }
            if (count == 0) {
                return "#000000";
            }
            int avgR = (int) (totalR / count);
            int avgG = (int) (totalG / count);
            int avgB = (int) (totalB / count);
            return String.format(Locale.US, "#%02X%02X%02X", avgR, avgG, avgB);
        } finally {
            bitmap.recycle();
        }
    }

    public String getLastOutputPath() {
        return lastOutputPath;
    }

    private boolean applyTransform(String path, String outPath, BitmapOperator operator) throws IOException {
        File source = resolveFile(path);
        if (!source.exists()) {
            throw new IOException("Image not found: " + path);
        }
        String normalizedOut = normalizeOutPath(outPath);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inMutable = true;
        Bitmap bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        if (bitmap == null) {
            return false;
        }
        Bitmap result = null;
        boolean success;
        try {
            result = operator.apply(bitmap);
            if (result == null) {
                result = bitmap;
            }
            ImageInfo info = load(source.getAbsolutePath());
            Bitmap.CompressFormat format = chooseFormat(info.mime);
            success = persistTransformedBitmap(result, source, normalizedOut, info, format);
        } finally {
            if (bitmap != null && bitmap != result && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            if (result != null && !result.isRecycled()) {
                result.recycle();
            }
        }
        return success;
    }

    private boolean persistTransformedBitmap(Bitmap bitmap, File source, String outPath, ImageInfo info, Bitmap.CompressFormat format) throws IOException {
        if (outPath != null) {
            File destination = resolveFile(outPath);
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            writeBitmap(bitmap, destination, format);
            lastOutputPath = destination.getAbsolutePath();
            return true;
        }
        File temp = createTempFile(source.getName(), format);
        if (temp == null) {
            return false;
        }
        try {
            writeBitmap(bitmap, temp, format);
            if (RootUtils.isRootAvailable() && copyWithRoot(temp, source)) {
                lastOutputPath = source.getAbsolutePath();
                return true;
            }
            String mime = info.mime != null ? info.mime : guessMimeFromFormat(format);
            String publicPath = saveToPublicGallery(temp, mime, format, source.getName());
            if (publicPath != null) {
                lastOutputPath = publicPath;
                return true;
            }
            return false;
        } finally {
            if (temp.exists() && !temp.delete()) {
                Log.w(TAG, "Unable to delete temp file " + temp.getAbsolutePath());
            }
        }
    }

    private String normalizeOutPath(String outPath) {
        if (outPath == null) {
            return null;
        }
        String trimmed = outPath.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean handleNoopResult(File source, String outPath) throws IOException {
        if (outPath == null) {
            lastOutputPath = source.getAbsolutePath();
            return true;
        }
        File destination = resolveFile(outPath);
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (InputStream inputStream = new FileInputStream(source);
             FileOutputStream outputStream = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
        lastOutputPath = destination.getAbsolutePath();
        return true;
    }

    private void removeFromMediaStore(String absolutePath) {
        ContentResolver resolver = appContext.getContentResolver();
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        resolver.delete(uri, MediaStore.Images.Media.DATA + "=?", new String[]{absolutePath});
    }

    private Rect buildRect(int left, int top, int right, int bottom, int maxWidth, int maxHeight) {
        int clampedLeft = clamp(Math.min(left, right), 0, maxWidth);
        int clampedRight = clamp(Math.max(left, right), 0, maxWidth);
        int clampedTop = clamp(Math.min(top, bottom), 0, maxHeight);
        int clampedBottom = clamp(Math.max(top, bottom), 0, maxHeight);
        return new Rect(clampedLeft, clampedTop, clampedRight, clampedBottom);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int parseColor(String color) {
        if (color == null || color.trim().isEmpty()) {
            return Color.WHITE;
        }
        try {
            return Color.parseColor(color.trim());
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Invalid color string '" + color + "', defaulting to white");
            return Color.WHITE;
        }
    }

    private WatermarkPosition resolvePosition(String value) {
        if (value == null) {
            return WatermarkPosition.BOTTOM_RIGHT;
        }
        switch (value.toLowerCase(Locale.US)) {
            case "top_left":
            case "tl":
                return WatermarkPosition.TOP_LEFT;
            case "top_right":
            case "tr":
                return WatermarkPosition.TOP_RIGHT;
            case "bottom_left":
            case "bl":
                return WatermarkPosition.BOTTOM_LEFT;
            case "center":
            case "middle":
                return WatermarkPosition.CENTER;
            case "bottom_right":
            case "br":
            default:
                return WatermarkPosition.BOTTOM_RIGHT;
        }
    }

    private Bitmap applyBoxBlur(Bitmap source, int radius) {
        if (radius <= 0) {
            return source.copy(Bitmap.Config.ARGB_8888, true);
        }
        Bitmap working = source.copy(Bitmap.Config.ARGB_8888, true);
        int width = working.getWidth();
        int height = working.getHeight();
        int[] pixels = new int[width * height];
        int[] temp = new int[width * height];
        working.getPixels(pixels, 0, width, 0, 0, width, height);
        boxBlurHorizontal(pixels, temp, width, height, radius);
        boxBlurVertical(temp, pixels, width, height, radius);
        Bitmap blurred = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        blurred.setPixels(pixels, 0, width, 0, 0, width, height);
        working.recycle();
        return blurred;
    }

    private void boxBlurHorizontal(int[] input, int[] output, int width, int height, int radius) {
        int window = radius * 2 + 1;
        for (int y = 0; y < height; y++) {
            int sumA = 0;
            int sumR = 0;
            int sumG = 0;
            int sumB = 0;
            int row = y * width;
            for (int i = -radius; i <= radius; i++) {
                int sampleX = clamp(i, 0, width - 1);
                int color = input[row + sampleX];
                sumA += Color.alpha(color);
                sumR += Color.red(color);
                sumG += Color.green(color);
                sumB += Color.blue(color);
            }
            for (int x = 0; x < width; x++) {
                output[row + x] = Color.argb(sumA / window, sumR / window, sumG / window, sumB / window);
                int removeIndex = clamp(x - radius, 0, width - 1);
                int addIndex = clamp(x + radius + 1, 0, width - 1);
                int remove = input[row + removeIndex];
                int add = input[row + addIndex];
                sumA += Color.alpha(add) - Color.alpha(remove);
                sumR += Color.red(add) - Color.red(remove);
                sumG += Color.green(add) - Color.green(remove);
                sumB += Color.blue(add) - Color.blue(remove);
            }
        }
    }

    private void boxBlurVertical(int[] input, int[] output, int width, int height, int radius) {
        int window = radius * 2 + 1;
        for (int x = 0; x < width; x++) {
            int sumA = 0;
            int sumR = 0;
            int sumG = 0;
            int sumB = 0;
            for (int i = -radius; i <= radius; i++) {
                int sampleY = clamp(i, 0, height - 1);
                int color = input[sampleY * width + x];
                sumA += Color.alpha(color);
                sumR += Color.red(color);
                sumG += Color.green(color);
                sumB += Color.blue(color);
            }
            for (int y = 0; y < height; y++) {
                output[y * width + x] = Color.argb(sumA / window, sumR / window, sumG / window, sumB / window);
                int removeIndex = clamp(y - radius, 0, height - 1);
                int addIndex = clamp(y + radius + 1, 0, height - 1);
                int remove = input[removeIndex * width + x];
                int add = input[addIndex * width + x];
                sumA += Color.alpha(add) - Color.alpha(remove);
                sumR += Color.red(add) - Color.red(remove);
                sumG += Color.green(add) - Color.green(remove);
                sumB += Color.blue(add) - Color.blue(remove);
            }
        }
    }

    private enum WatermarkPosition {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        CENTER
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
