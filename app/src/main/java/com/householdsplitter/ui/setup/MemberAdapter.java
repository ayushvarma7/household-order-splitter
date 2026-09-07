package com.householdsplitter.ui.setup;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.householdsplitter.data.entity.Member;
import com.householdsplitter.databinding.ItemMemberBinding;
import com.householdsplitter.ui.common.MemberPalette;

/** SPEC 4.3: ListAdapter with DiffUtil. SPEC 7.2.3 defines the row. */
public class MemberAdapter extends ListAdapter<Member, MemberAdapter.MemberViewHolder> {

    public interface Listener {
        void onEdit(Member member);

        void onDelete(Member member);
    }

    private final Listener listener;

    public MemberAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Member> DIFF = new DiffUtil.ItemCallback<Member>() {
        @Override
        public boolean areItemsTheSame(@NonNull Member oldItem, @NonNull Member newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Member oldItem, @NonNull Member newItem) {
            return oldItem.name.equals(newItem.name)
                    && oldItem.colorHex.equals(newItem.colorHex)
                    && oldItem.sortOrder == newItem.sortOrder
                    && oldItem.isArchived == newItem.isArchived;
        }
    };

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MemberViewHolder(ItemMemberBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {

        private final ItemMemberBinding binding;

        MemberViewHolder(ItemMemberBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Member member, Listener listener) {
            binding.avatar.setText(member.initials());
            binding.avatar.setBackgroundTintList(
                    ColorStateList.valueOf(MemberPalette.resolve(binding.getRoot().getContext(), member.colorHex)));
            binding.memberName.setText(member.name);

            // Accessibility: the avatar colour is decoration, so the name is announced.
            binding.avatar.setContentDescription(member.name);
            binding.editButton.setContentDescription("Rename " + member.name);
            binding.deleteButton.setContentDescription("Remove " + member.name);

            binding.editButton.setOnClickListener(v -> listener.onEdit(member));
            binding.deleteButton.setOnClickListener(v -> listener.onDelete(member));
        }
    }
}
