package com.example.wordtrigger;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ArticlesListFragment extends Fragment {
    RecyclerView rv;
    ArticleAdapter adapter;
    List<ArticleModel> list = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_article, container, false);
        rv = v.findViewById(R.id.rvWordsLibrary);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);
        adapter = new ArticleAdapter(list, article -> {
            ArticleDetailFragment detail = new ArticleDetailFragment();
            Bundle b = new Bundle();
            b.putString("title", article.getTitle());
            b.putString("content", article.getContent());
            detail.setArguments(b);

            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, detail)
                    .addToBackStack(null)
                    .commit();
        });
        rv.setAdapter(adapter);

        loadArticles();
        return v;
    }

    private void loadArticles() {
        FirebaseFirestore.getInstance().collection("articles").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    list.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        list.add(doc.toObject(ArticleModel.class));
                    }
                    adapter.notifyDataSetChanged();

                });
    }
}