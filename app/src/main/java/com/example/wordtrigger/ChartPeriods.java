package com.example.wordtrigger;

import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

/** Данные для графика главного экрана за неделю, месяц или год. Чистая логика без Android. */
public final class ChartPeriods {
    public static final int WEEK = 0, MONTH = 1, YEAR = 2;

    private ChartPeriods() {}

    public static String key(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Неделя: 7 значений Пн..Вс текущей недели. Месяц: по дню текущего месяца. Год: сумма по каждому из 12 месяцев. */
    public static int[] values(int mode, Map<String, Integer> byDate, Calendar now) {
        if (mode == YEAR) {
            int[] out = new int[12];
            int year = now.get(Calendar.YEAR);
            for (int m = 0; m < 12; m++) {
                String prefix = String.format(Locale.US, "%04d-%02d-", year, m + 1);
                for (Map.Entry<String, Integer> e : byDate.entrySet()) {
                    if (e.getKey().startsWith(prefix) && e.getValue() != null) out[m] += e.getValue();
                }
            }
            return out;
        }
        if (mode == MONTH) {
            Calendar c = (Calendar) now.clone();
            int days = c.getActualMaximum(Calendar.DAY_OF_MONTH);
            int[] out = new int[days];
            for (int d = 1; d <= days; d++) {
                c.set(Calendar.DAY_OF_MONTH, d);
                Integer v = byDate.get(key(c));
                out[d - 1] = v == null ? 0 : v;
            }
            return out;
        }
        Calendar c = (Calendar) now.clone();
        int dow = c.get(Calendar.DAY_OF_WEEK);
        c.add(Calendar.DAY_OF_YEAR, dow == Calendar.SUNDAY ? -6 : Calendar.MONDAY - dow);
        int[] out = new int[7];
        for (int i = 0; i < 7; i++) {
            Integer v = byDate.get(key(c));
            out[i] = v == null ? 0 : v;
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return out;
    }
}
