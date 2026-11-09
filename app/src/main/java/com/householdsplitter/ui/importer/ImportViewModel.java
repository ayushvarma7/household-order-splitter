package com.householdsplitter.ui.importer;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

/** S4. SPEC 7.4. */
public class ImportViewModel extends ViewModel {

    private static final String KEY_URIS = "selected_uris";
    private static final String KEY_READ = "already_read_uris";
    private static final String KEY_APPEND_ORDER = "append_order_id";

    /** One tile in the strip: a screenshot, and whether the order already has it. */
    public static final class Preview {

        public final String uri;
        /** Already read into the order: shown, numbered, but neither removable nor movable. */
        public final boolean read;

        Preview(String uri, boolean read) {
            this.uri = uri;
            this.read = read;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Preview && ((Preview) other).uri.equals(uri)
                    && ((Preview) other).read == read;
        }

        @Override
        public int hashCode() {
            return uri.hashCode() * 31 + (read ? 1 : 0);
        }
    }

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
    private final MutableLiveData<List<String>> read = new MutableLiveData<>(new ArrayList<>());
    private final MediatorLiveData<List<Preview>> previews = new MediatorLiveData<>();

    public ImportViewModel(SavedStateHandle handle) {
        this.handle = handle;
        ArrayList<String> restored = handle.get(KEY_URIS);
        if (restored != null) {
            uris.setValue(new ArrayList<>(restored));
        }
        ArrayList<String> restoredRead = handle.get(KEY_READ);
        if (restoredRead != null) {
            read.setValue(new ArrayList<>(restoredRead));
        }
        previews.addSource(uris, value -> previews.setValue(buildPreviews()));
        previews.addSource(read, value -> previews.setValue(buildPreviews()));
    }

    /** The strip as shown: the order's screenshots first, then the ones being added. */
    public LiveData<List<Preview>> previews() {
        return previews;
    }

    /** The order these screenshots are being added to, or 0 for a new order. */
    public long appendOrderId() {
        Long id = handle.get(KEY_APPEND_ORDER);
        return id == null ? 0L : id;
    }

    /** The screenshots the order already has, in their stored order. */
    public List<String> alreadyRead() {
        List<String> value = read.getValue();
        return value == null ? new ArrayList<>() : value;
    }

    /** Every screenshot, the order's own first, for reading them all again as one page. */
    public List<String> all() {
        List<String> out = new ArrayList<>(alreadyRead());
        out.addAll(current());
        return out;
    }

    /**
     * Switches to adding to an existing order, which already has these screenshots.
     *
     * <p>Screenshots already picked stay picked, unless the order turns out to have them:
     * images shared into a draft from another app arrive before the order's own list has
     * loaded, and clearing them here would silently drop what the user just shared.
     */
    public void addTo(long orderId, List<String> orderScreenshots) {
        handle.set(KEY_APPEND_ORDER, orderId);
        publishRead(new ArrayList<>(orderScreenshots));
        List<String> stillNew = new ArrayList<>(current());
        stillNew.removeAll(orderScreenshots);
        publish(stillNew);
    }

    /** Everything in the strip has now been read into this order. */
    public void markAllRead(long orderId) {
        handle.set(KEY_APPEND_ORDER, orderId);
        publishRead(all());
        publish(new ArrayList<>());
    }

    private List<Preview> buildPreviews() {
        List<Preview> out = new ArrayList<>();
        for (String uri : alreadyRead()) {
            out.add(new Preview(uri, true));
        }
        for (String uri : current()) {
            out.add(new Preview(uri, false));
        }
        return out;
    }

    private void publishRead(List<String> next) {
        read.setValue(next);
        handle.set(KEY_READ, new ArrayList<>(next));
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
            // A screenshot the order already has is not added twice.
            if (!next.contains(value) && !alreadyRead().contains(value)) {
                next.add(value);
            }
        }
        publish(next);
    }

    /** @param index a position in {@link #previews()}; screenshots already read stay */
    public void remove(int index) {
        index -= alreadyRead().size();
        List<String> next = new ArrayList<>(current());
        if (index >= 0 && index < next.size()) {
            next.remove(index);
            publish(next);
        }
    }

    /** Positions in {@link #previews()}. Only the screenshots being added can move. */
    public void move(int from, int to) {
        from -= alreadyRead().size();
        to -= alreadyRead().size();
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
