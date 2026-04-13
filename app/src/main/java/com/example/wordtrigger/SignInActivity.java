package com.example.wordtrigger;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

public class SignInActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);
        mAuth = FirebaseAuth.getInstance();

        findViewById(R.id.returnBut).setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
        });

        findViewById(R.id.tvToSignUp).setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
        });

        findViewById(R.id.btnDoSignIn).setOnClickListener(v -> {
            String email = ((EditText)findViewById(R.id.etLoginEmail)).getText().toString();
            String pass = ((EditText)findViewById(R.id.etLoginPass)).getText().toString();

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Заполните все поля!", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener(task -> {
                if(task.isSuccessful()) {
                    startActivity(new Intent(this, MainActivity.class));
                } else {
                    Toast.makeText(this, "Неверный логин или пароль", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

}