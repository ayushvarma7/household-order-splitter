package com.householdsplitter.ui.participants;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.householdsplitter.R;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.databinding.FragmentParticipantsBinding;
import com.householdsplitter.databinding.ItemParticipantBinding;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.MemberPalette;
import com.householdsplitter.ui.parsing.ParsingArgs;

import java.util.List;

/** S8, participants. SPEC 7.8. */
public class ParticipantsFragment extends BaseFragment {

    private FragmentParticipantsBinding binding;
    private ParticipantsViewModel model;
    private long orderId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentParticipantsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        orderId = getArguments() == null ? 0L : getArguments().getLong(ParsingArgs.ARG_ORDER_ID);
        model = viewModel(ParticipantsViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        model.members().observe(getViewLifecycleOwner(), members -> {
            model.initialiseFrom(model.bundle().getValue(), members);
            renderRows(members);
        });
        model.bundle().observe(getViewLifecycleOwner(), bundle ->
                model.initialiseFrom(bundle, model.members().getValue()));

        // SPEC 7.8.3 and 7.8.4
        model.selectedCount().observe(getViewLifecycleOwner(), count -> {
            int value = count == null ? 0 : count;
            binding.splitLine.setText(getString(R.string.participants_split_line, value));
            binding.continueButton.setEnabled(value >= 1);
        });

        binding.continueButton.setOnClickListener(v -> model.save(ok -> {
            if (!Boolean.TRUE.equals(ok) || binding == null) {
                return;
            }
            Bundle args = new Bundle();
            args.putLong(ParsingArgs.ARG_ORDER_ID, orderId);
            NavHostFragment.findNavController(this).navigate(R.id.assignFragment, args);
        }));
    }

    /**
     * A plain list rather than a RecyclerView: a household has a handful of members, and
     * checkbox state is simpler to keep honest this way. Nothing here assumes a count
     * (PROMPT hard rule 6).
     */
    private void renderRows(List<Member> members) {
        binding.memberContainer.removeAllViews();
        if (members == null) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Member member : members) {
            ItemParticipantBinding row = ItemParticipantBinding.inflate(
                    inflater, binding.memberContainer, false);
            row.avatar.setText(member.initials());
            row.avatar.setBackgroundTintList(
                    ColorStateList.valueOf(MemberPalette.parse(member.colorHex)));
            row.avatar.setContentDescription(member.name);
            MaterialCheckBox checkBox = row.checkbox;
            checkBox.setText(member.name);
            checkBox.setChecked(model.isSelected(member.id));
            checkBox.setOnCheckedChangeListener((button, checked) ->
                    model.toggle(member.id, checked));
            binding.memberContainer.addView(row.getRoot());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
