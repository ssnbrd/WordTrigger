package com.example.wordtrigger;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EdgeInsets.apply(this);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new HomeFragment()).commit();

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_home) selectedFragment = new HomeFragment();
            else if (id == R.id.nav_connect) selectedFragment = new ConnectFragment();
            else if (id == R.id.nav_profile) selectedFragment = new ProfileFragment();
            else if (id == R.id.nav_library) selectedFragment = new StudyFragment();

            if (selectedFragment != null) {
                getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment).commit();
            }
            return true;
        });

        // виджет серии открывает вкладку «Учёба»
        if (savedInstanceState == null && getIntent().getBooleanExtra(StreakWidgetProvider.EXTRA_OPEN_STUDY, false)) {
            bottomNav.setSelectedItemId(R.id.nav_library);
        }
    }
}