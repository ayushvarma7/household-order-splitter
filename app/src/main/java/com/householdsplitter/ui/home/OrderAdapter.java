package com.householdsplitter.ui.home;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.householdsplitter.R;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.data.relation.OrderWithMembers;
import com.householdsplitter.databinding.ItemOrderBinding;
import com.householdsplitter.ui.common.MemberPalette;
import com.householdsplitter.ui.common.StateColors;

import java.text.SimpleDateFormat;
import java.util.Locale;

/** SPEC 7.3.3: label, date, total, overlapping participant avatars, and a status chip. */
public class OrderAdapter extends ListAdapter<OrderWithMembers, OrderAdapter.OrderViewHolder> {

    public interface Listener {
        void onOpen(OrderWithMembers order);

        void onLongPress(OrderWithMembers order, View anchor);
    }

    private final Listener listener;
    private final CurrencyFormat money;

    public OrderAdapter(Listener listener, CurrencyFormat money) {
        super(DIFF);
        this.listener = listener;
        this.money = money;
    }

    private static final DiffUtil.ItemCallback<OrderWithMembers> DIFF =
            new DiffUtil.ItemCallback<OrderWithMembers>() {
                @Override
                public boolean areItemsTheSame(@NonNull OrderWithMembers a,
                                               @NonNull OrderWithMembers b) {
                    return a.order.id == b.order.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull OrderWithMembers a,
                                                  @NonNull OrderWithMembers b) {
                    return a.order.label.equals(b.order.label)
                            && a.order.status == b.order.status
                            && a.order.statedTotalCents == b.order.statedTotalCents
                            && a.order.orderDate == b.order.orderDate
                            && a.participants.size() == b.participants.size();
                }
            };

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new OrderViewHolder(ItemOrderBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        holder.bind(getItem(position), listener, money);
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {

        private final ItemOrderBinding binding;

        OrderViewHolder(ItemOrderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(OrderWithMembers row, Listener listener, CurrencyFormat money) {
            binding.orderLabel.setText(row.order.label);
            binding.orderDate.setText(new SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                    .format(new java.util.Date(row.order.orderDate)));
            binding.orderTotal.setText(money.format(row.order.statedTotalCents));

            // Not colour alone: the pill carries its own word.
            binding.statusChip.setText(statusLabel(row.order.status));
            StateColors.applyContainer(binding.statusChip, binding.statusChip,
                    statusState(row.order.status));

            renderAvatars(row);

            binding.getRoot().setOnClickListener(v -> listener.onOpen(row));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onLongPress(row, v);
                return true;
            });
        }

        /** Overlapping avatars, however many participants there are. */
        private void renderAvatars(OrderWithMembers row) {
            binding.avatarStrip.removeAllViews();
            int density = (int) binding.getRoot().getResources().getDisplayMetrics().density;
            for (Member member : row.participants) {
                TextView avatar = new TextView(binding.getRoot().getContext());
                avatar.setText(member.initials());
                avatar.setTextColor(0xFFFFFFFF);
                avatar.setTextSize(10f);
                avatar.setGravity(android.view.Gravity.CENTER);
                avatar.setBackgroundResource(R.drawable.bg_avatar_circle);
                avatar.setBackgroundTintList(
                        ColorStateList.valueOf(MemberPalette.resolve(binding.getRoot().getContext(), member.colorHex)));
                avatar.setContentDescription(member.name);
                android.widget.LinearLayout.LayoutParams params =
                        new android.widget.LinearLayout.LayoutParams(24 * density, 24 * density);
                if (binding.avatarStrip.getChildCount() > 0) {
                    params.setMarginStart(-7 * density);
                }
                avatar.setLayoutParams(params);
                binding.avatarStrip.addView(avatar);
            }
        }

        private int statusLabel(OrderStatus status) {
            switch (status) {
                case SETTLED:
                    return R.string.status_settled;
                case ASSIGNED:
                    return R.string.status_assigned;
                default:
                    return R.string.status_draft;
            }
        }

        private StateColors.State statusState(OrderStatus status) {
            switch (status) {
                case SETTLED:
                    return StateColors.State.SUCCESS;
                case ASSIGNED:
                    return StateColors.State.NEUTRAL;
                default:
                    return StateColors.State.WARNING;
            }
        }
    }
}
