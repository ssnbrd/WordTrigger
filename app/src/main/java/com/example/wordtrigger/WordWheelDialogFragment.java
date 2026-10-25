package com.example.wordtrigger;

import android.app.Dialog;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.util.List;
import java.util.Random;


public class WordWheelDialogFragment extends DialogFragment {
    public static final String TAG = "word_wheel";

    private FortuneWheelView wheel;
    private View wheelPanel, resultPanel;
    private Button btnSpin;
    private int[] candidates;
    private List<WordOfDay.Entry> bank;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, R.style.WheelDialog);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        Window w = d.getWindow();
        if (w != null) w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        return d;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_word_wheel, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        wheel = v.findViewById(R.id.wheel);
        wheelPanel = v.findViewById(R.id.wheelPanel);
        resultPanel = v.findViewById(R.id.resultPanel);
        btnSpin = v.findViewById(R.id.btnSpin);

        v.findViewById(R.id.btnWheelClose).setOnClickListener(x -> dismissAllowingStateLoss());
        v.findViewById(R.id.btnGotIt).setOnClickListener(x -> dismissAllowingStateLoss());

        bank = WordOfDay.bank(requireContext());
        WordOfDay.Entry already = WordOfDay.today(requireContext());
        if (already != null) {
            showResult(already);
            return;
        }
        if (bank.size() < WordOfDay.SECTORS) {
            dismissAllowingStateLoss();
            return;
        }

        candidates = WordOfDay.todayCandidates(requireContext());
        String[] labels = new String[candidates.length];
        for (int i = 0; i < candidates.length; i++) labels[i] = bank.get(candidates[i]).word;
        wheel.setLabels(labels);
        btnSpin.setOnClickListener(x -> spin());
    }

    private void spin() {
        if (wheel.isSpinning()) return;
        btnSpin.setEnabled(false);
        Random rnd = new Random();
        int landing = rnd.nextInt(candidates.length);
        float offset = (rnd.nextFloat() - 0.5f) * 0.7f;
        int turns = 5 + rnd.nextInt(3);
        float target = WheelMath.targetRotation(candidates.length, landing, offset, wheel.getRotation(), turns);
        wheel.spinTo(target, 5000, () -> {
            if (!isAdded()) return;
            int bankIndex = candidates[WheelMath.sectorAtTop(candidates.length, wheel.getRotation())];
            WordOfDay.saveToday(requireContext(), bankIndex);
            wheel.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showResult(bank.get(bankIndex));
        });
    }

    private void showResult(WordOfDay.Entry e) {
        View v = requireView();
        ((TextView) v.findViewById(R.id.tvWord)).setText(capitalize(e.word));
        ((TextView) v.findViewById(R.id.tvDefinition)).setText(capitalize(e.definition));
        ((TextView) v.findViewById(R.id.tvExample)).setText(
                getString(R.string.wod_example_label) + ": " + e.example);
        wheelPanel.setVisibility(View.GONE);
        resultPanel.setAlpha(0f);
        resultPanel.setVisibility(View.VISIBLE);
        resultPanel.animate().alpha(1f).setDuration(350).start();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    @Override
    public void onDestroyView() {
        if (wheel != null) wheel.cancelSpin();
        super.onDestroyView();
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);

        if (getParentFragmentManager().isStateSaved()) return;
        androidx.fragment.app.FragmentActivity a = getActivity();
        if (a instanceof MainActivity) ((MainActivity) a).onWordWheelClosed();
    }
}
