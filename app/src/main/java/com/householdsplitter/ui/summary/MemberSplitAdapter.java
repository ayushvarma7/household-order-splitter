package com.householdsplitter.ui.summary;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.householdsplitter.core.calc.AdjustmentType;
import com.householdsplitter.core.calc.result.ItemShare;
import com.householdsplitter.core.calc.result.MemberSplit;
import com.householdsplitter.core.export.ShareTextBuilder;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.databinding.ItemMemberSplitBinding;
import com.householdsplitter.ui.common.MemberPalette;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** SPEC 7.10.2: a row per member that expands into the four lines of the breakdown. */
public class MemberSplitAdapter
        extends ListAdapter<MemberSplit, MemberSplitAdapter.SplitViewHolder> {

    private final CurrencyFormat money;
    private final Map<Long, String> colorByMember = new HashMap<>();
    private final Set<Long> expanded = new HashSet<>();
    private String payerName;
    private Long payerId;

    public MemberSplitAdapter(CurrencyFormat money) {
        super(DIFF);
        this.money = money;
    }

    private static final DiffUtil.ItemCallback<MemberSplit> DIFF =
            new DiffUtil.ItemCallback<MemberSplit>() {
                @Override
                public boolean areItemsTheSame(@NonNull MemberSplit a, @NonNull MemberSplit b) {
                    return a.memberId() == b.memberId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull MemberSplit a, @NonNull MemberSplit b) {
                    return a.finalCents() == b.finalCents()
                            && a.preTaxCents() == b.preTaxCents()
                            && a.commonShareCents() == b.commonShareCents()
                            && a.itemShares().size() == b.itemShares().size();
                }
            };

    public void setColors(Map<Long, String> colors) {
        colorByMember.clear();
        colorByMember.putAll(colors);
    }

    /** SPEC 7.10.4. */
    public void setPayer(Long memberId, String name) {
        this.payerId = memberId;
        this.payerName = name;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SplitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SplitViewHolder(ItemMemberSplitBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull SplitViewHolder holder, int position) {
        holder.bind(getItem(position), this);
    }

    static class SplitViewHolder extends RecyclerView.ViewHolder {

        private final ItemMemberSplitBinding binding;

        SplitViewHolder(ItemMemberSplitBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MemberSplit split, MemberSplitAdapter adapter) {
            CurrencyFormat money = adapter.money;
            binding.avatar.setText(initials(split.memberName()));
            binding.avatar.setBackgroundTintList(ColorStateList.valueOf(
                    MemberPalette.resolve(binding.getRoot().getContext(),
                            adapter.colorByMember.get(split.memberId()))));
            binding.avatar.setContentDescription(split.memberName());
            binding.memberName.setText(split.memberName());
            binding.finalTotal.setText(money.format(split.finalCents()));

            // SPEC 7.10.4
            boolean isPayer = adapter.payerId != null && adapter.payerId == split.memberId();
            if (adapter.payerName != null) {
                binding.owesLine.setVisibility(View.VISIBLE);
                binding.owesLine.setText(ShareTextBuilder.owesLine(
                        split, adapter.payerName, isPayer, money));
            } else {
                binding.owesLine.setVisibility(View.GONE);
            }

            boolean open = adapter.expanded.contains(split.memberId());
            binding.breakdown.setVisibility(open ? View.VISIBLE : View.GONE);
            if (open) {
                binding.breakdown.setText(breakdownText(split, money));
            }
            binding.getRoot().setOnClickListener(v -> {
                if (open) {
                    adapter.expanded.remove(split.memberId());
                } else {
                    adapter.expanded.add(split.memberId());
                }
                adapter.notifyItemChanged(getBindingAdapterPosition());
            });
        }

        /** The four lines of SPEC 7.10.2. */
        private static String breakdownText(MemberSplit split, CurrencyFormat money) {
            StringBuilder out = new StringBuilder();
            out.append("Common share: ").append(money.format(split.commonShareCents()));
            if (!split.itemShares().isEmpty()) {
                out.append("\nOwn items:");
                for (ItemShare share : split.itemShares()) {
                    out.append("\n   ").append(share.itemName());
                    if (share.wayCount() > 1) {
                        out.append(" (").append(share.wayCount()).append(" ways");
                        if (share.shares() > 1) {
                            out.append(", x").append(share.shares()).append(" share");
                        }
                        out.append(')');
                    }
                    out.append("  ").append(money.format(share.cents()));
                }
            }
            boolean anyAdjustment = false;
            StringBuilder adjustments = new StringBuilder();
            for (AdjustmentType type : AdjustmentType.values()) {
                long value = split.adjustmentShare(type);
                if (value != 0L) {
                    anyAdjustment = true;
                    adjustments.append("\n   ").append(type.label()).append(": ")
                            .append(money.format(value));
                }
            }
            if (anyAdjustment) {
                out.append("\nTax and fees:").append(adjustments);
            }
            out.append("\nTotal: ").append(money.format(split.finalCents()));
            return out.toString();
        }

        private static String initials(String name) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty()) {
                return "?";
            }
            String[] words = trimmed.split("\\s+");
            StringBuilder out = new StringBuilder();
            out.append(Character.toUpperCase(words[0].charAt(0)));
            if (words.length > 1) {
                out.append(Character.toUpperCase(words[words.length - 1].charAt(0)));
            }
            return out.toString();
        }
    }
}
