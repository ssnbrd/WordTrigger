package com.example.wordtrigger.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.wordtrigger.ui.homeandconnect.ConnectFragment;
import com.example.wordtrigger.ui.homeandconnect.HomeFragment;
import com.example.wordtrigger.ui.profile.ProfileFragment;
import com.example.wordtrigger.R;
import com.example.wordtrigger.ui.study.StudyFragment;


public class TabHostFragment extends Fragment {
    private static final String ARG_TAB = "tab";

    public static TabHostFragment newInstance(int tabMenuId) {
        TabHostFragment f = new TabHostFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_TAB, tabMenuId);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tab_host, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getChildFragmentManager().findFragmentById(R.id.fragment_container) == null) {
            getChildFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.fragment_container, createRoot(requireArguments().getInt(ARG_TAB)))
                    .commit();
        }
    }

    private static Fragment createRoot(int tab) {
        if (tab == R.id.nav_connect) return new ConnectFragment();
        if (tab == R.id.nav_profile) return new ProfileFragment();
        if (tab == R.id.nav_library) return new StudyFragment();
        return new HomeFragment();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden || !isAdded()) return;
        Fragment current = getChildFragmentManager().findFragmentById(R.id.fragment_container);
        if (current instanceof TabAware && current.isAdded()) ((TabAware) current).onTabShown();
    }
}
