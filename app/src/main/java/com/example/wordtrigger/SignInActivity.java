package com.example.wordtrigger;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
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

        findViewById(R.id.resetPassword).setOnClickListener(v -> {
            showResetPasswordDialog();
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
    private void resetPassword(String email) {
        if (email.isEmpty()) {
            Toast.makeText(this, "Введите ваш email", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Письмо для сброса пароля отправлено на вашу почту", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Ошибка: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
    private void showResetPasswordDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reset_password, null);
        final EditText resetMail = dialogView.findViewById(R.id.etResetEmail);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Восстановление пароля")
                .setMessage("Введите email, на который зарегистрирован аккаунт")
                .setView(dialogView)
                .setPositiveButton("Отправить", (dialog, which) -> {
                    resetPassword(resetMail.getText().toString().trim());
                })
                .setNegativeButton("Отмена", null)
                .show()
                .getWindow().setBackgroundDrawableResource(android.R.color.white);
    }
}