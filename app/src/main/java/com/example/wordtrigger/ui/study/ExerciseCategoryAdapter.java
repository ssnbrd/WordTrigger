package com.example.wordtrigger.ui.study;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wordtrigger.R;

import java.util.List;

public class ExerciseCategoryAdapter extends RecyclerView.Adapter<ExerciseCategoryAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(ExerciseCategory category);
    }

    private final List<ExerciseCategory> list;
    private final OnItemClickListener listener;

    public ExerciseCategoryAdapter(List<ExerciseCategory> list, OnItemClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_exercise_category, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExerciseCategory item = list.get(position);
        holder.title.setText(item.titleRes);
        holder.desc.setText(item.descRes);

        int cardColor = item.completedToday ? R.color.dark_gray : R.color.white;
        int textColor = item.completedToday ? R.color.white : R.color.black;
        int arrowColor = item.completedToday ? R.color.white : R.color.blue;

        holder.card.setCardBackgroundColor(ContextCompat.getColor(holder.card.getContext(), cardColor));
        holder.title.setTextColor(ContextCompat.getColor(holder.card.getContext(), textColor));
        holder.desc.setTextColor(ContextCompat.getColor(holder.card.getContext(), textColor));
        ImageViewCompat.setImageTintList(holder.arrow,
                ContextCompat.getColorStateList(holder.card.getContext(), arrowColor));

        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final CardView card;
        final TextView title;
        final TextView desc;
        final ImageView arrow;

        ViewHolder(View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.cardExercise);
            title = itemView.findViewById(R.id.tvExerciseTitle);
            desc = itemView.findViewById(R.id.tvExerciseDesc);
            arrow = itemView.findViewById(R.id.ivExerciseArrow);
        }
    }
}
