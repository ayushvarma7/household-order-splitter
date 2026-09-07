package com.householdsplitter.ui.rules;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.MemberRule;
import com.householdsplitter.data.repo.HouseholdRepository;
import com.householdsplitter.suggest.RuleService;
import com.householdsplitter.util.Callback;
import com.householdsplitter.util.Result;

import java.util.ArrayList;
import java.util.List;

/** Standing rules. SPEC 4.5: no logic in the fragment, so the draft state lives here. */
public class RulesViewModel extends ViewModel {

    private final RuleService rules;
    private final long householdId;
    private final LiveData<List<MemberRule>> stored;
    private final LiveData<List<Member>> members;

    private final MutableLiveData<Long> pendingMemberId = new MutableLiveData<>(0L);
    private final MutableLiveData<MemberRule.Kind> pendingKind =
            new MutableLiveData<>(MemberRule.Kind.EXCLUDE);

    public RulesViewModel(RuleService rules, HouseholdRepository households, long householdId) {
        this.rules = rules;
        this.householdId = householdId;
        this.stored = rules.observe(householdId);
        this.members = households.observeMembers(householdId);
    }

    public LiveData<List<MemberRule>> rules() {
        return stored;
    }

    public LiveData<List<Member>> members() {
        return members;
    }

    public LiveData<Long> pendingMemberId() {
        return pendingMemberId;
    }

    public LiveData<MemberRule.Kind> pendingKind() {
        return pendingKind;
    }

    public void pickMember(long memberId) {
        pendingMemberId.setValue(memberId);
    }

    public void pickKind(MemberRule.Kind kind) {
        pendingKind.setValue(kind);
    }

    public List<Member> memberList() {
        List<Member> value = members.getValue();
        return value == null ? new ArrayList<>() : value;
    }

    public String memberName(long memberId) {
        for (Member member : memberList()) {
            if (member.id == memberId) {
                return member.name;
            }
        }
        return "";
    }

    public Member member(long memberId) {
        for (Member member : memberList()) {
            if (member.id == memberId) {
                return member;
            }
        }
        return null;
    }

    public void add(String keyword, Callback<Result<Long>> callback) {
        Long memberId = pendingMemberId.getValue();
        MemberRule.Kind kind = pendingKind.getValue();
        rules.add(householdId, memberId == null ? 0L : memberId, keyword,
                kind == null ? MemberRule.Kind.EXCLUDE : kind, callback);
    }

    public void delete(long ruleId, Callback<Result<Void>> callback) {
        rules.delete(householdId, ruleId, callback);
    }
}
