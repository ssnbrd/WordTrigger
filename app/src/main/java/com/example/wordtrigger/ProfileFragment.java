package com.example.wordtrigger;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String uid = FirebaseAuth.getInstance().getUid();
    private ChipGroup chipGroup;

    private void showBattery(View v) {
        int percent = DeviceBattery.get(requireContext());
        TextView tvPercent = v.findViewById(R.id.tvBatteryPercent);
        android.widget.ProgressBar pb = v.findViewById(R.id.pbBattery);
        View hint = v.findViewById(R.id.tvBatteryHint);
        if (percent < 0) {
            tvPercent.setText("—");
            pb.setProgress(0);
            hint.setVisibility(View.VISIBLE);
        } else {
            tvPercent.setText(percent + "%");
            pb.setProgress(percent);
            hint.setVisibility(View.GONE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);
        TextView tvName = v.findViewById(R.id.tvUserName);
        chipGroup = v.findViewById(R.id.contextChipGroup);
        ImageView logoutBtn = v.findViewById(R.id.ivLogout);
        //ImageView backArrow = v.findViewById(R.id.returnBut);
        Button btnSaveContext = v.findViewById(R.id.btnSaveContext);
        ImageView btnBack = v.findViewById(R.id.btnBack);

        ImageView ivAvatar = v.findViewById(R.id.ivAvatar);
        AvatarStore.showLocal(requireContext(), ivAvatar);

        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                tvName.setText(doc.getString("username"));
                if (isAdded()) AvatarStore.syncFromDoc(requireContext(), doc, ivAvatar);
            }
        });

        View.OnClickListener openSettings = view ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new SettingsFragment())
                        .addToBackStack(null)
                        .commit();
        v.findViewById(R.id.layoutUserHeader).setOnClickListener(openSettings);
        v.findViewById(R.id.ivSettings).setOnClickListener(openSettings);
        showBattery(v);

        logoutBtn.setOnClickListener(view -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
                    .setTitle(getString(R.string.profile_logout_title))
                    .setMessage(getString(R.string.profile_logout_msg))
                    .setPositiveButton(getString(R.string.profile_logout_yes), (dialogInterface, i) -> {
                        FirebaseAuth.getInstance().signOut();
                        Intent intent = new Intent(getActivity(), WelcomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    })
                    .setNegativeButton(getString(R.string.auth_cancel), null)
                    .show();
                    //.getWindow().setBackgroundDrawableResource(android.R.color.white);
        });

//        backArrow.setOnClickListener(view -> {
//            BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_navigation);
//            bottomNav.setSelectedItemId(R.id.nav_connect);
//        });

        db.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String name = documentSnapshot.getString("username");
                if (name != null) tvName.setText(name);
            }
        });

        btnSaveContext.setOnClickListener(view -> {
            List<Integer> ids = chipGroup.getCheckedChipIds();
            List<String> selectedContexts = new ArrayList<>();

            for (Integer id : ids) {
                Chip chip = chipGroup.findViewById(id);
                selectedContexts.add(chip.getText().toString());
            }

            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            db.collection("users").document(uid).update(
                    "active_contexts", selectedContexts,
                    "last_context_date", today
            ).addOnSuccessListener(aVoid -> {
                Toast.makeText(getContext(), getString(R.string.profile_context_saved), Toast.LENGTH_SHORT).show();
            }).addOnFailureListener(e -> {
                Toast.makeText(getContext(), getString(R.string.profile_save_error), Toast.LENGTH_SHORT).show();
            });
        });
        btnBack.setOnClickListener(view -> {
            getParentFragmentManager().popBackStack();
        });

        v.findViewById(R.id.cardArticles).setOnClickListener(view -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new ArticlesListFragment())
                    .addToBackStack(null)
                    .commit();
        });

        v.findViewById(R.id.cardLibrary).setOnClickListener(view -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new LibraryFragment())
                    .addToBackStack(null)
                    .commit();
        });

        checkAndResetContext();

        loadCurrentContexts();

        return v;
    }

    private void checkAndResetContext() {
        if (uid == null) return;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String lastDate = doc.getString("last_context_date");
                if (lastDate == null || !lastDate.equals(today)) {
                    chipGroup.clearCheck();
                    db.collection("users").document(uid).update(
                            "active_contexts", new ArrayList<>(),
                            "last_context_date", today
                    );
                }
            }
        });
    }
    private void loadCurrentContexts() {
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists() && doc.get("active_contexts") != null) {
                List<String> saved = (List<String>) doc.get("active_contexts");
                for (int i = 0; i < chipGroup.getChildCount(); i++) {
                    Chip chip = (Chip) chipGroup.getChildAt(i);
                    if (saved.contains(chip.getText().toString())) {
                        chip.setChecked(true);
                    }
                }
            }
        });
    }
}