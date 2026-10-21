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
        EdgeInsets.apply(this);
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
                Toast.makeText(this, getString(R.string.auth_fill_all), Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener(task -> {
                if(task.isSuccessful()) {
                    startActivity(new Intent(this, MainActivity.class));
                } else {
                    Toast.makeText(this, getString(R.string.auth_bad_login), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
    private void resetPassword(String email) {
        if (email.isEmpty()) {
            Toast.makeText(this, getString(R.string.auth_enter_email), Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, getString(R.string.auth_reset_sent), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, getString(R.string.auth_error, String.valueOf(task.getException() == null ? "" : task.getException().getMessage())), Toast.LENGTH_SHORT).show();
                    }
                });
    }
    private void showResetPasswordDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reset_password, null);
        final EditText resetMail = dialogView.findViewById(R.id.etResetEmail);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.auth_reset_title))
                .setMessage(getString(R.string.auth_reset_msg))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.auth_send), (dialog, which) -> {
                    resetPassword(resetMail.getText().toString().trim());
                })
                .setNegativeButton(getString(R.string.auth_cancel), null)
                .show()
                .getWindow().setBackgroundDrawableResource(android.R.color.white);
    }
}