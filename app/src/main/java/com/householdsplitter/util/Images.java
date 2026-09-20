package com.householdsplitter.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loading a picture the way it was taken, rather than the way it was stored.
 *
 * <p>A phone camera does not usually rotate the pixels it captures. It writes them in the
 * sensor's own orientation and records how far round the phone was held in an EXIF tag, and
 * every gallery, browser and messaging app honours that tag on the way to the screen.
 * {@code BitmapFactory} does not, and neither does {@code ImageView.setImageURI}, which
 * uses it.
 *
 * <p>The consequence was invisible in this app until it learned to read paper. A screenshot
 * carries no orientation tag, so both grocery stores were unaffected; the first photograph
 * anybody pointed at it arrived a quarter turn over, the text recogniser read almost
 * nothing off it, and the receipt came back with no items at all.
 *
 * <p>One place, used by both the reader and every screen that shows the picture. Splitting
 * them would be worse than leaving it broken: the parser records where on the image each
 * row was found, and those boxes are drawn back over it. A reader working on an upright
 * copy and a viewer showing a sideways one would put every highlight in the wrong place.
 */
public final class Images {

    private Images() {
    }

    /**
     * Decodes {@code uri}, downscaled to {@code maxDimensionPx} on its longest edge and
     * turned the right way up.
     *
     * @return the bitmap, or null when the image cannot be read
     */
    public static Bitmap decode(Context context, Uri uri, int maxDimensionPx)
            throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        int longEdge = Math.max(bounds.outWidth, bounds.outHeight);
        if (longEdge <= 0) {
            throw new IOException("That file is not an image");
        }
        int sample = 1;
        while (maxDimensionPx > 0 && longEdge / (sample * 2) >= maxDimensionPx) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap decoded;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            decoded = BitmapFactory.decodeStream(stream, null, options);
        }
        if (decoded == null) {
            return null;
        }
        return orient(decoded, transformFor(context, uri));
    }

    /**
     * The EXIF transform this image asks for, as a matrix, or null when it asks for none.
     *
     * <p>All eight orientations, not just the four rotations. The mirrored ones are rare
     * from a rear camera and ordinary from a front one, and a mirrored receipt read as an
     * unmirrored one produces text that is confidently wrong rather than unreadable.
     *
     * <p>Failure to read the tag is treated as "no transform" rather than as an error. An
     * image with no EXIF at all is the common case, since every screenshot is one.
     */
    private static Matrix transformFor(Context context, Uri uri) {
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream != null) {
                orientation = new ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (IOException | RuntimeException noUsableExif) {
            return null;
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270f);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_NORMAL:
            case ExifInterface.ORIENTATION_UNDEFINED:
            default:
                return null;
        }
        return matrix;
    }

    /**
     * Loads {@code uri} into {@code target}, off the main thread and the right way up.
     *
     * <p>Replaces {@code ImageView.setImageURI}, which reads and decodes the whole file
     * on whichever thread calls it and ignores the orientation tag while doing so. A
     * receipt photograph is several thousand pixels on its long edge, so that is a jank on
     * every bind as well as a sideways picture.
     *
     * <p>The view is tagged with the URI it was asked for and the result is dropped if the
     * tag has changed by the time the bitmap is ready, which is what stops a recycled row
     * in a list briefly showing the previous row's receipt.
     */
    public static void into(android.widget.ImageView target, Uri uri, int maxDimensionPx) {
        final Context context = target.getContext().getApplicationContext();
        // Taken from the application rather than passed in, so a list adapter can call this
        // without having to be handed plumbing it has no other use for.
        AppExecutors executors = ((com.householdsplitter.SplitterApp) context)
                .serviceLocator().executors();
        target.setTag(com.householdsplitter.R.id.image_uri_tag, uri);
        executors.diskIO().execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = decode(context, uri, maxDimensionPx);
            } catch (IOException | RuntimeException unreadable) {
                // A picture the user has since deleted. Leaving the view empty is the
                // honest outcome; there is nothing to show and nothing to say about it.
            }
            final Bitmap loaded = bitmap;
            executors.mainThread().execute(() -> {
                if (loaded == null
                        || !uri.equals(target.getTag(com.householdsplitter.R.id.image_uri_tag))) {
                    return;
                }
                target.setImageBitmap(loaded);
            });
        });
    }

    private static Bitmap orient(Bitmap source, Matrix transform) {
        if (transform == null) {
            return source;
        }
        Bitmap turned = Bitmap.createBitmap(
                source, 0, 0, source.getWidth(), source.getHeight(), transform, true);
        if (turned != source) {
            source.recycle();
        }
        return turned;
    }
}
