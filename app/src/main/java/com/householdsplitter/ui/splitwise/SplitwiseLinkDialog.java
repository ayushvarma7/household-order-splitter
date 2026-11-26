package com.householdsplitter.ui.splitwise;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.householdsplitter.R;
import com.householdsplitter.core.splitwise.MemberMatcher;
import com.householdsplitter.core.splitwise.SplitwiseGroup;
import com.householdsplitter.core.splitwise.SplitwiseUser;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.splitwise.SplitwiseService;

import java.util.ArrayList;
import java.util.List;

/**
 * Two steps: pick the Splitwise group, then say which group member each household member
 * is. Names are matched for the user where there is exactly one candidate, so the usual
 * case is two taps and a confirm.
 */
public final class SplitwiseLinkDialog {

    private SplitwiseLinkDialog() {
    }

    public static void show(Context context, SplitwiseService service, long householdId,
                            List<Member> members, Runnable onLinked) {
        Toast.makeText(context, R.string.splitwise_loading_groups, Toast.LENGTH_SHORT).show();
        service.loadGroups(result -> {
            if (!result.isOk()) {
                Toast.makeText(context, result.error(), Toast.LENGTH_LONG).show();
                return;
            }
            List<SplitwiseGroup> groups = result.value();
            if (groups == null || groups.isEmpty()) {
                Toast.makeText(context, R.string.splitwise_no_groups, Toast.LENGTH_LONG).show();
                return;
            }
            pickGroup(context, service, householdId, members, groups, onLinked);
        });
    }

    private static void pickGroup(Context context, SplitwiseService service, long householdId,
                                  List<Member> members, List<SplitwiseGroup> groups,
                                  Runnable onLinked) {
        String[] names = new String[groups.size()];
        int selected = 0;
        long current = service.linkedGroupId(householdId);
        for (int i = 0; i < names.length; i++) {
            names[i] = groups.get(i).name();
            if (groups.get(i).id() == current) {
                selected = i;
            }
        }
        final int[] choice = {selected};
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.splitwise_pick_group)
                .setSingleChoiceItems(names, selected, (d, which) -> choice[0] = which)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.splitwise_next, (d, w) ->
                        mapMembers(context, service, householdId, members,
                                groups.get(choice[0]), onLinked))
                .show();
    }

    private static void mapMembers(Context context, SplitwiseService service, long householdId,
                                   List<Member> members, SplitwiseGroup group, Runnable onLinked) {
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad / 2, pad, 0);

        List<String> options = new ArrayList<>();
        options.add(context.getString(R.string.splitwise_not_linked));
        for (SplitwiseUser user : group.members()) {
            options.add(user.displayName());
        }
        List<Spinner> spinners = new ArrayList<>();
        for (Member member : members) {
            TextView label = new TextView(context);
            label.setText(member.name);
            label.setPadding(0, pad / 2, 0, 0);
            column.addView(label);
            Spinner spinner = new Spinner(context);
            spinner.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_spinner_dropdown_item, options));
            long remembered = service.linkedUser(member.id);
            long wanted = remembered != 0L ? remembered
                    : MemberMatcher.suggest(member.name, group.members());
            for (int i = 0; i < group.members().size(); i++) {
                if (group.members().get(i).id() == wanted) {
                    spinner.setSelection(i + 1);
                }
            }
            column.addView(spinner, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            spinners.add(spinner);
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(column);

        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.splitwise_map_title, group.name()))
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.splitwise_save_link, (d, w) -> {
                    long[] memberIds = new long[members.size()];
                    long[] userIds = new long[members.size()];
                    for (int i = 0; i < members.size(); i++) {
                        memberIds[i] = members.get(i).id;
                        int pos = spinners.get(i).getSelectedItemPosition();
                        userIds[i] = pos <= 0 ? 0L : group.members().get(pos - 1).id();
                    }
                    service.saveLink(householdId, group, memberIds, userIds);
                    if (onLinked != null) {
                        onLinked.run();
                    }
                })
                .show();
    }
}
