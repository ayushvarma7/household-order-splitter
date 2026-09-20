package com.householdsplitter.ui.profile;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.householdsplitter.R;
import com.householdsplitter.core.analytics.Balances;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.export.WorkbookService;
import com.householdsplitter.databinding.FragmentProfileBinding;
import com.householdsplitter.databinding.ItemEditGroupRowBinding;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.common.MemberPalette;
import com.householdsplitter.ui.common.StateColors;

import java.util.List;

/**
 * You, among the other people in this group.
 *
 * <p>Everything else in this app is deliberately symmetric. A split produces a figure for
 * every person and has no opinion about which of them is holding the phone, which is the
 * right shape for the arithmetic and is why the analytics screen lists everybody and the
 * widget prints instructions like "Ben pays Ana $12.40".
 *
 * <p>That sentence has a reader, though, and until now the app could not tell whether it
 * was Ben or Ana. This screen is where that gets answered, and the answer is stored per
 * group because you are a different row in each one.
 *
 * <p>It is optional, and stays optional. Nothing breaks when nobody is chosen: the card at
 * the top asks the question instead of answering it, and every other screen in the app
 * carries on exactly as it did. That matters because most people will never open this.
 */
public class ProfileFragment extends BaseFragment {

    private FragmentProfileBinding binding;
    private long householdId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Insets.padTop(binding.toolbar);
        Insets.padBottom(binding.content);
        householdId = locator().currentHouseholdId();

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        binding.themeButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.settingsFragment));
        binding.settingsButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.settingsFragment));
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        long self = locator().settings().selfMemberId(householdId);
        locator().executors().diskIO().execute(() -> {
            List<Member> people = locator().database().memberDao().getActiveSync(householdId);
            locator().executors().mainThread().execute(() -> {
                if (binding == null) {
                    return;
                }
                renderPeople(people, self);
                // The balances are a pass over every settled order, so they are asked for
                // once and only when somebody has said who they are. Without a name to
                // attach it to, the figure has nothing to be about.
                if (self == 0L) {
                    renderUnknown();
                    return;
                }
                locator().workbookService().analyse(householdId,
                        insight -> renderStanding(insight, self, people));
            });
        });
    }

    private void renderUnknown() {
        binding.avatar.setBackground(disc(
                StateColors.container(requireContext(), StateColors.State.NEUTRAL)));
        binding.avatar.setText("?");
        binding.selfName.setText(R.string.profile_unknown_name);
        binding.standing.setText(R.string.profile_unknown_body);
        binding.standingDetail.setVisibility(View.GONE);
    }

    private void renderStanding(WorkbookService.Insight insight, long self,
                                List<Member> people) {
        if (binding == null) {
            return;
        }
        Member me = null;
        for (Member member : people) {
            if (member.id == self) {
                me = member;
            }
        }
        if (me == null) {
            // Chosen once and since archived. Forget the choice rather than showing a
            // figure attributed to somebody who is no longer in the group.
            locator().settings().selfMemberId(householdId, 0L);
            renderUnknown();
            return;
        }
        binding.avatar.setBackground(disc(
                MemberPalette.resolve(requireContext(), me.colorHex)));
        binding.avatar.setText(me.initials());
        binding.selfName.setText(me.name);

        CurrencyFormat money = new CurrencyFormat(
                locator().settings().currencySymbol(), locator().settings().locale());
        Balances.Balance mine = balanceFor(insight, self);
        if (mine == null) {
            binding.standing.setText(R.string.profile_nothing_yet);
            binding.standingDetail.setVisibility(View.GONE);
            return;
        }
        long net = mine.netCents();
        if (net == 0L) {
            binding.standing.setText(R.string.profile_square);
        } else if (net > 0L) {
            binding.standing.setText(getString(R.string.profile_owed, money.format(net)));
        } else {
            binding.standing.setText(getString(R.string.profile_owes, money.format(-net)));
        }
        binding.standingDetail.setVisibility(View.VISIBLE);
        binding.standingDetail.setText(getString(R.string.profile_standing_detail,
                money.format(mine.paidCents()), money.format(mine.owedCents())));
    }

    private Balances.Balance balanceFor(WorkbookService.Insight insight, long memberId) {
        if (insight == null || insight.balances == null) {
            return null;
        }
        for (Balances.Balance balance : insight.balances.balances()) {
            if (balance.memberId() == memberId) {
                return balance;
            }
        }
        return null;
    }

    private void renderPeople(List<Member> people, long self) {
        binding.memberList.removeAllViews();
        for (Member member : people) {
            ItemEditGroupRowBinding row = ItemEditGroupRowBinding.inflate(
                    getLayoutInflater(), binding.memberList, false);
            row.rowAvatar.setVisibility(View.VISIBLE);
            row.rowAvatar.setText(member.initials());
            row.rowAvatar.setBackground(
                    disc(MemberPalette.resolve(requireContext(), member.colorHex)));
            row.rowTitle.setText(member.name);
            boolean isSelf = member.id == self;
            row.rowAction.setIconResource(R.drawable.ic_check_circle);
            row.rowAction.setVisibility(isSelf ? View.VISIBLE : View.INVISIBLE);
            row.rowAction.setContentDescription(getString(R.string.profile_this_is_me));
            row.getRoot().setOnClickListener(v -> {
                // Tapping the person you already are clears the choice, which is the only
                // way back to "not said" once it has been said.
                locator().settings().selfMemberId(householdId, isSelf ? 0L : member.id);
                load();
            });
            binding.memberList.addView(row.getRoot());
        }
    }

    private GradientDrawable disc(int colour) {
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(colour);
        return disc;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
