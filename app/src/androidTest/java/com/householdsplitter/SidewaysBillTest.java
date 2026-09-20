package com.householdsplitter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;

/**
 * What the recogniser makes of a receipt photographed sideways.
 *
 * <p>A till receipt is long and narrow and a phone is held upright, so the natural way to
 * fit one in frame is to lay it across the picture. That produces a page rotated a quarter
 * turn, which is not a tilt: the deskew stage refuses anything past fifteen degrees, and
 * refusing is correct, because rotating a page by an angle measured from a column that is
 * not a column would make it worse.
 *
 * <p>This measures the four orientations against the same photograph so the fix can be
 * chosen from evidence rather than assumed.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SidewaysBillTest {

    private static final String TAG = "SidewaysBill";
    private static final String BILL = "bill-burlington.jpg";

    @Test
    public void whatEachQuarterTurnYields() throws Exception {
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        try {
            testContext.getAssets().open(BILL).close();
        } catch (java.io.IOException absent) {
            Log.i(TAG, "SKIP: " + BILL + " not present");
            return;
        }

        Bitmap original;
        try (InputStream in = testContext.getAssets().open(BILL)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            original = BitmapFactory.decodeStream(in, null, options);
        }
        Log.i(TAG, "source " + original.getWidth() + "x" + original.getHeight());

        TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            for (int degrees : new int[]{0, 90, 180, 270}) {
                Bitmap turned = rotate(original, degrees);
                Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(turned, 0)));

                int elements = 0;
                int amounts = 0;
                StringBuilder found = new StringBuilder();
                for (Text.TextBlock block : text.getTextBlocks()) {
                    for (Text.Line line : block.getLines()) {
                        for (Text.Element element : line.getElements()) {
                            elements++;
                            if (element.getText().matches("-?\\$?\\d{1,3}(,\\d{3})*\\.\\d{2}")) {
                                amounts++;
                                found.append(element.getText()).append(' ');
                            }
                        }
                    }
                }
                Log.i(TAG, String.format("%3d degrees: %3d elements, %2d amounts   %s",
                        degrees, elements, amounts, found.toString().trim()));
                if (degrees != 0) {
                    turned.recycle();
                }
            }
        } finally {
            recognizer.close();
        }
    }

    /**
     * Also worth knowing: whether ML Kit's own per-line angle would have told us, which is
     * a signal this app currently discards at the recognition boundary.
     */
    @Test
    public void doesTheRecogniserReportTheRotationItself() throws Exception {
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        try {
            testContext.getAssets().open(BILL).close();
        } catch (java.io.IOException absent) {
            Log.i(TAG, "SKIP: " + BILL + " not present");
            return;
        }
        Bitmap bitmap;
        try (InputStream in = testContext.getAssets().open(BILL)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)));
            int lines = 0;
            int wide = 0;
            int tall = 0;
            for (Text.TextBlock block : text.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    lines++;
                    android.graphics.Rect box = line.getBoundingBox();
                    if (box == null) {
                        continue;
                    }
                    if (box.width() >= box.height()) {
                        wide++;
                    } else {
                        tall++;
                    }
                    if (lines <= 8) {
                        Log.i(TAG, "  line angle=" + line.getAngle()
                                + " box=" + box.width() + "x" + box.height()
                                + "  " + line.getText());
                    }
                }
            }
            Log.i(TAG, "lines " + lines + ", wider-than-tall " + wide + ", taller-than-wide " + tall);
        } finally {
            recognizer.close();
        }
    }

    private static Bitmap rotate(Bitmap source, int degrees) {
        if (degrees == 0) {
            return source;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }
}
