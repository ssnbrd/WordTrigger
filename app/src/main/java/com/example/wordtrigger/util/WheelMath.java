package com.example.wordtrigger.util;

import java.util.Random;

public final class WheelMath {
    private WheelMath() {}

    public static int[] candidates(int bankSize, int count, String date, String lang) {
        int n = Math.min(count, bankSize);
        int[] idx = new int[bankSize];
        for (int i = 0; i < bankSize; i++) idx[i] = i;
        Random r = new Random((date + "|" + lang).hashCode());
        for (int i = 0; i < n; i++) {
            int j = i + r.nextInt(bankSize - i);
            int t = idx[i]; idx[i] = idx[j]; idx[j] = t;
        }
        int[] out = new int[n];
        System.arraycopy(idx, 0, out, 0, n);
        return out;
    }

    public static int sectorAtTop(int sectors, float rotation) {
        double angle = ((-rotation) % 360 + 360) % 360;
        return (int) (angle / (360.0 / sectors)) % sectors;
    }

    public static float targetRotation(int sectors, int landing, float offset, float currentRotation, int fullTurns) {
        float sector = 360f / sectors;
        float angleFromTop = (landing + 0.5f + offset) * sector;
        float base = (((-angleFromTop) % 360) + 360) % 360;
        float floor = (float) Math.floor(currentRotation / 360f) * 360f;
        return floor + fullTurns * 360f + base;
    }
}
