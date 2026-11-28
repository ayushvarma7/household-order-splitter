package com.householdsplitter.ui.importer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.householdsplitter.R;
import com.householdsplitter.data.entity.OrderImage;
import com.householdsplitter.databinding.FragmentImportBinding;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.parsing.ParsingArgs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** S4, import. SPEC 7.4. */
public class ImportFragment extends BaseFragment {

    /** Set when the user shared screenshots straight into the app (SPEC 8.1.2). */
    public static final String ARG_SHARED_URIS = "shared_uris";

    /**
     * What the file picker will offer.
     *
     * <p>Images only, so a PDF is greyed out rather than accepted and then failing at
     * decode. Plenty of receipts do arrive as PDFs and reading one would mean rendering a
     * page to a bitmap first; offering it here without doing that would be a promise the
     * next screen breaks.
     */
    private static final String[] IMAGE_TYPES = {"image/*"};

    private FragmentImportBinding binding;
    private ImportViewModel model;
    private ImagePreviewAdapter adapter;
    private ItemTouchHelper touchHelper;

    private ActivityResultLauncher<PickVisualMediaRequest> pickMultiple;
    private ActivityResultLauncher<String[]> openDocuments;
    private ActivityResultLauncher<Uri> takePicture;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Resolved here, not in onViewCreated, because both callbacks below use it. Results
        // are delivered at STARTED, which is after the view exists, so the old ordering
        // happened to work; depending on that is not the same as it being safe.
        model = viewModel(ImportViewModel.class);

        // SPEC 7.4.3
        pickMultiple = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(20), uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        persistPermissions(uris);
                        model.add(uris);
                    }
                });

        // The system file picker, which reaches everywhere the photo picker cannot:
        // Downloads, Documents, and whatever cloud providers the user has mounted.
        //
        // Its grants are also the better ones. A photo picker URI is readable until the
        // process dies and refuses takePersistableUriPermission; a document URI survives a
        // restart, which matters because this app keeps the URI on the order and reads the
        // image again later to explain what the parser missed.
        openDocuments = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                    if (uris == null || uris.isEmpty()) {
                        return;
                    }
                    persistPermissions(uris);
                    model.add(uris);
                });

        takePicture = registerForActivityResult(
                new ActivityResultContracts.TakePicture(), saved -> {
                    // Read back from saved state rather than a field: if the process was
                    // killed while the camera was in front, the field would be gone and
                    // the photo would vanish without a word.
                    String pending = model.pendingCaptureUri();
                    if (Boolean.TRUE.equals(saved) && pending != null) {
                        model.add(java.util.Collections.singletonList(Uri.parse(pending)));
                    }
                    model.pendingCaptureUri(null);
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentImportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Insets.padTop(binding.toolbar);
        Insets.padBottom(binding.footer);

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        adapter = new ImagePreviewAdapter(new ImagePreviewAdapter.Listener() {
            @Override
            public void onRemove(int position) {
                model.remove(position);
            }

            @Override
            public void onDragHandleTouched(RecyclerView.ViewHolder holder) {
                touchHelper.startDrag(holder);
            }
        });
        binding.previewStrip.setLayoutManager(new LinearLayoutManager(
                requireContext(), RecyclerView.HORIZONTAL, false));
        binding.previewStrip.setAdapter(adapter);

        // SPEC 7.4.4: drag to reorder, because the order drives stitching (SPEC 8.7.1).
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.START | ItemTouchHelper.END, 0) {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder holder) {
                // A screenshot already read is part of the order; it is shown, not moved.
                return adapter.isRead(holder.getBindingAdapterPosition())
                        ? 0 : super.getMovementFlags(recyclerView, holder);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder source,
                                  @NonNull RecyclerView.ViewHolder target) {
                if (adapter.isRead(target.getBindingAdapterPosition())) {
                    return false;
                }
                model.move(source.getBindingAdapterPosition(), target.getBindingAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }
        });
        touchHelper.attachToRecyclerView(binding.previewStrip);

        applyStoreFraming();

        model.previews().observe(getViewLifecycleOwner(), this::render);

        // Adding to an order from its own item list: show what it already has.
        long orderToAddTo = getArguments() == null
                ? 0L : getArguments().getLong(ParsingArgs.ARG_ORDER_ID, 0L);
        if (orderToAddTo != 0L && model.appendOrderId() == 0L) {
            loadOrderScreenshots(orderToAddTo);
        }

        // Back here after these screenshots were read: they are now part of that order, so
        // what is added next joins it rather than starting the whole read again.
        // By destination rather than getCurrentBackStackEntry(): while a pop is still
        // settling, the current entry can be the screen being left, whose result this is not.
        NavBackStackEntry self = ownBackStackEntry();
        if (self != null) {
            self.getSavedStateHandle().<Long>getLiveData(ParsingArgs.RESULT_READ_INTO_ORDER)
                    .observe(getViewLifecycleOwner(), orderId -> {
                        if (orderId == null || orderId == 0L) {
                            return;
                        }
                        model.markAllRead(orderId);
                        self.getSavedStateHandle().remove(ParsingArgs.RESULT_READ_INTO_ORDER);
                    });
        }

        binding.readAllButton.setOnClickListener(v -> readScreenshots(model.all(), true));
        binding.continueButton.setOnClickListener(v -> readScreenshots(model.current(), false));

        // SPEC 8.1.2: images shared in from another app arrive already loaded.
        if (savedInstanceState == null && getArguments() != null) {
            ArrayList<String> shared = getArguments().getStringArrayList(ARG_SHARED_URIS);
            if (shared != null && !shared.isEmpty()) {
                List<Uri> uris = new ArrayList<>();
                for (String value : shared) {
                    uris.add(Uri.parse(value));
                }
                model.add(uris);
                getArguments().remove(ARG_SHARED_URIS);
            }
        }
    }

    /**
     * Opens the reading screen.
     *
     * @param readAll true to read every screenshot again as one page, false to read only
     *                the ones being added (or, for a new order, all of them)
     */
    private void readScreenshots(List<String> uris, boolean readAll) {
        Bundle args = new Bundle();
        args.putStringArrayList(ParsingArgs.ARG_URIS, new ArrayList<>(uris));
        // The store the picker chose travels with the import, so the parsing screen
        // builds the reader for this order rather than for whichever store is default.
        if (getArguments() != null && getArguments().getString(ParsingArgs.ARG_STORE) != null) {
            args.putString(ParsingArgs.ARG_STORE, getArguments().getString(ParsingArgs.ARG_STORE));
        }
        // Adding to an order that already exists, rather than starting a new one (SPEC
        // 8.1.3): either the one this screen was opened for, or the one its screenshots
        // were just read into.
        long existingOrderId = model.appendOrderId();
        if (existingOrderId != 0L) {
            args.putLong(ParsingArgs.ARG_ORDER_ID, existingOrderId);
            args.putBoolean(ParsingArgs.ARG_READ_ALL, readAll);
        }
        NavHostFragment.findNavController(this).navigate(R.id.parsingFragment, args);
    }

    private void render(List<ImportViewModel.Preview> previews) {
        if (binding == null) {
            return;
        }
        adapter.submitList(new ArrayList<>(previews));
        int total = previews.size();
        int fresh = model.current().size();
        boolean appending = model.appendOrderId() != 0L;

        binding.toolbar.setTitle(appending ? R.string.import_add_to_order : R.string.import_title);
        binding.previewStrip.setVisibility(total == 0 ? View.GONE : View.VISIBLE);
        binding.stripLabel.setVisibility(total == 0 ? View.GONE : View.VISIBLE);
        binding.emptyHint.setVisibility(total == 0 || appending ? View.VISIBLE : View.GONE);
        binding.emptyHint.setText(appending ? R.string.import_add_hint : R.string.import_hint);

        // SPEC 7.4.2: Continue shows the count. SPEC 7.4.5: one image minimum, and when
        // adding to an order, one new image: reading everything again is the other button.
        if (fresh == 0) {
            binding.continueButton.setText(R.string.action_continue);
        } else if (appending) {
            binding.continueButton.setText(
                    getResources().getQuantityString(R.plurals.import_read_new, fresh, fresh));
        } else {
            binding.continueButton.setText(
                    getResources().getQuantityString(R.plurals.continue_with_images, fresh, fresh));
        }
        binding.continueButton.setEnabled(fresh > 0);

        boolean canReadAll = appending && total > fresh;
        binding.readAllButton.setVisibility(canReadAll ? View.VISIBLE : View.GONE);
        binding.readAllButton.setText(
                getResources().getQuantityString(R.plurals.import_read_all_again, total, total));
    }

    private NavBackStackEntry ownBackStackEntry() {
        try {
            return NavHostFragment.findNavController(this).getBackStackEntry(R.id.importFragment);
        } catch (IllegalArgumentException notOnTheStack) {
            return null;
        }
    }

    /** The order's own screenshots, in their stored order, shown as already read. */
    private void loadOrderScreenshots(long orderId) {
        locator().executors().diskIO().execute(() -> {
            List<OrderImage> images = new ArrayList<>(
                    locator().database().orderImageDao().getForOrderSync(orderId));
            images.sort((first, second) -> Integer.compare(first.position, second.position));
            List<String> uris = new ArrayList<>();
            for (OrderImage image : images) {
                uris.add(image.uri);
            }
            locator().executors().mainThread().execute(() -> {
                if (binding != null) {
                    model.addTo(orderId, uris);
                }
            });
        });
    }

    /** SPEC 7.4.6: persistable permission, so the URIs survive a restart. */
    private void persistPermissions(List<Uri> uris) {
        for (Uri uri : uris) {
            try {
                requireContext().getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException notPersistable) {
                // Some providers do not offer a persistable grant. The URI still works for
                // this session, and the order keeps whatever it can.
            }
        }
    }

    private void launchCamera() {
        File directory = new File(requireContext().getCacheDir(), "captures");
        if (!directory.exists() && !directory.mkdirs()) {
            return;
        }
        File file = new File(directory, "capture-" + System.currentTimeMillis() + ".jpg");
        Uri target = androidx.core.content.FileProvider.getUriForFile(
                requireContext(), requireContext().getPackageName() + ".fileprovider", file);
        model.pendingCaptureUri(target.toString());
        takePicture.launch(target);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.previewStrip.setAdapter(null);
        binding = null;
    }
    /**
     * Which of the two buttons leads, and what the screen calls itself.
     *
     * <p>A grocery order is already on the phone, so the job is picking screenshots and the
     * camera is the odd case. A restaurant bill is on the table in front of the user, so the
     * camera is the whole point and picking from the gallery is the odd case.
     *
     * <p>The two buttons swap what they do rather than the layout reordering, because the
     * first one is filled and the second outlined and that difference is the emphasis. Moving
     * views between parents at runtime to achieve the same thing would be a lot of machinery
     * for the same pixels.
     */
    private void applyStoreFraming() {
        Runnable choosePhotos = () -> pickMultiple.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());

        // The same in both framings. Where a file came from is not a property of which
        // shop it is from, and a receipt saved out of an email is as likely either way.
        binding.filesButton.setOnClickListener(v -> openDocuments.launch(IMAGE_TYPES));

        com.householdsplitter.core.parse.StoreKind store = chosenStore();
        if (store != null && store.isPaper()) {
            // A restaurant hands over a bill; a grocery till hands over a receipt.
            boolean bill = store == com.householdsplitter.core.parse.StoreKind.RESTAURANT;
            binding.toolbar.setTitle(bill ? getString(R.string.import_title_bill)
                    : getString(R.string.import_title_receipt, store.displayName()));
            binding.emptyHint.setText(bill ? R.string.import_hint_bill
                    : R.string.import_hint_receipt);
            binding.chooseButton.setText(bill ? R.string.action_photograph_bill
                    : R.string.action_photograph_receipt);
            binding.chooseButton.setOnClickListener(v -> launchCamera());
            binding.cameraButton.setText(R.string.action_choose_photo);
            binding.cameraButton.setOnClickListener(v -> choosePhotos.run());
            return;
        }
        binding.chooseButton.setOnClickListener(v -> choosePhotos.run());
        binding.cameraButton.setOnClickListener(v -> launchCamera());
    }

    /** The store chosen in the picker, or null when none was passed in. */
    @androidx.annotation.Nullable
    private com.householdsplitter.core.parse.StoreKind chosenStore() {
        if (getArguments() == null) {
            return null;
        }
        String store = getArguments().getString(ParsingArgs.ARG_STORE);
        return store == null ? null : com.householdsplitter.core.parse.StoreKind.fromName(store);
    }

}
