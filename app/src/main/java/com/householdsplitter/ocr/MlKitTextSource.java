package com.householdsplitter.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.householdsplitter.core.parse.model.OcrElement;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * The only class that knows ML Kit exists. SPEC 8.2.1.
 *
 * <p>It loads and downscales an image, runs recognition, and converts the result into the
 * plain {@link OcrElement} POJOs that the layout parser in :core understands. Keeping the
 * boundary here is what makes the parser testable without a device.
 */
public class MlKitTextSource {

    private final Context context;
    private final TextRecognizer recognizer;
    private final int maxDimensionPx;

    public MlKitTextSource(Context context, int maxDimensionPx) {
        this.context = context.getApplicationContext();
        this.recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        this.maxDimensionPx = maxDimensionPx;
    }

    /** SPEC 8.2.1: load, downscale to the long-edge limit, recognise, collect elements. */
    public List<OcrElement> read(Uri uri, int imageIndex) throws IOException {
        Bitmap bitmap = loadDownscaled(uri);
        if (bitmap == null) {
            throw new IOException("Could not open that image");
        }
        try {
            Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)));
            return toElements(text, imageIndex, bitmap.getWidth(), bitmap.getHeight());
        } catch (Exception recognitionFailed) {
            throw new IOException("Text recognition failed", recognitionFailed);
        } finally {
            bitmap.recycle();
        }
    }

    /** SPEC 11.10: the same screenshot picked twice is dropped before parsing. */
    public String contentHash(Uri uri) throws IOException {
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                throw new IOException("Could not open that image");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            StringBuilder out = new StringBuilder();
            for (byte b : digest.digest()) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }

    private Bitmap loadDownscaled(Uri uri) throws IOException {
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
        while (longEdge / (sample * 2) >= maxDimensionPx) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(stream, null, options);
        }
    }

    /**
     * SPEC 8.2.1 collects elements with their bounding boxes. Confidence is carried as an
     * integer percent rather than a float, so no floating point value crosses into the
     * parser (see the OcrElement class comment).
     */
    private static List<OcrElement> toElements(Text text, int imageIndex, int width, int height) {
        List<OcrElement> elements = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    Rect box = element.getBoundingBox();
                    if (box == null) {
                        continue;
                    }
                    int confidence = -1;
                    Float reported = element.getConfidence();
                    if (reported != null) {
                        confidence = Math.round(reported * 100f);
                    }
                    elements.add(new OcrElement(element.getText(), imageIndex,
                            box.left, box.top, box.right, box.bottom,
                            width, height, confidence));
                }
            }
        }
        return elements;
    }

    public void close() {
        recognizer.close();
    }
}
