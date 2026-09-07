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
    private Uri pendingCameraUri;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                    if (Boolean.TRUE.equals(saved) && pendingCameraUri != null) {
                        model.add(java.util.Collections.singletonList(pendingCameraUri));
                    }
                    pendingCameraUri = null;
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
        model = viewModel(ImportViewModel.class);

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

        binding.chooseButton.setOnClickListener(v -> pickMultiple.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        binding.cameraButton.setOnClickListener(v -> launchCamera());

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
        pendingCameraUri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(), requireContext().getPackageName() + ".fileprovider", file);
        takePicture.launch(pendingCameraUri);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.previewStrip.setAdapter(null);
        binding = null;
    }
}
