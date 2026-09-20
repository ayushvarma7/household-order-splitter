package com.householdsplitter.ui.importer;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.householdsplitter.databinding.ItemImagePreviewBinding;
import com.householdsplitter.util.Images;

/** SPEC 7.4.2: the preview strip, each with a remove control and a drag handle. */
public class ImagePreviewAdapter
        extends ListAdapter<String, ImagePreviewAdapter.PreviewViewHolder> {

    public interface Listener {
        void onRemove(int position);

        void onDragHandleTouched(RecyclerView.ViewHolder holder);
    }

    private final Listener listener;

    public ImagePreviewAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<String> DIFF = new DiffUtil.ItemCallback<String>() {
        @Override
        public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
            return oldItem.equals(newItem);
        }
    };

    @NonNull
    @Override
    public PreviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PreviewViewHolder(ItemImagePreviewBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PreviewViewHolder holder, int position) {
        holder.bind(getItem(position), position, listener);
    }

    static class PreviewViewHolder extends RecyclerView.ViewHolder {

        private final ItemImagePreviewBinding binding;

        PreviewViewHolder(ItemImagePreviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @SuppressWarnings("ClickableViewAccessibility")
        void bind(String uri, int position, Listener listener) {
            Images.into(binding.preview, Uri.parse(uri), 512);
            binding.positionLabel.setText(String.valueOf(position + 1));
            binding.removeButton.setContentDescription("Remove screenshot " + (position + 1));
            binding.removeButton.setOnClickListener(v ->
                    listener.onRemove(getBindingAdapterPosition()));
            binding.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    listener.onDragHandleTouched(this);
                }
                return false;
            });
        }
    }
}
