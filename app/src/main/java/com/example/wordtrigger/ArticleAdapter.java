package com.example.wordtrigger;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ArticleAdapter extends RecyclerView.Adapter<ArticleAdapter.ViewHolder> {
    List<ArticleModel> list;
    public interface OnItemClickListener {
        void onItemClick(ArticleModel article);
    }
    private OnItemClickListener listener;

    public ArticleAdapter(List<ArticleModel> list, OnItemClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_article, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ArticleModel item = list.get(position);
        holder.title.setText(item.title);
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));

    }

    @Override
    public int getItemCount() { return list.size(); }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        public ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.articleTitle);
        }
    }
}
