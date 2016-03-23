package com.zyon.zeroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class ZeroidPreferences {
	private SharedPreferences mPrefs = null;
	private MenuItem mSettingsMenuItem = null;
	private Context _context = null;

	ZeroidPreferences(Context context) {
		_context = context;
		new LoadPreferencesTask().execute();
	}

	public MenuItem SettingsMenuItem() {
	    return mSettingsMenuItem;
	}

	void Resume(){
        if (mPrefs != null) {
            mPrefs.registerOnSharedPreferenceChangeListener(mSharedPreferenceListener);
        }
	}

	void Pause(){
        if (mPrefs != null) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceListener);
        }
	}

	void Create(final Menu menu){
        mSettingsMenuItem = menu.add(R.string.settings);
        mSettingsMenuItem.setIcon(android.R.drawable.ic_menu_manage);
	}

	private final OnSharedPreferenceChangeListener mSharedPreferenceListener =
            new OnSharedPreferenceChangeListener(){
        @Override
        public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
            Log.d("Pref", "onSharedPreferenceChanged");
        } // onSharedPreferenceChanged(SharedPreferences, String)
    }; // mSharedPreferencesListener

    private final class LoadPreferencesTask extends AsyncTask<Void, Void, SharedPreferences> {
        private LoadPreferencesTask() {
            super();
        } // constructor()

        @Override
        protected SharedPreferences doInBackground(final Void... noParams) {
            return PreferenceManager.getDefaultSharedPreferences(_context);
        } // doInBackground()

        @Override
        protected void onPostExecute(final SharedPreferences prefs) {
        	mPrefs = prefs;
            prefs.registerOnSharedPreferenceChangeListener(mSharedPreferenceListener);
        } // onPostExecute(SharedPreferences)
    } // class LoadPreferencesTask
}