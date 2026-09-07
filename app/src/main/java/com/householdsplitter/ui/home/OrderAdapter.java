package com.householdsplitter.ui.home;

import android.content.Context;
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
import com.householdsplitter.databinding.ItemOrderHeaderBinding;
import com.householdsplitter.ui.common.MemberPalette;
import com.householdsplitter.ui.common.StateColors;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * SPEC 7.3.3: label, date, total, overlapping participant avatars, and a status chip,
 * grouped under month headings.
 */
public class OrderAdapter extends ListAdapter<HomeRow, RecyclerView.ViewHolder> {

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

    private static final DiffUtil.ItemCallback<HomeRow> DIFF =
            new DiffUtil.ItemCallback<HomeRow>() {
                @Override
                public boolean areItemsTheSame(@NonNull HomeRow a, @NonNull HomeRow b) {
                    return a.key().equals(b.key());
                }

                @Override
                public boolean areContentsTheSame(@NonNull HomeRow a, @NonNull HomeRow b) {
                    if (a.type != b.type) {
                        return false;
                    }
                    if (a.type == HomeRow.TYPE_HEADER) {
                        return a.heading == b.heading
                                && a.orderCount == b.orderCount
                                && a.totalCents == b.totalCents;
                    }
                    return a.order.order.label.equals(b.order.order.label)
                            && a.order.order.status == b.order.order.status
                            && a.order.order.statedTotalCents == b.order.order.statedTotalCents
                            && a.order.order.orderDate == b.order.order.orderDate
                            && a.order.participants.size() == b.order.participants.size();
                }
            };

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == HomeRow.TYPE_HEADER) {
            return new HeaderViewHolder(ItemOrderHeaderBinding.inflate(inflater, parent, false));
        }
        return new OrderViewHolder(ItemOrderBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        HomeRow row = getItem(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(row, money);
        } else {
            ((OrderViewHolder) holder).bind(row.order, listener, money);
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {

        private final ItemOrderHeaderBinding binding;

        HeaderViewHolder(ItemOrderHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(HomeRow row, CurrencyFormat money) {
            Context context = binding.getRoot().getContext();
            binding.heading.setText(headingText(context, row));
            binding.subheading.setText(context.getResources().getQuantityString(
                    R.plurals.home_month_summary, row.orderCount, row.orderCount,
                    money.format(row.totalCents)));
        }

        private String headingText(Context context, HomeRow row) {
            switch (row.heading) {
                case THIS_MONTH:
                    return context.getString(R.string.home_month_current);
                case LAST_MONTH:
                    return context.getString(R.string.home_month_previous);
                case MONTH:
                    return row.month.format(DateTimeFormatter.ofPattern(
                            context.getString(R.string.home_month_pattern),
                            Locale.getDefault()));
                default:
                    return row.month.format(DateTimeFormatter.ofPattern(
                            context.getString(R.string.home_month_pattern_with_year),
                            Locale.getDefault()));
            }
        }
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
                avatar.setTextColor(androidx.core.content.ContextCompat.getColor(
                        binding.getRoot().getContext(), R.color.on_member_color));
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
