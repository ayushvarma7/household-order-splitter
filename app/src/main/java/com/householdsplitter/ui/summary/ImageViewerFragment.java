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
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
