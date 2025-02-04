package org.thoughtcrime.securesms.trustedIntroductions.receive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ContactFilterView
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme

class ManageActivity : PassphraseRequiredActivity() {

  private val tabTitles = hashMapOf<Int, String>()
  private val dynamicTheme: DynamicTheme = DynamicNoActionBarTheme()

  private lateinit var toolbar: Toolbar
  private lateinit var contactFilterView: ContactFilterView
  private lateinit var pager: ViewPager2
  private lateinit var viewModel: ManageViewModel
  private lateinit var tabLayout: TabLayout

  enum class ActiveTab {
    NEW,
    LIBRARY;

    companion object {
      @JvmStatic
      fun fromString(state: String): ActiveTab = when (state) {
        LIBRARY.toString() -> LIBRARY
        NEW.toString() -> NEW
        else -> throw AssertionError("No such screen state!")
      }

      @JvmStatic
      fun fromInt(position: Int): ActiveTab = when (position) {
        0 -> NEW
        1 -> LIBRARY
        else -> throw AssertionError("Invalid Tab position!")
      }

      @JvmStatic
      fun toInt(tab: ActiveTab): Int = when (tab) {
        NEW -> 0
        LIBRARY -> 1
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    dynamicTheme.onCreate(this)
    super.onCreate(savedInstanceState, ready)

    ManageListFragment.FORGOTTEN_INTRODUCER = getString(R.string.ManageIntroductionsListItem__Forgotten_Introducer)

    // Initialize navigation titles
    tabTitles[0] = getString(R.string.ManageIntroductionsActivity__Navigation_Tab_pending)
    tabTitles[1] = getString(R.string.ManageIntroductionsActivity__Navigation_Tab_library)
//    tabTitles[2] = getString(R.string.ManageIntroductionsActivity__Navigation_Tab_all) // todo: No idea why, to remove

    val factory = ManageViewModel.Factory(ManageListFragment.FORGOTTEN_INTRODUCER)
    viewModel = ViewModelProvider(this, factory)[ManageViewModel::class.java]
    viewModel.loadIntroductions()

    setContentView(R.layout.ti_manage_activity)

    // Bind views
    toolbar = findViewById(R.id.toolbar)
    contactFilterView = findViewById(R.id.introduction_filter_edit_text)
    pager = findViewById(R.id.pager)
    tabLayout = findViewById(R.id.tab_navigation)

    initializeToolbar()
    initializePager(
      if (intent.extras != null) {
        intent.extras
      } else {
        savedInstanceState
      }
    )

    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
  }

  private fun initializePager(savedInstanceState: Bundle?) {
    val adapter = ManagePagerAdapter(this)
    adapter.initializeViewModelOwner(this)
    pager.adapter = adapter

    contactFilterView.setHint(R.string.ManageIntroductionsActivity__Filter_hint)

    TabLayoutMediator(tabLayout, pager) { tab, position ->
      tab.text = tabTitles[position]
    }.attach()

    setActiveTab(savedInstanceState)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putInt(ACTIVE_TAB, tabLayout.selectedTabPosition)
  }

  private fun setActiveTab(savedInstanceState: Bundle?) {
    val position = savedInstanceState?.getInt(ACTIVE_TAB) ?: intent.getIntExtra(ACTIVE_TAB, 0)
    tabLayout.getTabAt(position)?.select()
  }

  private fun initializeToolbar() {
    setSupportActionBar(toolbar)
    toolbar.setTitle(R.string.ManageIntroductionsActivity__Toolbar_Title)
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(false)
      setIcon(null)
      setLogo(null)
    }
    toolbar.apply {
      setNavigationIcon(R.drawable.ic_arrow_left_24)
      setNavigationOnClickListener {
        setResult(RESULT_CANCELED)
        finish()
      }
    }
  }

  private inner class ManagePagerAdapter : FragmentStateAdapter {
    private lateinit var owner: ViewModelStoreOwner

    constructor(activity: FragmentActivity) : super(activity) {
      contactFilterView.setOnFilterChangedListener(FilterChangedListener())
    }

    constructor(fragmentManager: FragmentManager, lifecycle: Lifecycle) : super(fragmentManager, lifecycle) {
      contactFilterView.setOnFilterChangedListener(FilterChangedListener())
    }

    fun initializeViewModelOwner(owner: ViewModelStoreOwner) {
      this.owner = owner
    }

    override fun createFragment(position: Int): Fragment {
      return ManageListFragment(owner, ActiveTab.fromInt(position))
    }

    override fun getItemCount(): Int = 2

    private inner class FilterChangedListener : ContactFilterView.OnFilterChangedListener {
      override fun onFilterChanged(filter: String) {
        supportFragmentManager.fragments.forEach { fragment ->
          (fragment as? ManageListFragment)?.onFilterChanged(filter)
        }
      }
    }
  }

  companion object {
    private val TAG = "${TI_Utils.TI_LOG_TAG}${Log.tag(ManageActivity::class.java)}"
    private const val ACTIVE_TAB = "initial_active"

    @JvmStatic
    fun createIntent(context: Context, initialActive: ActiveTab): Intent =
      Intent(context, ManageActivity::class.java).apply {
        putExtra(ACTIVE_TAB, ActiveTab.toInt(initialActive))
      }
  }
}