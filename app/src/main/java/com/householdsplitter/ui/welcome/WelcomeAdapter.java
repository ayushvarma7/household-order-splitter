package com.householdsplitter.ui.welcome;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.householdsplitter.databinding.ItemWelcomePageBinding;

import java.util.List;

/** The introduction's pages. */
class WelcomeAdapter extends RecyclerView.Adapter<WelcomeAdapter.PageViewHolder> {

    private final List<WelcomePage> pages;

    WelcomeAdapter(List<WelcomePage> pages) {
        this.pages = pages;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PageViewHolder(ItemWelcomePageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        holder.bind(pages.get(position));
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {

        private final ItemWelcomePageBinding binding;

        PageViewHolder(ItemWelcomePageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(WelcomePage page) {
            binding.pageIcon.setImageResource(page.icon);
            binding.pageTitle.setText(page.title);
            binding.pageBody.setText(page.body);
        }
    }
}
