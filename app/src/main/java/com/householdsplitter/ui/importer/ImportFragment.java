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
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.householdsplitter.R;
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

    private FragmentImportBinding binding;
    private ImportViewModel model;
    private ImagePreviewAdapter adapter;
    private ItemTouchHelper touchHelper;

    private ActivityResultLauncher<PickVisualMediaRequest> pickMultiple;
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
        if (getArguments() != null
                && getArguments().getLong(ParsingArgs.ARG_ORDER_ID, 0L) != 0L) {
            binding.toolbar.setTitle(R.string.import_add_to_order);
        }

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
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder source,
                                  @NonNull RecyclerView.ViewHolder target) {
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

        model.uris().observe(getViewLifecycleOwner(), uris -> {
            adapter.submitList(new ArrayList<>(uris));
            int count = uris.size();
            binding.previewStrip.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
            binding.stripLabel.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
            binding.emptyHint.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
            // SPEC 7.4.2: Continue shows the count. SPEC 7.4.5: one image minimum.
            binding.continueButton.setText(count == 0
                    ? getString(R.string.action_continue)
                    : getResources().getQuantityString(R.plurals.continue_with_images, count, count));
            binding.continueButton.setEnabled(count > 0);
        });

        binding.continueButton.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putStringArrayList(ParsingArgs.ARG_URIS, new ArrayList<>(model.current()));
            // The store the picker chose travels with the import, so the parsing screen
            // builds the reader for this order rather than for whichever store is default.
            if (getArguments() != null && getArguments().getString(ParsingArgs.ARG_STORE) != null) {
                args.putString(ParsingArgs.ARG_STORE, getArguments().getString(ParsingArgs.ARG_STORE));
            }
            // Carried through when the user came here to add to an order that already
            // exists, rather than to start a new one (SPEC 8.1.3).
            long existingOrderId = getArguments() == null
                    ? 0L : getArguments().getLong(ParsingArgs.ARG_ORDER_ID, 0L);
            if (existingOrderId != 0L) {
                args.putLong(ParsingArgs.ARG_ORDER_ID, existingOrderId);
            }
            NavHostFragment.findNavController(this).navigate(R.id.parsingFragment, args);
        });

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

        if (storeIsPaper()) {
            binding.toolbar.setTitle(R.string.import_title_bill);
            binding.emptyHint.setText(R.string.import_hint_bill);
            binding.chooseButton.setText(R.string.action_photograph_bill);
            binding.chooseButton.setOnClickListener(v -> launchCamera());
            binding.cameraButton.setText(R.string.action_choose_photo);
            binding.cameraButton.setOnClickListener(v -> choosePhotos.run());
            return;
        }
        binding.chooseButton.setOnClickListener(v -> choosePhotos.run());
        binding.cameraButton.setOnClickListener(v -> launchCamera());
    }

    /** True when this order is a photograph of paper rather than a set of screenshots. */
    private boolean storeIsPaper() {
        if (getArguments() == null) {
            return false;
        }
        String store = getArguments().getString(ParsingArgs.ARG_STORE);
        return store != null
                && com.householdsplitter.core.parse.StoreKind.fromName(store)
                        == com.householdsplitter.core.parse.StoreKind.RESTAURANT;
    }

}
