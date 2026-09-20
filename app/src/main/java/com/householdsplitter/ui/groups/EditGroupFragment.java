package com.householdsplitter.ui.groups;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;
import com.householdsplitter.R;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.databinding.FragmentEditGroupBinding;
import com.householdsplitter.databinding.ItemEditGroupRowBinding;
import com.householdsplitter.ui.MainActivity;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.common.MemberPalette;
import com.householdsplitter.ui.parsing.ParsingArgs;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * One group: what it is worth, what it has bought, and who is in it.
 *
 * <p>Three things that used to be in three places. The name was only editable during setup,
 * the people were a drawer entry that did not say which group they belonged to, and what a
 * group had spent was not visible anywhere without opening its analytics. They are all
 * properties of the same thing, so they are on the same screen.
 *
 * <p>The two figures at the top are split into settled and outstanding rather than shown as
 * one total, because a group that has spent six hundred pounds and settled all of it is in
 * a completely different state from one that has spent the same and settled none, and a
 * single number cannot tell those apart.
 *
 * <p>Edits are held until Save rather than applied as you type. A group's name appears in
 * the tab bar, on the home screen and on every order, and having all of that flicker while
 * somebody is still choosing a name is noise. Deleting is the exception: it asks first and
 * then acts, because there is nothing to save afterwards.
 */
public class EditGroupFragment extends BaseFragment {

    private static final String ARG_HOUSEHOLD_ID = "householdId";

    private FragmentEditGroupBinding binding;
    private long householdId;
    private Household household;

    public static Bundle argsFor(long householdId) {
        Bundle args = new Bundle();
        args.putLong(ARG_HOUSEHOLD_ID, householdId);
        return args;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEditGroupBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Insets.padTop(binding.toolbar);
        Insets.padBottom(binding.footer);

        householdId = getArguments() == null
                ? locator().currentHouseholdId() : getArguments().getLong(ARG_HOUSEHOLD_ID);

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        binding.saveButton.setOnClickListener(v -> save());
        binding.managePeopleButton.setOnClickListener(v -> openPeople());
        binding.addOrderButton.setOnClickListener(v -> openNewOrder());
        binding.deleteGroupButton.setOnClickListener(v -> confirmDelete());
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        locator().executors().diskIO().execute(() -> {
            Household group = locator().database().householdDao().getByIdSync(householdId);
            List<Member> people = locator().database().memberDao().getActiveSync(householdId);
            List<Order> orders = locator().database().orderDao().getAllSync(householdId);
            locator().executors().mainThread().execute(() -> render(group, people, orders));
        });
    }

    private void render(Household group, List<Member> people, List<Order> orders) {
        if (binding == null || group == null) {
            return;
        }
        household = group;
        // Only when the field is untouched, or a rename in progress would be wiped out by
        // the reload that follows returning from the people screen.
        if (TextUtils.isEmpty(binding.nameInput.getText())) {
            binding.nameInput.setText(group.name);
        }
        binding.toolbar.setTitle(group.name);

        CurrencyFormat money = new CurrencyFormat(
                locator().settings().currencySymbol(), locator().settings().locale());
        long settled = 0L;
        long open = 0L;
        List<Order> recent = new ArrayList<>();
        for (Order order : orders) {
            if (order.status == OrderStatus.SETTLED) {
                settled += order.statedTotalCents;
            } else {
                open += order.statedTotalCents;
            }
            if (recent.size() < 5) {
                recent.add(order);
            }
        }
        binding.totalSpent.setText(money.format(settled));
        binding.totalOpen.setText(money.format(open));

        binding.ordersLabel.setText(orders.isEmpty()
                ? getString(R.string.edit_group_orders)
                : getString(R.string.edit_group_orders_count, orders.size()));
        binding.orderList.removeAllViews();
        for (Order order : recent) {
            binding.orderList.addView(orderRow(order, money));
        }

        binding.peopleLabel.setText(getString(R.string.edit_group_people_count, people.size()));
        binding.peopleList.removeAllViews();
        for (Member member : people) {
            binding.peopleList.addView(personRow(member));
        }
    }

    private View orderRow(Order order, CurrencyFormat money) {
        ItemEditGroupRowBinding row = ItemEditGroupRowBinding.inflate(
                getLayoutInflater(), binding.orderList, false);
        row.rowTitle.setText(order.label);
        row.rowDetail.setVisibility(View.VISIBLE);
        row.rowDetail.setText(getString(R.string.edit_group_order_detail,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(order.orderDate)),
                order.status == OrderStatus.SETTLED
                        ? getString(R.string.edit_group_settled)
                        : getString(R.string.edit_group_draft)));
        row.rowAmount.setVisibility(View.VISIBLE);
        row.rowAmount.setText(money.format(order.statedTotalCents));
        row.rowAction.setIconResource(R.drawable.ic_back);
        row.rowAction.setRotation(180f);
        row.rowAction.setContentDescription(getString(R.string.edit_group_open_order));

        View.OnClickListener open = v -> {
            Bundle args = new Bundle();
            args.putLong(ParsingArgs.ARG_ORDER_ID, order.id);
            NavHostFragment.findNavController(this).navigate(R.id.summaryFragment, args);
        };
        row.getRoot().setOnClickListener(open);
        row.rowAction.setOnClickListener(open);
        return row.getRoot();
    }

    private View personRow(Member member) {
        ItemEditGroupRowBinding row = ItemEditGroupRowBinding.inflate(
                getLayoutInflater(), binding.peopleList, false);
        row.rowAvatar.setVisibility(View.VISIBLE);
        row.rowAvatar.setText(member.initials());
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(MemberPalette.resolve(requireContext(), member.colorHex));
        row.rowAvatar.setBackground(disc);
        row.rowTitle.setText(member.name);
        // Removing a person is done on the people screen, which already enforces the rule
        // that somebody with history is archived rather than deleted. A second delete
        // button here would be a second place for that rule to be got wrong.
        row.rowAction.setIconResource(R.drawable.ic_edit);
        row.rowAction.setContentDescription(getString(R.string.edit_group_manage_people));
        row.rowAction.setOnClickListener(v -> openPeople());
        row.getRoot().setOnClickListener(v -> openPeople());
        return row.getRoot();
    }

    private void save() {
        String name = binding.nameInput.getText() == null
                ? "" : binding.nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            binding.nameLayout.setError(getString(R.string.edit_group_name_required));
            return;
        }
        binding.nameLayout.setError(null);
        if (household != null && name.equals(household.name)) {
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }
        locator().householdRepository().renameHousehold(householdId, name, result -> {
            if (binding == null) {
                return;
            }
            if (result == null || !result.isOk()) {
                Snackbar.make(binding.getRoot(), R.string.edit_group_save_failed,
                        Snackbar.LENGTH_LONG).show();
                return;
            }
            NavHostFragment.findNavController(this).popBackStack();
        });
    }

    private void openPeople() {
        // The people screen edits whichever group is open, so switching first is not
        // housekeeping: without it, editing the people of a group you are not in would
        // silently edit the people of the one you are.
        if (householdId != locator().currentHouseholdId() && household != null
                && getActivity() instanceof MainActivity) {
            locator().currentHouseholdId(householdId);
        }
        NavHostFragment.findNavController(this).navigate(R.id.setupMembersFragment);
    }

    private void openNewOrder() {
        if (householdId != locator().currentHouseholdId()) {
            locator().currentHouseholdId(householdId);
        }
        NavHostFragment.findNavController(this).navigate(R.id.importFragment);
    }

    /**
     * Deleting a group takes its orders with it, which is not recoverable, so it asks and
     * says what is going.
     */
    private void confirmDelete() {
        if (household == null) {
            return;
        }
        locator().executors().diskIO().execute(() -> {
            int groups = locator().database().householdDao().getAllSync().size();
            locator().executors().mainThread().execute(() -> {
                if (binding == null) {
                    return;
                }
                if (groups <= 1) {
                    // The app has nowhere to go without a group, and an empty state that
                    // exists only after a deletion is a state nobody has ever seen.
                    Snackbar.make(binding.getRoot(), R.string.edit_group_delete_last,
                            Snackbar.LENGTH_LONG).show();
                    return;
                }
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.edit_group_delete_title, household.name))
                        .setMessage(R.string.edit_group_delete_message)
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.edit_group_delete, (d, w) -> delete())
                        .show();
            });
        });
    }

    private void delete() {
        long doomed = householdId;
        locator().executors().diskIO().execute(() -> {
            locator().database().householdDao().deleteById(doomed);
            List<Household> left = locator().database().householdDao().getAllSync();
            long next = left.isEmpty() ? 0L : left.get(0).id;
            if (locator().currentHouseholdId() == doomed) {
                locator().currentHouseholdId(next);
            }
            locator().executors().mainThread().execute(() -> {
                if (binding == null || !isAdded()) {
                    return;
                }
                NavHostFragment.findNavController(this).popBackStack();
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
