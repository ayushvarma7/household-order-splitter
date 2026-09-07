package com.householdsplitter.ui.summary;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.householdsplitter.R;
import com.householdsplitter.databinding.ItemScreenshotThumbBinding;

/** SPEC 7.12.2: the thumbnail strip of the original screenshots. */
public class ScreenshotAdapter
        extends ListAdapter<String, ScreenshotAdapter.ThumbViewHolder> {

    public interface Listener {
        void onOpen(String uri, int position);
    }

    private final Listener listener;

    public ScreenshotAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<String> DIFF = new DiffUtil.ItemCallback<String>() {
        @Override
        public boolean areItemsTheSame(@NonNull String a, @NonNull String b) {
            return a.equals(b);
        }

        @Override
        public boolean areContentsTheSame(@NonNull String a, @NonNull String b) {
            return a.equals(b);
        }
    };

    @NonNull
    @Override
    public ThumbViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ThumbViewHolder(ItemScreenshotThumbBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ThumbViewHolder holder, int position) {
        holder.bind(getItem(position), position, listener);
    }

    static class ThumbViewHolder extends RecyclerView.ViewHolder {

        private final ItemScreenshotThumbBinding binding;

        ThumbViewHolder(ItemScreenshotThumbBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(String uri, int position, Listener listener) {
            try {
                binding.thumbnail.setImageURI(Uri.parse(uri));
            } catch (SecurityException noLongerPermitted) {
                // The user revoked access to the picture. The order still reads fine
                // without it, so the tile simply stays blank.
                binding.thumbnail.setImageDrawable(null);
            }
            String label = binding.getRoot().getContext()
                    .getString(R.string.screenshot_number, position + 1);
            binding.getRoot().setContentDescription(label);
            binding.getRoot().setOnClickListener(v -> listener.onOpen(uri, position));
        }
    }
}
