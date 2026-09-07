package com.householdsplitter.ui.summary;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.householdsplitter.R;
import com.householdsplitter.databinding.FragmentImageViewerBinding;
import com.householdsplitter.ui.common.Insets;

/** SPEC 7.12.2: one original screenshot, full screen. */
public class ImageViewerFragment extends Fragment {

    public static final String ARG_URI = "image_uri";
    public static final String ARG_POSITION = "image_position";
    /** Optional: the region to ring, in permille, and what to call it. */
    public static final String ARG_CAPTION = "highlight_caption";
    public static final String ARG_LEFT = "highlight_left";
    public static final String ARG_TOP = "highlight_top";
    public static final String ARG_RIGHT = "highlight_right";
    public static final String ARG_BOTTOM = "highlight_bottom";

    private FragmentImageViewerBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentImageViewerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Insets.padTop(binding.toolbar);

        String uri = getArguments() == null ? null : getArguments().getString(ARG_URI);
        int position = getArguments() == null ? 0 : getArguments().getInt(ARG_POSITION, 0);
        binding.toolbar.setTitle(getString(R.string.screenshot_number, position + 1));
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        if (uri != null) {
            try {
                binding.image.setImageURI(Uri.parse(uri));
            } catch (SecurityException noLongerPermitted) {
                binding.image.setImageDrawable(null);
            }
        }
        applyHighlight();
    }

    /**
     * Rings the region this screenshot was opened for, when it was opened for one.
     *
     * <p>The overlay is redrawn after a layout pass rather than immediately: it maps the
     * region through the ImageView's matrix, and that matrix is not final until the image
     * has been measured against the view it sits in.
     */
    private void applyHighlight() {
        Bundle args = getArguments();
        if (args == null || !args.containsKey(ARG_LEFT)) {
            return;
        }
        int left = args.getInt(ARG_LEFT, -1);
        int top = args.getInt(ARG_TOP, 0);
        int right = args.getInt(ARG_RIGHT, 0);
        int bottom = args.getInt(ARG_BOTTOM, 0);
        String caption = args.getString(ARG_CAPTION);

        if (caption != null && !caption.isEmpty()) {
            binding.caption.setText(caption);
            binding.caption.setVisibility(View.VISIBLE);
        }
        binding.image.post(() -> {
            if (binding == null) {
                return;
            }
            binding.highlight.highlight(binding.image, left, top, right, bottom);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
