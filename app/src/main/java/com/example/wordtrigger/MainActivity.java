package com.example.wordtrigger;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    // Android 13+: без этого разрешения уведомление «система активна» скрыто («Suppressing notification»)
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EdgeInsets.apply(this);

        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }

        bottomNav = findViewById(R.id.bottom_navigation);
        if (savedInstanceState == null) showTab(R.id.nav_home);

        bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        // повторное нажатие на текущую вкладку возвращает её к главному экрану
        bottomNav.setOnItemReselectedListener(item -> {
            Fragment host = getSupportFragmentManager().findFragmentByTag(tag(item.getItemId()));
            if (host != null) host.getChildFragmentManager().popBackStack(null,
                    androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        });

        boolean fromWidget = getIntent().getBooleanExtra(StreakWidgetProvider.EXTRA_OPEN_STUDY, false);
        if (savedInstanceState == null && fromWidget) {
            bottomNav.setSelectedItemId(R.id.nav_library);
        }
        // раз в день при входе в приложение: колесо слова дня поверх главного экрана
        if (savedInstanceState == null && !fromWidget && !WordOfDay.hasToday(this)) {
            openWordWheel();
        }
    }

    /** Открывает колесо слова дня (или сразу выпавшее слово, если колесо уже крутили сегодня). */
    public void openWordWheel() {
        androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
        if (fm.isStateSaved() || fm.findFragmentByTag(WordWheelDialogFragment.TAG) != null) return;
        new WordWheelDialogFragment().show(fm, WordWheelDialogFragment.TAG);
    }

    /** Колесо закрыли: карточка слова дня на главном экране обновляется. */
    public void onWordWheelClosed() {
        Fragment host = getSupportFragmentManager().findFragmentByTag(tag(R.id.nav_home));
        if (host == null) return;
        Fragment current = host.getChildFragmentManager().findFragmentById(R.id.fragment_container);
        if (current instanceof TabAware) ((TabAware) current).onTabShown();
    }

    private BottomNavigationView bottomNav;
    private static final int[] TABS = {R.id.nav_home, R.id.nav_connect, R.id.nav_library, R.id.nav_profile};

    private static String tag(int menuId) {
        return "tab_" + menuId;
    }

    /** Переключение вкладки без пересоздания: остальные вкладки скрываются, но живут. */
    private void showTab(int menuId) {
        androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
        androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        Fragment target = fm.findFragmentByTag(tag(menuId));
        for (int t : TABS) {
            Fragment f = fm.findFragmentByTag(tag(t));
            if (f != null && f != target) tx.hide(f);
        }
        if (target == null) {
            target = TabHostFragment.newInstance(menuId);
            tx.add(R.id.fragment_container, target, tag(menuId));
        } else {
            tx.show(target);
        }
        // системная кнопка «назад» сначала закрывает вложенные экраны активной вкладки
        tx.setPrimaryNavigationFragment(target).commit();
    }

    /** Для экранов, у которых стрелка «назад» ведёт на главную вкладку. */
    public void selectTab(int menuId) {
        bottomNav.setSelectedItemId(menuId);
    }
}
