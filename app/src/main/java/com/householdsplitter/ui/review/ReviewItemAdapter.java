package com.householdsplitter.ui.review;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.core.parse.model.ReviewReason;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.databinding.ItemReviewRowBinding;
import com.householdsplitter.ui.common.StateColors;

/** SPEC 7.6.3 and 7.6.4. */
public class ReviewItemAdapter extends ListAdapter<LineItem, ReviewItemAdapter.RowViewHolder> {

    public interface Listener {
        void onEdit(LineItem item);
    }

    private final Listener listener;
    private final CurrencyFormat money;

    public ReviewItemAdapter(Listener listener, CurrencyFormat money) {
        super(DIFF);
        this.listener = listener;
        this.money = money;
    }

    private static final DiffUtil.ItemCallback<LineItem> DIFF =
            new DiffUtil.ItemCallback<LineItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull LineItem a, @NonNull LineItem b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull LineItem a, @NonNull LineItem b) {
                    return a.name.equals(b.name)
                            && a.lineTotalCents == b.lineTotalCents
                            && a.quantity == b.quantity
                            && a.needsReview == b.needsReview
                            && a.scope == b.scope
                            && a.position == b.position;
                }
            };

    public LineItem itemAt(int position) {
        return getItem(position);
    }

    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RowViewHolder(ItemReviewRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RowViewHolder holder, int position) {
        holder.bind(getItem(position), position, listener, money);
    }

    static class RowViewHolder extends RecyclerView.ViewHolder {

        private final ItemReviewRowBinding binding;

        RowViewHolder(ItemReviewRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(LineItem item, int position, Listener listener, CurrencyFormat money) {
            binding.positionLabel.setText(String.valueOf(position + 1));
            binding.itemName.setText(item.name.isEmpty() ? "(no name yet)" : item.name);
            binding.itemPrice.setText(money.format(item.lineTotalCents));

            binding.quantityChip.setVisibility(item.quantity > 1 ? View.VISIBLE : View.GONE);
            binding.quantityChip.setText("x" + item.quantity);

            // The unit price is captured (SPEC 8.3.3) but not shown. Only the amount
            // actually billed for the row matters, and a second figure beside it invites a
            // reader to wonder which one they are paying.
            binding.unitPrice.setVisibility(View.GONE);

            // SPEC 7.6.4: tinted, with a warning icon whose content description says why.
            // The tint is a faint wash of the warning container, so it reads as "look at
            // this" in both palettes and never as an error.
            boolean flagged = item.needsReview;
            android.content.Context context = binding.getRoot().getContext();
            binding.warningIcon.setVisibility(flagged ? View.VISIBLE : View.GONE);
            binding.rowBackground.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    flagged ? StateColors.wash(context, StateColors.State.WARNING)
                            : android.graphics.Color.TRANSPARENT));
            binding.warningIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    StateColors.content(context, StateColors.State.WARNING)));
            binding.warningIcon.setContentDescription(reasonText(item));

            boolean excluded = item.scope == Scope.EXCLUDED;
            binding.excludedLabel.setVisibility(excluded ? View.VISIBLE : View.GONE);
            binding.excludedLabel.setTextColor(
                    StateColors.content(context, StateColors.State.DANGER));
            binding.itemName.setAlpha(excluded ? 0.5f : 1f);
            binding.itemPrice.setAlpha(excluded ? 0.5f : 1f);

            binding.getRoot().setOnClickListener(v -> listener.onEdit(item));
        }

        /** SPEC 8.8.2: every flagged row states its reason. */
        private static String reasonText(LineItem item) {
            if (item.reviewReasonsCsv == null || item.reviewReasonsCsv.isEmpty()) {
                return "Needs a check";
            }
            StringBuilder out = new StringBuilder();
            for (String name : item.reviewReasonsCsv.split(",")) {
                ReviewReason reason = ReviewReason.fromName(name.trim());
                if (reason == null) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append(". ");
                }
                out.append(reason.message());
            }
            return out.length() == 0 ? "Needs a check" : out.toString();
        }
    }
}
