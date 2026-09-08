package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.lifecycle.SavedStateHandle;
import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.householdsplitter.ui.importer.ImportViewModel;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The photo being taken right now has to outlive the process.
 *
 * <p>The camera is the one place in this app where Android reliably kills the host: it is
 * memory-hungry and runs in front for as long as the user takes to frame a shot. The
 * pending capture URI used to be a field on the fragment, so a kill lost it, and the
 * failure was silent in the worst way: the user took the photo, came back, and the import
 * screen simply did not have it. No crash, no message, nothing to report.
 *
 * <p>Restoring a ViewModel from the same {@link SavedStateHandle} is exactly what the
 * framework does after that kill, so that is what this does.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class CameraCaptureSurvivalTest {

    private static final String CAPTURE =
            "content://com.householdsplitter.fileprovider/captures/capture-1.jpg";

    @Test
    @UiThreadTest
    public void thePendingCaptureSurvivesProcessDeath() {
        SavedStateHandle handle = new SavedStateHandle();

        ImportViewModel before = new ImportViewModel(handle);
        before.pendingCaptureUri(CAPTURE);

        // The process dies while the camera is in front. Same saved state, new instance.
        ImportViewModel after = new ImportViewModel(handle);

        assertEquals("the photo would otherwise be taken and silently dropped",
                CAPTURE, after.pendingCaptureUri());
    }

    @Test
    @UiThreadTest
    public void thereIsNoPendingCaptureToStartWith() {
        assertNull(new ImportViewModel(new SavedStateHandle()).pendingCaptureUri());
    }

    /** Cleared once the photo has been taken, so a later restore does not re-add it. */
    @Test
    @UiThreadTest
    public void clearingItSticks() {
        SavedStateHandle handle = new SavedStateHandle();
        ImportViewModel model = new ImportViewModel(handle);
        model.pendingCaptureUri(CAPTURE);
        model.pendingCaptureUri(null);

        assertNull(new ImportViewModel(handle).pendingCaptureUri());
    }

    /** The chosen screenshots survive the same kill, which they already did. */
    @Test
    @UiThreadTest
    public void theChosenScreenshotsSurviveToo() {
        SavedStateHandle handle = new SavedStateHandle();
        ImportViewModel before = new ImportViewModel(handle);
        before.add(java.util.Collections.singletonList(
                android.net.Uri.parse("content://media/external/images/1")));

        ImportViewModel after = new ImportViewModel(handle);

        assertEquals(1, after.uris().getValue().size());
    }
}
