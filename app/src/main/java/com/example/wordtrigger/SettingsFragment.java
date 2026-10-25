package com.example.wordtrigger;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsFragment extends Fragment {

    private interface Validator { @StringRes int error(String value); }
    private interface Saver { void save(String value); }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String uid = FirebaseAuth.getInstance().getUid();

    private ImageView ivAvatar;
    private TextView tvName, tvEmail, tvPhone, pillRu, pillEn;

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) handlePhoto(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        ivAvatar = v.findViewById(R.id.ivAvatar);
        tvName = v.findViewById(R.id.tvName);
        tvEmail = v.findViewById(R.id.tvEmail);
        tvPhone = v.findViewById(R.id.tvPhone);
        pillRu = v.findViewById(R.id.pillRu);
        pillEn = v.findViewById(R.id.pillEn);

        v.findViewById(R.id.returnBut).setOnClickListener(x -> getParentFragmentManager().popBackStack());

        AvatarStore.showLocal(requireContext(), ivAvatar);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) tvEmail.setText(user.getEmail());
        loadProfile();

        View.OnClickListener photoClick = x -> onPhotoClicked();
        ivAvatar.setOnClickListener(photoClick);
        v.findViewById(R.id.tvEditPhoto).setOnClickListener(photoClick);

        v.findViewById(R.id.rowName).setOnClickListener(x -> showEditDialog(
                R.string.settings_edit_name, R.string.settings_name_hint, text(tvName),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS,
                value -> value.isEmpty() ? R.string.settings_err_empty : 0,
                this::saveName));
        v.findViewById(R.id.rowPhone).setOnClickListener(x -> showEditDialog(
                R.string.settings_edit_phone, R.string.settings_phone_hint, text(tvPhone),
                InputType.TYPE_CLASS_PHONE,
                value -> value.replaceAll("[^0-9]", "").length() >= 7 && value.replaceAll("[^0-9]", "").length() <= 15
                        ? 0 : R.string.settings_err_phone,
                this::savePhone));
        v.findViewById(R.id.rowEmail).setOnClickListener(x -> showEditDialog(
                R.string.settings_edit_email, R.string.settings_email_hint, text(tvEmail),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                value -> Patterns.EMAIL_ADDRESS.matcher(value).matches() ? 0 : R.string.settings_err_email,
                this::requestEmailChange));

        pillRu.setOnClickListener(x -> setLanguage("ru"));
        pillEn.setOnClickListener(x -> setLanguage("en"));
        updatePills();

        MaterialSwitch aiSwitch = v.findViewById(R.id.switchAiTopic);
        aiSwitch.setChecked(AppSettings.isAiCheckEnabled(requireContext()));
        aiSwitch.setOnCheckedChangeListener((b, checked) ->
                AppSettings.setAiCheckEnabled(requireContext(), checked));
    }

    private static String text(TextView tv) {
        return tv.getText().toString();
    }

    private void loadProfile() {
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded() || !doc.exists()) return;
            tvName.setText(doc.getString("username"));
            tvPhone.setText(doc.getString("phone"));
            AvatarStore.syncFromDoc(requireContext(), doc, ivAvatar);
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            user.reload().addOnSuccessListener(x -> {
                FirebaseUser fresh = FirebaseAuth.getInstance().getCurrentUser();
                if (!isAdded() || fresh == null || fresh.getEmail() == null) return;
                tvEmail.setText(fresh.getEmail());
                db.collection("users").document(uid).update("email", fresh.getEmail());
            });
        }
    }

    private void saveName(String value) {
        updateField("username", value, () -> tvName.setText(value));
    }

    private void savePhone(String value) {
        updateField("phone", value, () -> tvPhone.setText(value));
    }

    private void updateField(String field, String value, Runnable onOk) {
        if (uid == null) return;
        db.collection("users").document(uid).update(field, value)
                .addOnSuccessListener(x -> {
                    if (!isAdded()) return;
                    onOk.run();
                    toast(R.string.settings_saved);
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) toast(R.string.settings_save_failed);
                });
    }

    private void requestEmailChange(String newEmail) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        user.verifyBeforeUpdateEmail(newEmail)
                .addOnSuccessListener(x -> {
                    if (isAdded()) Toast.makeText(requireContext(),
                            getString(R.string.settings_email_sent, newEmail), Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    if (e instanceof FirebaseAuthRecentLoginRequiredException) {
                        askPasswordThen(() -> requestEmailChange(newEmail));
                    } else {
                        Toast.makeText(requireContext(), String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void askPasswordThen(Runnable onConfirmed) {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.settings_reauth_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_reauth_title)
                .setView(wrap(input))
                .setPositiveButton(R.string.settings_save, (d, w) -> {
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user == null || user.getEmail() == null) return;
                    user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), input.getText().toString()))
                            .addOnSuccessListener(x -> onConfirmed.run())
                            .addOnFailureListener(e -> {
                                if (isAdded()) toast(R.string.settings_reauth_wrong);
                            });
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private FrameLayout wrap(EditText input) {
        FrameLayout box = new FrameLayout(requireContext());
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private void showEditDialog(@StringRes int title, @StringRes int hint, String current,
                                int inputType, Validator validator, Saver saver) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setInputType(inputType);
        input.setText(current);
        input.setSelection(input.getText().length());

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(wrap(input))
                .setPositiveButton(R.string.settings_save, null)
                .setNegativeButton(R.string.settings_cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(b -> {
                    String value = input.getText().toString().trim();
                    int err = validator.error(value);
                    if (err != 0) {
                        input.setError(getString(err));
                        return;
                    }
                    dialog.dismiss();
                    if (!value.equals(current)) saver.save(value);
                }));
        dialog.show();
    }

    private void onPhotoClicked() {
        if (AvatarStore.loadLocal(requireContext()) == null) {
            launchPicker();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setItems(new CharSequence[]{
                        getString(R.string.settings_choose_photo),
                        getString(R.string.settings_remove_photo)}, (d, which) -> {
                    if (which == 0) launchPicker(); else removePhoto();
                })
                .show();
    }

    private void launchPicker() {
        photoPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void handlePhoto(android.net.Uri uri) {
        final android.content.Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            try {
                Bitmap bmp = AvatarStore.decodeSquare(app.getContentResolver(), uri);
                String data = AvatarStore.toBase64(bmp);
                AvatarStore.saveLocal(app, bmp);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    AvatarStore.show(ivAvatar, bmp);
                    if (uid != null) {
                        db.collection("users").document(uid).update("avatar", data)
                                .addOnSuccessListener(x -> { if (isAdded()) toast(R.string.settings_photo_updated); })
                                .addOnFailureListener(e -> { if (isAdded()) toast(R.string.settings_save_failed); });
                    }
                });
            } catch (Exception e) {
                if (isAdded()) requireActivity().runOnUiThread(() -> toast(R.string.settings_photo_failed));
            }
        }).start();
    }

    private void removePhoto() {
        AvatarStore.deleteLocal(requireContext());
        AvatarStore.show(ivAvatar, null);
        if (uid != null) db.collection("users").document(uid).update("avatar", FieldValue.delete());
    }

    private void updatePills() {
        boolean ru = "ru".equals(AppSettings.currentLanguage(requireContext()));
        pillRu.setBackgroundResource(ru ? R.drawable.bg_pill_blue : R.drawable.bg_pill_gray);
        pillEn.setBackgroundResource(ru ? R.drawable.bg_pill_gray : R.drawable.bg_pill_blue);
    }

    private void setLanguage(String tag) {
        if (tag.equals(AppSettings.currentLanguage(requireContext()))) return;

        if (uid != null) db.collection("users").document(uid).update("language", tag);
        AppSettings.setLanguage(requireContext(), tag);

        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
    }

    private void toast(@StringRes int res) {
        Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show();
    }
}
