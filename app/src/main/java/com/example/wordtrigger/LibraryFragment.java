package com.example.wordtrigger;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class LibraryFragment extends Fragment {

    private RecyclerView recyclerView;
    private WordAdapter adapter;
    private List<WordModel> wordList;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    public LibraryFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_library, container, false);

        recyclerView = v.findViewById(R.id.rvWordsLibrary);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        wordList = new ArrayList<>();
        adapter = new WordAdapter(wordList);
        recyclerView.setAdapter(adapter);

        loadWords();
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.returnBut).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ProfileFragment.class);
            startActivity(intent);
        });
    }

    private void loadWords() {
        db.collection("words_library").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                wordList.clear();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    WordModel model = document.toObject(WordModel.class);
                    wordList.add(model);
                }
                adapter.notifyDataSetChanged();
            }
        });
    }
}