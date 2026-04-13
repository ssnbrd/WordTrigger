package com.example.wordtrigger;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class WordAdapter extends RecyclerView.Adapter<WordAdapter.WordViewHolder> {
    private List<WordModel> wordList;

    public WordAdapter(List<WordModel> wordList) {
        this.wordList = wordList;
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_word, parent, false);
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        WordModel item = wordList.get(position);
        holder.tvTitle.setText(item.word);
        holder.tvDef.setText(item.definition);
        holder.tvSyn.setText(item.synonyms);
    }

    @Override
    public int getItemCount() { return wordList.size(); }

    static class WordViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDef, tvSyn;
        public WordViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvWordTitle);
            tvDef = itemView.findViewById(R.id.tvDefinition);
            tvSyn = itemView.findViewById(R.id.tvSynonyms);
        }
    }
}