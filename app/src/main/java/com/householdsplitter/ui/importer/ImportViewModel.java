package com.householdsplitter.ui.importer;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

/** S4. SPEC 7.4. */
public class ImportViewModel extends ViewModel {

    private static final String KEY_URIS = "selected_uris";

    private final SavedStateHandle handle;

    /**
     * Where a photo being taken right now will land.
     *
     * <p>Held here rather than on the fragment because the camera is the classic case for
     * SPEC 11.14: it is memory-hungry, so Android kills the host process behind it more
     * often than anywhere else in the app. A fragment field does not survive that, and the
     * result was silent: the user took the photo, came back, and it was simply not there.
     */
    private static final String KEY_PENDING_CAPTURE = "pending_capture_uri";
    private final MutableLiveData<List<String>> uris = new MutableLiveData<>(new ArrayList<>());

    public ImportViewModel(SavedStateHandle handle) {
        this.handle = handle;
        ArrayList<String> restored = handle.get(KEY_URIS);
        if (restored != null) {
            uris.setValue(new ArrayList<>(restored));
        }
    }

    public LiveData<List<String>> uris() {
        return uris;
    }

    public List<String> current() {
        List<String> value = uris.getValue();
        return value == null ? new ArrayList<>() : value;
    }

    /** SPEC 7.4.4: default order is selection order, and the user can drag to change it. */
    public void add(List<Uri> picked) {
        List<String> next = new ArrayList<>(current());
        for (Uri uri : picked) {
            String value = uri.toString();
            if (!next.contains(value)) {
                next.add(value);
            }
        }
        publish(next);
    }

    public void remove(int index) {
        List<String> next = new ArrayList<>(current());
        if (index >= 0 && index < next.size()) {
            next.remove(index);
            publish(next);
        }
    }

    public void move(int from, int to) {
        List<String> next = new ArrayList<>(current());
        if (from < 0 || to < 0 || from >= next.size() || to >= next.size()) {
            return;
        }
        next.add(to, next.remove(from));
        publish(next);
    }

    /** SPEC 7.4.5. */
    public boolean canContinue() {
        return !current().isEmpty();
    }

    private void publish(List<String> next) {
        uris.setValue(next);
        handle.set(KEY_URIS, new ArrayList<>(next));
    }

    /** The capture in flight, or null. Survives process death. */
    public String pendingCaptureUri() {
        return handle.get(KEY_PENDING_CAPTURE);
    }

    public void pendingCaptureUri(String uri) {
        handle.set(KEY_PENDING_CAPTURE, uri);
    }
}
