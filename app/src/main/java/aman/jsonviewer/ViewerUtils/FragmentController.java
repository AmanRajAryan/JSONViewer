package aman.jsonviewer;

import android.util.Log;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.tabs.TabLayout;
import java.util.HashMap;
import java.util.Map;

public class FragmentController {

    private final FragmentManager fragmentManager;
    private final int containerId;
    private final TabLayout tabLayout;
    private final Map<String, CachedData> fragmentCache = new HashMap<>();
    
    private Fragment currentFragment;
    private final Fragment[] fragments = new Fragment[5];
    
    private static final String[] TAGS = {
        "TAG_TREE", "TAG_RAW", "TAG_CARD", "TAG_PRETTY", "TAG_FLOW"
    };

    private Runnable onFragmentChangedListener;

    public FragmentController(FragmentManager fm, int containerId, TabLayout tabLayout) {
        this.fragmentManager = fm;
        this.containerId = containerId;
        this.tabLayout = tabLayout;
        setupTabs();
    }
    
    public void setOnFragmentChangedListener(Runnable listener) {
        this.onFragmentChangedListener = listener;
    }

    public void restoreState(android.os.Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            for (int i = 0; i < TAGS.length; i++) {
                fragments[i] = fragmentManager.findFragmentByTag(TAGS[i]);
            }
            int selectedTab = savedInstanceState.getInt("selected_tab", 0);
            TabLayout.Tab tab = tabLayout.getTabAt(selectedTab);
            if (tab != null) {
                tab.select();
                loadFragment(selectedTab);
            }
        } else {
            loadFragment(0);
        }
    }

    public void saveState(android.os.Bundle outState) {
        outState.putInt("selected_tab", tabLayout.getSelectedTabPosition());
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Tree").setIcon(R.drawable.ic_tree));
        tabLayout.addTab(tabLayout.newTab().setText("Pretty").setIcon(R.drawable.ic_format));
        tabLayout.addTab(tabLayout.newTab().setText("Flow").setIcon(R.drawable.ic_flow));
        tabLayout.addTab(tabLayout.newTab().setText("Cards").setIcon(R.drawable.ic_cards));
        tabLayout.addTab(tabLayout.newTab().setText("Raw").setIcon(R.drawable.ic_code));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                loadFragment(tab.getPosition());
                if (onFragmentChangedListener != null) {
                    onFragmentChangedListener.run();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadFragment(int position) {
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        if (currentFragment != null) {
            trimFragmentCache(currentFragment);
            transaction.hide(currentFragment);
        }

        Fragment targetFragment = fragments[position];
        String tag = TAGS[position];

        if (targetFragment == null) {
            targetFragment = fragmentManager.findFragmentByTag(tag);

            if (targetFragment == null) {
                targetFragment = createFragment(position);
                transaction.add(containerId, targetFragment, tag);
            } else {
                transaction.show(targetFragment);
            }
            fragments[position] = targetFragment;
        } else {
            transaction.show(targetFragment);
        }

        currentFragment = targetFragment;
        transaction.commitNow(); // Immediate commit so search can access it
    }

    private Fragment createFragment(int position) {
        switch (position) {
            case 0: return TreeViewFragment.newInstance();
            case 1: return PrettyViewFragment.newInstance();
            case 2: return FlowChartViewFragment.newInstance();
            case 3: return CardViewFragment.newInstance();
            case 4: return RawViewFragment.newInstance();
            default: return TreeViewFragment.newInstance();
        }
    }

    public Fragment getCurrentFragment() {
        return currentFragment;
    }

    // --- Cache Management ---

    public CachedData getCachedData(String key) {
        return fragmentCache.get(key);
    }

    public void setCachedData(String key, CachedData data) {
        fragmentCache.put(key, data);
    }

    private void trimFragmentCache(Fragment fragment) {
        // Logic to trim heavy resources from hidden fragments if needed
        try {
            if (fragment instanceof PrettyViewFragment) {
                // Specific cleanup if required
            }
        } catch (Exception e) {
            Log.e("FragmentController", "Error trimming cache", e);
        }
    }
    
    public void cleanup() {
        fragmentCache.clear();
        for (Fragment f : fragments) {
            if (f != null) trimFragmentCache(f);
        }
    }
    
    public void trimMemory() {
         for (Fragment fragment : fragments) {
            if (fragment != null && fragment != currentFragment) {
                trimFragmentCache(fragment);
            }
        }
        fragmentCache.clear();
    }
}
