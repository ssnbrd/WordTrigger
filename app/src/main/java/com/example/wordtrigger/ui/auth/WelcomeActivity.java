package com.example.wordtrigger.ui.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wordtrigger.util.EdgeInsets;
import com.example.wordtrigger.ui.main.MainActivity;
import com.example.wordtrigger.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class WelcomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        EdgeInsets.apply(this, false);

        findViewById(R.id.btnGetStarted).setOnClickListener(v -> {
            startActivity(new Intent(this, SignInActivity.class));
        });
    }
    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            Intent main = new Intent(this, MainActivity.class);
            main.putExtras(getIntent());
            startActivity(main);
            finish();
        }
    }
}