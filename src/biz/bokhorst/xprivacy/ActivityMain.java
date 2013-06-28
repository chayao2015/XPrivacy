package biz.bokhorst.xprivacy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InterfaceAddress;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmlpull.v1.XmlSerializer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SectionIndexer;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class ActivityMain extends Activity implements OnItemSelectedListener {

	private int mThemeId;
	private Spinner spRestriction = null;
	private AppListAdapter mAppAdapter = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Set layout
		String sTheme = Restriction.getSetting(null, this, Restriction.cSettingTheme, null, false);
		mThemeId = (sTheme == null ? android.R.style.Theme_Holo_Light : Integer.parseInt(sTheme));
		setTheme(mThemeId);
		setContentView(R.layout.xmainlist);

		// Get localized restriction name
		List<String> listRestriction = Restriction.getRestrictions();
		List<String> listLocalizedRestriction = new ArrayList<String>();
		for (String restrictionName : listRestriction)
			listLocalizedRestriction.add(Restriction.getLocalizedName(this, restrictionName));

		// Build spinner adapter
		ArrayAdapter<String> spAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
		spAdapter.addAll(listLocalizedRestriction);

		// Setup search
		final EditText etFilter = (EditText) findViewById(R.id.etFilter);
		etFilter.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (mAppAdapter != null)
					mAppAdapter.getFilter().filter(etFilter.getText().toString());
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		// Setup spinner
		spRestriction = (Spinner) findViewById(R.id.spRestriction);
		spRestriction.setAdapter(spAdapter);
		spRestriction.setOnItemSelectedListener(this);

		// Handle help
		ImageView ivHelp = (ImageView) findViewById(R.id.ivHelp);
		ivHelp.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Dialog dialog = new Dialog(ActivityMain.this);
				dialog.requestWindowFeature(Window.FEATURE_LEFT_ICON);
				dialog.setTitle(getString(R.string.help_application));
				dialog.setContentView(R.layout.xhelp);
				dialog.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.ic_launcher);
				dialog.setCancelable(true);
				dialog.show();
			}
		});

		// Start task to get app list
		AppListTask appListTask = new AppListTask();
		appListTask.execute(listRestriction.get(0));

		// Check environment
		checkRequirements();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.xmain, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean pro = (Util.isProVersion(this) != null);
		boolean mounted = Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());

		menu.findItem(R.id.menu_export).setEnabled(pro && mounted);
		menu.findItem(R.id.menu_import).setEnabled(pro && mounted);
		if (Util.isProVersion(this) != null)
			menu.removeItem(R.id.menu_pro);

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		try {
			switch (item.getItemId()) {
			case R.id.menu_settings:
				optionSettings();
				return true;
			case R.id.menu_update:
				optionCheckUpdate();
				return true;
			case R.id.menu_report:
				optionReportIssue();
				return true;
			case R.id.menu_export:
				optionExport();
				return true;
			case R.id.menu_import:
				optionImport();
				return true;
			case R.id.menu_theme:
				optionSwitchTheme();
				return true;
			case R.id.menu_pro:
				optionPro();
				return true;
			case R.id.menu_about:
				optionAbout();
				return true;
			default:
				return super.onOptionsItemSelected(item);
			}
		} catch (Throwable ex) {
			Util.bug(null, ex);
			return true;
		}
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
		if (mAppAdapter != null) {
			String restrictionName = Restriction.getRestrictions().get(pos);
			mAppAdapter.setRestrictionName(restrictionName);
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {
		if (mAppAdapter != null)
			mAppAdapter.setRestrictionName(null);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mAppAdapter != null)
			mAppAdapter.notifyDataSetChanged();
	}

	private void checkRequirements() {
		// Check Android version
		if (Build.VERSION.SDK_INT != Build.VERSION_CODES.JELLY_BEAN
				&& Build.VERSION.SDK_INT != Build.VERSION_CODES.JELLY_BEAN_MR1) {
			AlertDialog alertDialog = new AlertDialog.Builder(this).create();
			alertDialog.setTitle(getString(R.string.app_name));
			alertDialog.setMessage(getString(R.string.app_wrongandroid));
			alertDialog.setIcon(R.drawable.ic_launcher);
			alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Intent xposedIntent = new Intent(Intent.ACTION_VIEW);
					xposedIntent.setData(Uri.parse("https://github.com/M66B/XPrivacy#installation"));
					startActivity(xposedIntent);
				}
			});
			alertDialog.show();
		}

		// Check Xposed version
		int xVersion = Util.getXposedVersion();
		if (xVersion < Restriction.cXposedMinVersion) {
			String msg = String.format(getString(R.string.app_notxposed), Restriction.cXposedMinVersion);
			Util.log(null, Log.WARN, msg);

			AlertDialog alertDialog = new AlertDialog.Builder(this).create();
			alertDialog.setTitle(getString(R.string.app_name));
			alertDialog.setMessage(msg);
			alertDialog.setIcon(R.drawable.ic_launcher);
			alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Intent xposedIntent = new Intent(Intent.ACTION_VIEW);
					xposedIntent.setData(Uri.parse("http://forum.xda-developers.com/showthread.php?t=1574401"));
					startActivity(xposedIntent);
				}
			});
			alertDialog.show();
		}

		// Check if XPrivacy is enabled
		if (!Util.isXposedEnabled()) {
			String msg = getString(R.string.app_notenabled);
			Util.log(null, Log.WARN, msg);

			AlertDialog alertDialog = new AlertDialog.Builder(this).create();
			alertDialog.setTitle(getString(R.string.app_name));
			alertDialog.setMessage(msg);
			alertDialog.setIcon(R.drawable.ic_launcher);
			alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Intent xInstallerIntent = getPackageManager().getLaunchIntentForPackage(
							"de.robv.android.xposed.installer");
					if (xInstallerIntent != null)
						startActivity(xInstallerIntent);
				}
			});
			alertDialog.show();
		}

		// Location manager
		// mContext is missing sometimes

		// Check package manager
		if (!checkField(getPackageManager(), "mContext", Context.class))
			reportClass(getPackageManager().getClass());

		// TODO: PackageManagerService.getPackageUid
		// Unfortunately the PMS class cannot be loaded by the app class loader

		// Check content resolver
		if (!checkField(getContentResolver(), "mContext", Context.class))
			reportClass(getContentResolver().getClass());

		// Check telephony manager
		if (!checkField(getSystemService(Context.TELEPHONY_SERVICE), "sContext", Context.class))
			reportClass(getSystemService(Context.TELEPHONY_SERVICE).getClass());

		// Check WifiInfo
		if (!checkField(WifiInfo.class, "mBSSID") || !checkField(WifiInfo.class, "mIpAddress")
				|| !checkField(WifiInfo.class, "mMacAddress")
				|| !(checkField(WifiInfo.class, "mSSID") || checkField(WifiInfo.class, "mWifiSsid")))
			reportClass(WifiInfo.class);

		// Check InterfaceAddress
		if (!checkField(InterfaceAddress.class, "address") || !checkField(InterfaceAddress.class, "broadcastAddress")
				|| XNetworkInterface.getInetAddressEmpty() == null)
			reportClass(InterfaceAddress.class);

		// Check runtime
		try {
			Runtime.class.getDeclaredMethod("load", String.class, ClassLoader.class);
			Runtime.class.getDeclaredMethod("loadLibrary", String.class, ClassLoader.class);
		} catch (NoSuchMethodException ex) {
			reportClass(Runtime.class);
		}
	}

	private boolean checkField(Object obj, String fieldName, Class<?> expectedClass) {
		try {
			// Find field
			Field field = null;
			Class<?> superClass = (obj == null ? null : obj.getClass());
			while (superClass != null)
				try {
					field = superClass.getDeclaredField(fieldName);
					field.setAccessible(true);
					break;
				} catch (Throwable ex) {
					superClass = superClass.getSuperclass();
				}

			// Check field
			if (field != null) {
				Object value = field.get(obj);
				if (value != null && expectedClass.isAssignableFrom(value.getClass()))
					return true;
			}
		} catch (Throwable ex) {
			Util.bug(null, ex);
		}
		return false;
	}

	private boolean checkField(Class<?> clazz, String fieldName) {
		try {
			clazz.getDeclaredField(fieldName);
			return true;
		} catch (Throwable ex) {
			Util.bug(null, ex);
			return false;
		}
	}

	private void optionSettings() {
		// Build dialog
		final Dialog dlgSettings = new Dialog(this);
		dlgSettings.requestWindowFeature(Window.FEATURE_LEFT_ICON);
		dlgSettings.setTitle(getString(R.string.app_name));
		dlgSettings.setContentView(R.layout.xsettings);
		dlgSettings.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.ic_launcher);

		// Reference controls
		final CheckBox cbSettings = (CheckBox) dlgSettings.findViewById(R.id.cbExpert);
		final EditText etLat = (EditText) dlgSettings.findViewById(R.id.etLat);
		final EditText etLon = (EditText) dlgSettings.findViewById(R.id.etLon);
		final EditText etMac = (EditText) dlgSettings.findViewById(R.id.etMac);
		Button btnOk = (Button) dlgSettings.findViewById(R.id.btnOk);

		// Set current values
		String sExpert = Restriction.getSetting(null, ActivityMain.this, Restriction.cSettingExpert,
				Boolean.FALSE.toString(), false);
		final boolean expert = Boolean.parseBoolean(sExpert);
		cbSettings.setChecked(expert);
		etLat.setText(Restriction.getSetting(null, ActivityMain.this, Restriction.cSettingLatitude, "", false));
		etLon.setText(Restriction.getSetting(null, ActivityMain.this, Restriction.cSettingLongitude, "", false));
		etMac.setText(Restriction.getSetting(null, ActivityMain.this, Restriction.cSettingMac,
				Restriction.getDefacedMac(), false));

		// Wait for OK
		btnOk.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				// Set expert mode
				Restriction.setSetting(null, ActivityMain.this, Restriction.cSettingExpert,
						Boolean.toString(cbSettings.isChecked()));
				if (expert != cbSettings.isChecked()) {
					// Start task to get app list
					List<String> listRestriction = Restriction.getRestrictions();
					String restrictionName = listRestriction.get(0);
					AppListTask appListTask = new AppListTask();
					appListTask.execute(restrictionName);
				}

				// Set location
				try {
					float lat = Float.parseFloat(etLat.getText().toString().replace(',', '.'));
					float lon = Float.parseFloat(etLon.getText().toString().replace(',', '.'));
					if (lat < -90 || lat > 90 || lon < -180 || lon > 180)
						throw new InvalidParameterException();

					Restriction.setSetting(null, ActivityMain.this, Restriction.cSettingLatitude, Float.toString(lat));
					Restriction.setSetting(null, ActivityMain.this, Restriction.cSettingLongitude,
							Float.toString(lon));

				} catch (Throwable ex) {
					Restriction.setSetting(null, ActivityMain.this, Restriction.cSettingLatitude, "");
					Restriction.setSetting(null, ActivityMain.this, Restriction.cSettingLongitude, "");
				}

				// Set MAC address
				Restriction.setSetting(null, ActivityMain.this, Restriction.cSettingMac, etMac.getText().toString());

				// Done
				dlgSettings.dismiss();
			}
		});

		dlgSettings.setCancelable(true);
		dlgSettings.show();
	}

	private void optionCheckUpdate() {
		new UpdateTask().execute("http://goo.im/json2&path=/devs/M66B/xprivacy");
	}

	private void optionReportIssue() {
		// Report issue
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/M66B/XPrivacy/issues"));
		startActivity(browserIntent);
	}

	private void optionPro() {
		// Redirect to pro page
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.faircode.eu/xprivacy/"));
		startActivity(browserIntent);
	}

	private void optionExport() {
		ExportTask exportTask = new ExportTask();
		exportTask.execute(getExportFile());
	}

	private void optionImport() {
		ImportTask importTask = new ImportTask();
		importTask.execute(getExportFile());
	}

	private void optionSwitchTheme() {
		String sTheme = Restriction.getSetting(null, this, Restriction.cSettingTheme, null, false);
		int themeId = (sTheme == null ? android.R.style.Theme_Holo_Light : Integer.parseInt(sTheme));
		if (themeId == android.R.style.Theme_Holo_Light)
			Restriction.setSetting(null, this, Restriction.cSettingTheme,
					Integer.toString(android.R.style.Theme_Holo));
		else
			Restriction.setSetting(null, this, Restriction.cSettingTheme,
					Integer.toString(android.R.style.Theme_Holo_Light));
		this.recreate();
	}

	private void optionAbout() {
		// About
		Dialog dlgAbout = new Dialog(this);
		dlgAbout.requestWindowFeature(Window.FEATURE_LEFT_ICON);
		dlgAbout.setTitle(getString(R.string.app_name));
		dlgAbout.setContentView(R.layout.xabout);
		dlgAbout.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.ic_launcher);

		// Show version
		try {
			PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			TextView tvVersion = (TextView) dlgAbout.findViewById(R.id.tvVersion);
			tvVersion.setText(String.format(getString(R.string.app_version), pInfo.versionName, pInfo.versionCode));
		} catch (Throwable ex) {
			Util.bug(null, ex);
		}

		// Show Xposed version
		int xVersion = Util.getXposedVersion();
		TextView tvXVersion = (TextView) dlgAbout.findViewById(R.id.tvXVersion);
		tvXVersion.setText(String.format(getString(R.string.app_xversion), xVersion));

		// Show license
		String licensed = Util.isProVersion(this);
		TextView tvLicensed = (TextView) dlgAbout.findViewById(R.id.tvLicensed);
		if (licensed == null)
			tvLicensed.setVisibility(View.GONE);
		else
			tvLicensed.setText(String.format(getString(R.string.msg_licensed), licensed));

		// Show external storage folder
		TextView tvStorage = (TextView) dlgAbout.findViewById(R.id.tvStorage);
		tvStorage.setText(Environment.getExternalStorageDirectory().toString());

		dlgAbout.setCancelable(true);
		dlgAbout.show();
	}

	private File getExportFile() {
		String folder = Environment.getExternalStorageDirectory().getAbsolutePath();
		String fileName = folder + File.separator + "XPrivacy.xml";
		return new File(fileName);
	}

	private String fetchJson(String... uri) {
		try {
			// Request downloads
			HttpClient httpclient = new DefaultHttpClient();
			HttpResponse response = httpclient.execute(new HttpGet(uri[0]));
			StatusLine statusLine = response.getStatusLine();

			if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
				// Succeeded
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				response.getEntity().writeTo(out);
				out.close();
				return out.toString("ISO-8859-1");
			} else {
				// Failed
				response.getEntity().getContent().close();
				throw new IOException(statusLine.getReasonPhrase());
			}
		} catch (Throwable ex) {
			Util.bug(null, ex);
			return ex.toString();
		}
	}

	private void processJson(String json) {
		try {
			// Parse result
			String version = null;
			String url = null;
			if (json != null)
				if (json.startsWith("{")) {
					long newest = 0;
					String prefix = "XPrivacy_";
					JSONObject jRoot = new JSONObject(json);
					JSONArray jArray = jRoot.getJSONArray("list");
					for (int i = 0; jArray != null && i < jArray.length(); i++) {
						// File
						JSONObject jEntry = jArray.getJSONObject(i);
						String filename = jEntry.getString("filename");
						if (filename.startsWith(prefix)) {
							// Check if newer
							long modified = jEntry.getLong("modified");
							if (modified > newest) {
								newest = modified;
								version = filename.substring(prefix.length()).replace(".apk", "");
								url = "http://goo.im" + jEntry.getString("path");
							}
						}
					}
				} else {
					Toast toast = Toast.makeText(ActivityMain.this, json, Toast.LENGTH_LONG);
					toast.show();
				}

			if (url == null || version == null) {
				// Assume no update
				String msg = getString(R.string.msg_noupdate);
				Toast toast = Toast.makeText(ActivityMain.this, msg, Toast.LENGTH_LONG);
				toast.show();
			} else {
				// Compare versions
				PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
				Version ourVersion = new Version(pInfo.versionName);
				Version latestVersion = new Version(version);
				if (ourVersion.compareTo(latestVersion) < 0) {
					// Update available
					Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					startActivity(browserIntent);
				} else {
					// No update available
					String msg = getString(R.string.msg_noupdate);
					Toast toast = Toast.makeText(ActivityMain.this, msg, Toast.LENGTH_LONG);
					toast.show();
				}
			}
		} catch (Throwable ex) {
			Toast toast = Toast.makeText(ActivityMain.this, ex.toString(), Toast.LENGTH_LONG);
			toast.show();
			Util.bug(null, ex);
		}
	}

	private void reportClass(final Class<?> clazz) {
		String msg = String.format("Incompatible %s", clazz.getName());
		Util.log(null, Log.WARN, msg);

		AlertDialog alertDialog = new AlertDialog.Builder(this).create();
		alertDialog.setTitle(getString(R.string.app_name));
		alertDialog.setMessage(msg);
		alertDialog.setIcon(R.drawable.ic_launcher);
		alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				sendClassInfo(clazz);
			}
		});
		alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		alertDialog.show();
	}

	private void sendClassInfo(Class<?> clazz) {
		StringBuilder sb = new StringBuilder();
		sb.append(clazz.getName());
		sb.append("\r\n");
		sb.append("\r\n");
		for (Constructor<?> constructor : clazz.getConstructors()) {
			sb.append(constructor.toString());
			sb.append("\r\n");
		}
		sb.append("\r\n");
		for (Method method : clazz.getDeclaredMethods()) {
			sb.append(method.toString());
			sb.append("\r\n");
		}
		sb.append("\r\n");
		for (Field field : clazz.getDeclaredFields()) {
			sb.append(field.toString());
			sb.append("\r\n");
		}
		sb.append("\r\n");
		sendSupportInfo(sb.toString());
	}

	private void sendSupportInfo(String text) {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("message/rfc822");
		intent.putExtra(Intent.EXTRA_EMAIL, new String[] { "marcel+xprivacy@faircode.eu" });
		intent.putExtra(Intent.EXTRA_SUBJECT, "XPrivacy support info");
		intent.putExtra(Intent.EXTRA_TEXT, text);
		try {
			startActivity(Intent.createChooser(intent, "Send mail..."));
		} catch (Throwable ex) {
			Util.bug(null, ex);
		}
	}

	private class UpdateTask extends AsyncTask<String, String, String> {

		@Override
		protected String doInBackground(String... uri) {
			return fetchJson(uri);
		}

		@Override
		protected void onPostExecute(String json) {
			super.onPostExecute(json);
			if (json != null)
				processJson(json);
		}
	}

	private class ExportTask extends AsyncTask<File, String, String> {
		private File mFile;
		private final static int NOTIFY_ID = 1;

		@Override
		protected String doInBackground(File... params) {
			mFile = params[0];
			try {
				// Serialize
				FileOutputStream fos = new FileOutputStream(mFile);
				XmlSerializer serializer = Xml.newSerializer();
				serializer.setOutput(fos, "UTF-8");
				serializer.startDocument(null, Boolean.valueOf(true));
				serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
				serializer.startTag(null, "XPrivacy");

				// Process settings
				publishProgress(getString(R.string.menu_settings));
				Cursor sCursor = getContentResolver().query(PrivacyProvider.URI_SETTING, null, null, null, null);
				while (sCursor.moveToNext()) {
					// Get setting
					String setting = sCursor.getString(sCursor.getColumnIndex(PrivacyProvider.COL_SETTING));
					String value = sCursor.getString(sCursor.getColumnIndex(PrivacyProvider.COL_VALUE));

					// Serialize setting
					serializer.startTag(null, "Setting");
					serializer.attribute(null, "Name", setting);
					serializer.attribute(null, "Value", value);
					serializer.endTag(null, "Setting");
				}
				sCursor.close();

				// Process restrictions
				Cursor rCursor = getContentResolver().query(PrivacyProvider.URI_RESTRICTION, null, null,
						new String[] { Integer.toString(0), Boolean.toString(false) }, null);
				while (rCursor.moveToNext()) {
					// Decode uid
					int uid = rCursor.getInt(rCursor.getColumnIndex(PrivacyProvider.COL_UID));
					boolean restricted = Boolean.parseBoolean(rCursor.getString(rCursor
							.getColumnIndex(PrivacyProvider.COL_RESTRICTED)));
					String[] packages = getPackageManager().getPackagesForUid(uid);
					if (packages == null)
						Util.log(null, Log.WARN, "No packages for uid=" + uid);
					else
						for (String packageName : packages) {
							publishProgress(packageName);

							// Package
							serializer.startTag(null, "Package");

							// Attribute package name
							serializer.attribute(null, "Name", packageName);

							// Attribute restriction name
							String restrictionName = rCursor.getString(rCursor
									.getColumnIndex(PrivacyProvider.COL_RESTRICTION));
							serializer.attribute(null, "Restriction", restrictionName);

							// Attribute method name
							String methodName = rCursor.getString(rCursor.getColumnIndex(PrivacyProvider.COL_METHOD));
							if (methodName != null)
								serializer.attribute(null, "Method", methodName);

							// Restricted indication
							serializer.attribute(null, "Restricted", Boolean.toString(restricted));

							serializer.endTag(null, "Package");
						}
				}
				rCursor.close();

				// End serialization
				serializer.endTag(null, "XPrivacy");
				serializer.endDocument();
				serializer.flush();
				fos.close();

				// Display message
				return getString(R.string.msg_done);
			} catch (Throwable ex) {
				Util.bug(null, ex);
				return ex.toString();
			}
		}

		@Override
		protected void onPreExecute() {
			notify(getExportFile().getAbsolutePath(), true);
			super.onPreExecute();
		}

		@Override
		protected void onProgressUpdate(String... values) {
			notify(values[0], true);
			super.onProgressUpdate(values);
		}

		@Override
		protected void onPostExecute(String result) {
			notify(result, false);
			super.onPostExecute(result);
		}

		private void notify(String text, boolean ongoing) {
			NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(ActivityMain.this);
			notificationBuilder.setSmallIcon(R.drawable.ic_launcher);
			notificationBuilder.setContentTitle(getString(R.string.menu_export));
			notificationBuilder.setContentText(text);
			notificationBuilder.setWhen(System.currentTimeMillis());
			if (ongoing)
				notificationBuilder.setOngoing(true);
			else
				notificationBuilder.setAutoCancel(true);
			Notification notification = notificationBuilder.build();

			NotificationManager notificationManager = (NotificationManager) ActivityMain.this
					.getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.notify(NOTIFY_ID, notification);
		}
	}

	private class ImportTask extends AsyncTask<File, String, String> {

		private File mFile;
		private final static int NOTIFY_ID = 2;

		@Override
		protected String doInBackground(File... params) {
			mFile = params[0];
			try {
				// Read XML
				FileInputStream fis = new FileInputStream(mFile);
				InputStreamReader isr = new InputStreamReader(fis);
				char[] inputBuffer = new char[fis.available()];
				isr.read(inputBuffer);
				String xml = new String(inputBuffer);
				isr.close();
				fis.close();

				// Prepare XML document
				InputStream is = new ByteArrayInputStream(xml.getBytes("UTF-8"));
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				Document dom = db.parse(is);
				dom.getDocumentElement().normalize();

				// Process settings
				publishProgress(getString(R.string.menu_settings));
				NodeList sItems = dom.getElementsByTagName("Setting");
				for (int i = 0; i < sItems.getLength(); i++) {
					// Process package restriction
					Node entry = sItems.item(i);
					NamedNodeMap attrs = entry.getAttributes();
					String setting = attrs.getNamedItem("Name").getNodeValue();
					String value = attrs.getNamedItem("Value").getNodeValue();
					Restriction.setSetting(null, ActivityMain.this, setting, value);
				}

				// Process restrictions
				Map<String, Map<String, List<String>>> mapPackage = new HashMap<String, Map<String, List<String>>>();
				NodeList rItems = dom.getElementsByTagName("Package");
				for (int i = 0; i < rItems.getLength(); i++) {
					// Process package restriction
					Node entry = rItems.item(i);
					NamedNodeMap attrs = entry.getAttributes();
					String packageName = attrs.getNamedItem("Name").getNodeValue();
					String restrictionName = attrs.getNamedItem("Restriction").getNodeValue();
					String methodName = (attrs.getNamedItem("Method") == null ? null : attrs.getNamedItem("Method")
							.getNodeValue());

					// Map package restriction
					if (!mapPackage.containsKey(packageName))
						mapPackage.put(packageName, new HashMap<String, List<String>>());
					if (!mapPackage.get(packageName).containsKey(restrictionName))
						mapPackage.get(packageName).put(restrictionName, new ArrayList<String>());
					if (methodName != null)
						mapPackage.get(packageName).get(restrictionName).add(methodName);
				}

				// Process result
				for (String packageName : mapPackage.keySet()) {
					try {
						publishProgress(packageName);

						// Get uid
						int uid = getPackageManager().getPackageInfo(packageName, 0).applicationInfo.uid;

						// Reset existing restrictions
						Restriction.deleteRestrictions(ActivityMain.this, uid);

						// Set imported restrictions
						for (String restrictionName : mapPackage.get(packageName).keySet()) {
							Restriction.setRestricted(null, ActivityMain.this, uid, restrictionName, null, true);
							for (String methodName : mapPackage.get(packageName).get(restrictionName))
								Restriction.setRestricted(null, ActivityMain.this, uid, restrictionName, methodName,
										false);
						}
					} catch (NameNotFoundException ex) {
						Util.log(null, Log.WARN, "Not found package=" + packageName);
					}
				}

				// Display message
				return getString(R.string.msg_done);
			} catch (Throwable ex) {
				Util.bug(null, ex);
				return ex.toString();
			}
		}

		@Override
		protected void onPreExecute() {
			notify(getExportFile().getAbsolutePath(), true);
			super.onPreExecute();
		}

		@Override
		protected void onProgressUpdate(String... values) {
			notify(values[0], true);
			super.onProgressUpdate(values);
		}

		@Override
		protected void onPostExecute(String result) {
			notify(result, false);
			super.onPostExecute(result);
		}

		private void notify(String text, boolean ongoing) {
			NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(ActivityMain.this);
			notificationBuilder.setSmallIcon(R.drawable.ic_launcher);
			notificationBuilder.setContentTitle(getString(R.string.menu_import));
			notificationBuilder.setContentText(text);
			notificationBuilder.setWhen(System.currentTimeMillis());
			if (ongoing)
				notificationBuilder.setOngoing(true);
			else
				notificationBuilder.setAutoCancel(true);
			Notification notification = notificationBuilder.build();

			NotificationManager notificationManager = (NotificationManager) ActivityMain.this
					.getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.notify(NOTIFY_ID, notification);
		}
	}

	private class AppListTask extends AsyncTask<String, Integer, List<ApplicationInfoEx>> {

		private String mRestrictionName;

		@Override
		protected List<ApplicationInfoEx> doInBackground(String... params) {
			mRestrictionName = params[0];
			return ApplicationInfoEx.getXApplicationList(ActivityMain.this);
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			// Reset spinner
			spRestriction.setSelection(0);
			spRestriction.setEnabled(false);

			// Reset filter
			EditText etFilter = (EditText) findViewById(R.id.etFilter);
			etFilter.setText("");
			etFilter.setEnabled(false);

			// Show indeterminate progress circle
			ProgressBar progressBar = (ProgressBar) findViewById(R.id.pbApp);
			progressBar.setVisibility(View.VISIBLE);
			ListView lvApp = (ListView) findViewById(R.id.lvApp);
			lvApp.setVisibility(View.GONE);
		}

		@Override
		protected void onPostExecute(List<ApplicationInfoEx> listApp) {
			super.onPostExecute(listApp);

			// Display app list
			mAppAdapter = new AppListAdapter(ActivityMain.this, R.layout.xmainentry, listApp, mRestrictionName);
			ListView lvApp = (ListView) findViewById(R.id.lvApp);
			lvApp.setAdapter(mAppAdapter);

			// Hide indeterminate progress circle
			ProgressBar progressBar = (ProgressBar) findViewById(R.id.pbApp);
			progressBar.setVisibility(View.GONE);
			lvApp.setVisibility(View.VISIBLE);

			// Enable search
			EditText etFilter = (EditText) findViewById(R.id.etFilter);
			etFilter.setEnabled(true);

			// Enable spinner
			Spinner spRestriction = (Spinner) findViewById(R.id.spRestriction);
			spRestriction.setEnabled(true);
		}
	}

	@SuppressLint("DefaultLocale")
	private class AppListAdapter extends ArrayAdapter<ApplicationInfoEx> implements SectionIndexer {

		private String mRestrictionName;
		private Map<String, Integer> alphaIndexer;
		private String[] sections;

		public AppListAdapter(Context context, int resource, List<ApplicationInfoEx> objects,
				String initialRestrictionName) {
			super(context, resource, objects);
			mRestrictionName = initialRestrictionName;
			reindexSections();
		}

		public void setRestrictionName(String restrictionName) {
			mRestrictionName = restrictionName;
			notifyDataSetChanged();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View row = inflater.inflate(R.layout.xmainentry, parent, false);
			LinearLayout llIcon = (LinearLayout) row.findViewById(R.id.llIcon);
			ImageView imgIcon = (ImageView) row.findViewById(R.id.imgIcon);
			ImageView imgInternet = (ImageView) row.findViewById(R.id.imgInternet);
			ImageView imgUsed = (ImageView) row.findViewById(R.id.imgUsed);
			final CheckedTextView ctvApp = (CheckedTextView) row.findViewById(R.id.ctvName);

			// Get entry
			final ApplicationInfoEx xAppInfo = getItem(position);

			// Set background color
			if (xAppInfo.getIsSystem())
				if (mThemeId == android.R.style.Theme_Holo_Light)
					row.setBackgroundColor(Color.parseColor("#FFFDD0"));
				else
					row.setBackgroundColor(Color.DKGRAY);

			// Click handler
			llIcon.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					Intent intentSettings = new Intent(view.getContext(), ActivityApp.class);
					intentSettings.putExtra(ActivityApp.cPackageName, xAppInfo.getPackageName());
					intentSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					view.getContext().startActivity(intentSettings);
				}
			});

			// Set icon
			imgIcon.setImageDrawable(xAppInfo.getDrawable());

			// Set title
			ctvApp.setText(xAppInfo.toString());

			// Check if internet access
			imgInternet.setVisibility(xAppInfo.hasInternet() ? View.VISIBLE : View.INVISIBLE);

			// Check if used
			boolean used = Restriction.isUsed(row.getContext(), xAppInfo.getUid(), mRestrictionName, null);
			ctvApp.setTypeface(null, used ? Typeface.BOLD_ITALIC : Typeface.NORMAL);
			imgUsed.setVisibility(used ? View.VISIBLE : View.INVISIBLE);

			// Display restriction
			boolean restricted = Restriction.getRestricted(null, row.getContext(), xAppInfo.getUid(),
					mRestrictionName, null, false, false);
			ctvApp.setChecked(restricted);

			// Listen for restriction changes
			ctvApp.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					boolean restricted = Restriction.getRestricted(null, view.getContext(), xAppInfo.getUid(),
							mRestrictionName, null, false, false);
					restricted = !restricted;
					ctvApp.setChecked(restricted);
					Restriction.setRestricted(null, view.getContext(), xAppInfo.getUid(), mRestrictionName, null,
							restricted);
				}
			});

			row.refreshDrawableState();
			return row;
		}

		@Override
		public int getPositionForSection(int section) {
			if (section >= sections.length)
				return super.getCount() - 1;

			return alphaIndexer.get(sections[section]);
		}

		@Override
		public int getSectionForPosition(int position) {
			// Iterate over the sections to find the closest index
			// that is not greater than the position
			int closestIndex = 0;
			int latestDelta = Integer.MAX_VALUE;

			for (int i = 0; i < sections.length; i++) {
				int current = alphaIndexer.get(sections[i]);
				if (current == position) {
					// If position matches an index, return it immediately
					return i;
				} else if (current < position) {
					// Check if this is closer than the last index we inspected
					int delta = position - current;
					if (delta < latestDelta) {
						closestIndex = i;
						latestDelta = delta;
					}
				}
			}

			return closestIndex;
		}

		@Override
		public Object[] getSections() {
			return sections;
		}

		@Override
		public void notifyDataSetChanged() {
			super.notifyDataSetChanged();
			reindexSections();
		}

		private void reindexSections() {
			alphaIndexer = new HashMap<String, Integer>();
			for (int i = getCount() - 1; i >= 0; i--) {
				ApplicationInfoEx app = getItem(i);
				String appName = app.toString();
				String firstChar;
				if (appName == null || appName.length() < 1) {
					firstChar = "@";
				} else {
					firstChar = appName.substring(0, 1).toUpperCase();
					if (firstChar.charAt(0) > 'Z' || firstChar.charAt(0) < 'A')
						firstChar = "@";
				}

				alphaIndexer.put(firstChar, i);
			}

			// create a list from the set to sort
			List<String> sectionList = new ArrayList<String>(alphaIndexer.keySet());
			Collections.sort(sectionList);

			sections = new String[sectionList.size()];
			sectionList.toArray(sections);
		}
	}
}
