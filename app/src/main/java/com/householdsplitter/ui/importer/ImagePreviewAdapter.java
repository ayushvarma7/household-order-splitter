package com.householdsplitter.ui.importer;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.householdsplitter.R;
import com.householdsplitter.databinding.ItemImagePreviewBinding;
import com.householdsplitter.util.Images;

/** SPEC 7.4.2: the preview strip, each with a remove control and a drag handle. */
public class ImagePreviewAdapter
        extends ListAdapter<ImportViewModel.Preview, ImagePreviewAdapter.PreviewViewHolder> {

    public interface Listener {
        void onRemove(int position);

        void onDragHandleTouched(RecyclerView.ViewHolder holder);
    }

    private final Listener listener;

    public ImagePreviewAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<ImportViewModel.Preview> DIFF =
            new DiffUtil.ItemCallback<ImportViewModel.Preview>() {
                @Override
                public boolean areItemsTheSame(@NonNull ImportViewModel.Preview oldItem,
                                               @NonNull ImportViewModel.Preview newItem) {
                    return oldItem.uri.equals(newItem.uri);
                }

                @Override
                public boolean areContentsTheSame(@NonNull ImportViewModel.Preview oldItem,
                                                  @NonNull ImportViewModel.Preview newItem) {
                    return oldItem.equals(newItem);
                }
            };

    /** True when the tile at this position is a screenshot the order already has. */
    public boolean isRead(int position) {
        return position >= 0 && position < getItemCount() && getItem(position).read;
    }

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
        void bind(ImportViewModel.Preview item, int position, Listener listener) {
            Images.into(binding.preview, Uri.parse(item.uri), 512);
            binding.positionLabel.setText(String.valueOf(position + 1));
            binding.preview.setAlpha(item.read ? 0.55f : 1f);
            binding.readBadge.setVisibility(item.read ? View.VISIBLE : View.GONE);
            binding.removeButton.setVisibility(item.read ? View.GONE : View.VISIBLE);
            binding.dragHandle.setVisibility(item.read ? View.GONE : View.VISIBLE);
            binding.getRoot().setContentDescription(item.read
                    ? binding.getRoot().getContext().getString(
                            R.string.import_already_read_description, position + 1)
                    : null);
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
