package com.example.wordtrigger;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class ArticleDetailFragment extends Fragment {
    private TextView title;
    private TextView content;
    private ImageView btnBack;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_article_detail, container, false);

        title = v.findViewById(R.id.tvArticleTitle);
        content = v.findViewById(R.id.tvArticleContent);
        btnBack = v.findViewById(R.id.btnBack);

        if (getArguments() != null) {
            title.setText(getArguments().getString("title"));
            content.setText(getArguments().getString("content"));
        }

        btnBack.setOnClickListener(view -> {
            getParentFragmentManager().popBackStack();
        });

        return v;
    }
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            String title = getArguments().getString("title");
            String content = getArguments().getString("content");

            TextView tvTitle = view.findViewById(R.id.tvArticleTitle);
            TextView tvContent = view.findViewById(R.id.tvArticleContent);

            tvTitle.setText(title);
            tvContent.setText(content);
        }
    }
}