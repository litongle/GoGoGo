package com.zcshou.gogogo;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Poi;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeAddress;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.amap.api.services.help.Inputtips;
import com.amap.api.services.help.InputtipsQuery;
import com.amap.api.services.help.Tip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.zcshou.service.ServiceGo;
import com.zcshou.database.DataBaseHistoryLocation;
import com.zcshou.database.DataBaseHistorySearch;
import com.zcshou.utils.ShareUtils;
import com.zcshou.utils.GoUtils;
import com.zcshou.utils.MapUtils;

import com.elvishew.xlog.XLog;

import io.noties.markwon.Markwon;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends BaseActivity implements SensorEventListener {
    /* 对外 */
    public static final String LAT_MSG_ID = "LAT_VALUE";
    public static final String LNG_MSG_ID = "LNG_VALUE";
    public static final String ALT_MSG_ID = "ALT_VALUE";

    public static final String POI_NAME = "POI_NAME";
    public static final String POI_ADDRESS = "POI_ADDRESS";
    public static final String POI_LONGITUDE = "POI_LONGITUDE";
    public static final String POI_LATITUDE = "POI_LATITUDE";

    private OkHttpClient mOkHttpClient;
    private SharedPreferences sharedPreferences;

    /*============================== 主界面地图 相关 ==============================*/
    /************** 地图 *****************/
    private static final float DEFAULT_MAP_ZOOM = 18.0f;
    public static BitmapDescriptor mMapIndicator;
    public static String mCurrentCity = null;
    private MapView mMapView;
    private static AMap mAMap = null;
    private static Marker mMarkMarker = null;
    private Marker mCurrentLocationMarker;
    private static LatLng mMarkLatLngMap = null; // 当前标记的地图点
    private static String mMarkName = null;
    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetic;
    private float[] mAccValues = new float[3];//加速度传感器数据
    private float[] mMagValues = new float[3];//地磁传感器数据
    private final float[] mR = new float[9];//旋转矩阵，用来保存磁场和加速度的数据
    private final float[] mDirectionValues = new float[3];//模拟方向传感器的数据（原始数据为弧度）
    /************** 定位 *****************/
    private AMapLocationClient mLocClient = null;
    private double mCurrentLat = 0.0;       // 当前位置的高德纬度
    private double mCurrentLon = 0.0;       // 当前位置的高德经度
    private float mCurrentDirection = 0.0f;
    private boolean isFirstLoc = true; // 是否首次定位
    private boolean isMockServStart = false;
    private ServiceGo.ServiceGoBinder mServiceBinder;
    private ServiceConnection mConnection;
    private FloatingActionButton mButtonStart;
    /*============================== 历史记录 相关 ==============================*/
    private SQLiteDatabase mLocationHistoryDB;
    private SQLiteDatabase mSearchHistoryDB;
    /*============================== SearchView 相关 ==============================*/
    private SearchView searchView;
    private ListView mSearchList;
    private LinearLayout mSearchLayout;
    private ListView mSearchHistoryList;
    private LinearLayout mHistoryLayout;
    private MenuItem searchItem;
    /*============================== 更新 相关 ==============================*/
    private DownloadManager mDownloadManager = null;
    private long mDownloadId;
    private BroadcastReceiver mDownloadBdRcv;
    private String mUpdateFilename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.nav_drawer_open, R.string.nav_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        XLog.i("MainActivity: onCreate");

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mOkHttpClient = new OkHttpClient();

        initNavigationView();

        initMap(savedInstanceState);

        initMapLocation();

        initMapButton();

        initGoBtn();

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceBinder = (ServiceGo.ServiceGoBinder)service;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

        initStoreHistory();

        initSearchView();

        initUpdateVersion();

        checkUpdateVersion(false);
    }

    @Override
    protected void onPause() {
        XLog.i("MainActivity: onPause");
        if (mMapView != null) {
            mMapView.onPause();
        }
        mSensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        XLog.i("MainActivity: onResume");
        if (mMapView != null) {
            mMapView.onResume();
        }
        mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
        super.onResume();
    }

    @Override
    protected void onStop() {
        XLog.i("MainActivity: onStop");
        //取消注册传感器监听
        mSensorManager.unregisterListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        XLog.i("MainActivity: onDestroy");

        if (isMockServStart) {
            unbindService(mConnection); // 解绑服务，服务要记得解绑，不要造成内存泄漏
            Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
            stopService(serviceGoIntent);
        }
        unregisterReceiver(mDownloadBdRcv);

        mSensorManager.unregisterListener(this);

        // 退出时销毁定位
        if (mLocClient != null) {
            mLocClient.stopLocation();
            mLocClient.onDestroy();
        }
        if (mCurrentLocationMarker != null) {
            mCurrentLocationMarker.remove();
            mCurrentLocationMarker = null;
        }
        if (mMarkMarker != null) {
            mMarkMarker.remove();
            mMarkMarker = null;
        }
        if (mMapView != null) {
            mMapView.onDestroy();
        }

        //close db
        mLocationHistoryDB.close();
        mSearchHistoryDB.close();

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mMapView != null) {
            mMapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(false);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        //找到searchView
        searchItem = menu.findItem(R.id.action_search);
        searchItem.setOnActionExpandListener(new  MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                mHistoryLayout.setVisibility(View.INVISIBLE);
                return true;  // Return true to collapse action view
            }
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                //展示搜索历史
                List<Map<String, Object>> data = getSearchHistory();

                if (!data.isEmpty()) {
                    SimpleAdapter simAdapt = new SimpleAdapter(
                            MainActivity.this,
                            data,
                            R.layout.search_item,
                            new String[] {DataBaseHistorySearch.DB_COLUMN_KEY,
                                    DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                                    DataBaseHistorySearch.DB_COLUMN_TIMESTAMP,
                                    DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                                    DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM,
                                    DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM,
                                    DataBaseHistorySearch.DB_COLUMN_CUSTOM_COORD_TYPE},
                            new int[] {R.id.search_key,
                                    R.id.search_description,
                                    R.id.search_timestamp,
                                    R.id.search_isLoc,
                                    R.id.search_longitude,
                                    R.id.search_latitude,
                                    R.id.search_coord_type});
                    mSearchHistoryList.setAdapter(simAdapt);
                    mHistoryLayout.setVisibility(View.VISIBLE);
                }

                return true;  // Return true to expand action view
            }
        });

        searchView = (SearchView) searchItem.getActionView();
        searchView.setIconified(false);// 设置searchView处于展开状态
        searchView.onActionViewExpanded();// 当展开无输入内容的时候，没有关闭的图标
        searchView.setIconifiedByDefault(true);//默认为true在框内，设置false则在框外
        searchView.setSubmitButtonEnabled(false);//显示提交按钮
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                try {
                    requestInputTips(query);
                    //搜索历史 插表参数
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, query);
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, "搜索关键字");
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_KEY);
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

                    DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
                    mSearchLayout.setVisibility(View.INVISIBLE);
                } catch (Exception e) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_search));
                    XLog.d(getResources().getString(R.string.app_error_search));
                }

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //当输入框内容改变的时候回调
                //搜索历史置为不可见
                mHistoryLayout.setVisibility(View.INVISIBLE);

                if (newText != null && !newText.isEmpty()) {
                    try {
                        requestInputTips(newText);
                    } catch (Exception e) {
                        GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_search));
                        XLog.d(getResources().getString(R.string.app_error_search));
                    }
                } else {
                    mSearchLayout.setVisibility(View.INVISIBLE);
                }

                return true;
            }
        });

        // 搜索框的清除按钮(该按钮属于安卓系统图标)
        ImageView closeButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        closeButton.setOnClickListener(v -> {
            EditText et = findViewById(androidx.appcompat.R.id.search_src_text);
            et.setText("");
            searchView.setQuery("", false);
            mSearchLayout.setVisibility(View.INVISIBLE);
            mHistoryLayout.setVisibility(View.VISIBLE);
        });

        return true;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            mAccValues = sensorEvent.values;
        }
        else if(sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            mMagValues = sensorEvent.values;
        }

        SensorManager.getRotationMatrix(mR, null, mAccValues, mMagValues);
        SensorManager.getOrientation(mR, mDirectionValues);
        mCurrentDirection = (float) Math.toDegrees(mDirectionValues[0]);    // 弧度转角度
        if (mCurrentDirection < 0) {    // 由 -180 ~ + 180 转为 0 ~ 360
            mCurrentDirection += 360;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    /*============================== NavigationView 相关 ==============================*/
    private void initNavigationView() {
        /*============================== NavigationView 相关 ==============================*/
        NavigationView mNavigationView = findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_history) {
                Intent intent = new Intent(MainActivity.this, HistoryActivity.class);

                startActivity(intent);
            } else if (id == R.id.nav_settings) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_dev) {
                if (!GoUtils.isDeveloperOptionsEnabled(this)) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_dev));
                } else {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                        startActivity(intent);
                    } catch (Exception e) {
                        GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_dev));
                    }
                }
            } else if (id == R.id.nav_update) {
                checkUpdateVersion(true);
            } else if (id == R.id.nav_feedback) {
                File file = new File(getExternalFilesDir("Logs"), GoApplication.LOG_FILE_NAME);
                ShareUtils.shareFile(this, file, item.getTitle().toString());
            } else if (id == R.id.nav_contact) {
                Uri uri = Uri.parse("https://github.com/litongle/GoGoGo/issues");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }

            DrawerLayout drawer = findViewById(R.id.drawer_layout);
            drawer.closeDrawer(GravityCompat.START);

            return true;
        });

        // 直接获取第 0 个头部视图
        View headerView = mNavigationView.getHeaderView(0);
        TextView app_version = headerView.findViewById(R.id.app_version);
        app_version.setText(GoUtils.getVersionName(this));
    }

    /*============================== 主界面地图 相关 ==============================*/
    private void initMap(Bundle savedInstanceState) {
        mMapView = findViewById(R.id.mapView);
        mMapView.onCreate(savedInstanceState);
        mAMap = mMapView.getMap();
        mAMap.getUiSettings().setZoomControlsEnabled(false);
        mAMap.setMapType(AMap.MAP_TYPE_NORMAL);
        if (mMapIndicator == null) {
            mMapIndicator = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);
        }

        mAMap.setOnMapClickListener(point -> {
            mMarkLatLngMap = point;
            markMap();
        });
        mAMap.setOnMapLongClickListener(point -> {
            mMarkLatLngMap = point;
            markMap();
            reverseGeocode(point, new ReverseGeocodeCallback() {
                @Override
                public void onSuccess(String address) {
                    mMarkName = address;
                    showPoiDialog(point, address);
                }

                @Override
                public void onFailure() {
                    showPoiDialog(point, getResources().getString(R.string.history_location_default_name));
                }
            });
        });
        mAMap.setOnPOIClickListener((Poi poi) -> {
            mMarkName = poi.getName();
            mMarkLatLngMap = poi.getCoordinate();
            markMap();
        });

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);// 获取传感器管理服务
        if (mSensorManager != null) {
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (mSensorAccelerometer != null) {
                mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
            }
            mSensorMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (mSensorMagnetic != null) {
                mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    //开启地图的定位图层
    private void initMapLocation() {
        try {
            mLocClient = new AMapLocationClient(this);
            mLocClient.setLocationOption(getLocationClientOption());
            mLocClient.setLocationListener(location -> {
                if (location == null || mMapView == null) {
                    return;
                }

                if (location.getErrorCode() != 0) {
                    XLog.e("AMap ERROR: " + location.getErrorCode() + "-" + location.getErrorInfo());
                    return;
                }

                mCurrentCity = location.getCity();
                mCurrentLat = location.getLatitude();
                mCurrentLon = location.getLongitude();
                updateCurrentLocationMarker();

                if (isFirstLoc) {
                    isFirstLoc = false;
                    moveMapTo(new LatLng(mCurrentLat, mCurrentLon));
                    XLog.i("First AMap LatLng: " + mCurrentLat + "," + mCurrentLon);
                }
            });
            mLocClient.startLocation();
        } catch (Exception e) {
            XLog.e("ERROR: initMapLocation");
        }
    }

    @NonNull
    private static AMapLocationClientOption getLocationClientOption() {
        AMapLocationClientOption locationOption = new AMapLocationClientOption();
        locationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        locationOption.setNeedAddress(true);
        locationOption.setInterval(1000);
        locationOption.setMockEnable(true);
        locationOption.setGpsFirst(true);
        locationOption.setLocationCacheEnable(false);
        return locationOption;
    }

    private void updateCurrentLocationMarker() {
        if (mAMap == null) {
            return;
        }

        LatLng currentLatLng = new LatLng(mCurrentLat, mCurrentLon);
        if (mCurrentLocationMarker == null) {
            mCurrentLocationMarker = mAMap.addMarker(new MarkerOptions().position(currentLatLng));
        } else {
            mCurrentLocationMarker.setPosition(currentLatLng);
        }
    }

    private static void moveMapTo(LatLng latLng) {
        if (mAMap != null && latLng != null) {
            mAMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_MAP_ZOOM));
        }
    }

    private void showPoiDialog(LatLng latLng, String address) {
        View poiView = View.inflate(MainActivity.this, R.layout.location_poi_info, null);
        TextView poiAddress = poiView.findViewById(R.id.poi_address);
        TextView poiLongitude = poiView.findViewById(R.id.poi_longitude);
        TextView poiLatitude = poiView.findViewById(R.id.poi_latitude);
        poiAddress.setText(address);
        poiLongitude.setText(String.valueOf(latLng.longitude));
        poiLatitude.setText(String.valueOf(latLng.latitude));

        AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                .setView(poiView)
                .create();

        ImageButton ibSave = poiView.findViewById(R.id.poi_save);
        ibSave.setOnClickListener(v -> {
            recordCurrentLocation(latLng.longitude, latLng.latitude);
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_location_save));
            dialog.dismiss();
        });
        ImageButton ibCopy = poiView.findViewById(R.id.poi_copy);
        ibCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clipData = ClipData.newPlainText("Label", latLng.longitude + "," + latLng.latitude);
            cm.setPrimaryClip(clipData);
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_location_copy));
        });
        ImageButton ibShare = poiView.findViewById(R.id.poi_share);
        ibShare.setOnClickListener(v -> ShareUtils.shareText(MainActivity.this, "分享位置", poiLongitude.getText() + "," + poiLatitude.getText()));
        ImageButton ibFly = poiView.findViewById(R.id.poi_fly);
        ibFly.setOnClickListener(v -> {
            mMarkLatLngMap = latLng;
            mMarkName = address;
            markMap();
            dialog.dismiss();
            doGoLocation(v);
        });

        dialog.show();
    }

    private void reverseGeocode(LatLng latLng, ReverseGeocodeCallback callback) {
        try {
            GeocodeSearch geocodeSearch = new GeocodeSearch(this);
            geocodeSearch.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
                @Override
                public void onRegeocodeSearched(RegeocodeResult result, int rCode) {
                    if (rCode == AMapException.CODE_AMAP_SUCCESS && result != null) {
                        RegeocodeAddress regeocodeAddress = result.getRegeocodeAddress();
                        if (regeocodeAddress != null && !TextUtils.isEmpty(regeocodeAddress.getFormatAddress())) {
                            callback.onSuccess(regeocodeAddress.getFormatAddress());
                            return;
                        }
                    }
                    callback.onFailure();
                }

                @Override
                public void onGeocodeSearched(GeocodeResult geocodeResult, int rCode) {
                    XLog.d("Ignore forward geocode callback: " + rCode);
                }
            });
            RegeocodeQuery query = new RegeocodeQuery(new LatLonPoint(latLng.latitude, latLng.longitude), 200, GeocodeSearch.AMAP);
            geocodeSearch.getFromLocationAsyn(query);
        } catch (Exception e) {
            callback.onFailure();
        }
    }

    private interface ReverseGeocodeCallback {
        void onSuccess(String address);

        void onFailure();
    }

    //地图上各按键的监听
    private void initMapButton() {
        RadioGroup mGroupMapType = this.findViewById(R.id.RadioGroupMapType);
        mGroupMapType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.mapNormal) {
                mAMap.setMapType(AMap.MAP_TYPE_NORMAL);
            }

            if (checkedId == R.id.mapSatellite) {
                mAMap.setMapType(AMap.MAP_TYPE_SATELLITE);
            }
        });

        ImageButton curPosBtn = this.findViewById(R.id.cur_position);
        curPosBtn.setOnClickListener(v -> resetMap());

        ImageButton zoomInBtn = this.findViewById(R.id.zoom_in);
        zoomInBtn.setOnClickListener(v -> mAMap.animateCamera(CameraUpdateFactory.zoomIn()));

        ImageButton zoomOutBtn = this.findViewById(R.id.zoom_out);
        zoomOutBtn.setOnClickListener(v -> mAMap.animateCamera(CameraUpdateFactory.zoomOut()));

        ImageButton inputPosBtn = this.findViewById(R.id.input_pos);
        inputPosBtn.setOnClickListener(v -> {
            AlertDialog dialog;
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("请输入经度和纬度");
            View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.location_input, null);
            builder.setView(view);
            dialog = builder.show();

            EditText dialog_lng = view.findViewById(R.id.joystick_longitude);
            EditText dialog_lat = view.findViewById(R.id.joystick_latitude);
            RadioButton rbGcj02 = view.findViewById(R.id.pos_type_gcj02);

            Button btnGo = view.findViewById(R.id.input_position_ok);
            btnGo.setOnClickListener(v2 -> {
                String dialog_lng_str = dialog_lng.getText().toString();
                String dialog_lat_str = dialog_lat.getText().toString();

                if (TextUtils.isEmpty(dialog_lng_str) || TextUtils.isEmpty(dialog_lat_str)) {
                    GoUtils.DisplayToast(MainActivity.this,getResources().getString(R.string.app_error_input));
                } else {
                    double dialog_lng_double = Double.parseDouble(dialog_lng_str);
                    double dialog_lat_double = Double.parseDouble(dialog_lat_str);

                    if (dialog_lng_double > 180.0 || dialog_lng_double < -180.0) {
                        GoUtils.DisplayToast(MainActivity.this,  getResources().getString(R.string.app_error_longitude));
                    } else {
                        if (dialog_lat_double > 90.0 || dialog_lat_double < -90.0) {
                            GoUtils.DisplayToast(MainActivity.this,  getResources().getString(R.string.app_error_latitude));
                        } else {
                            if (rbGcj02.isChecked()) {
                                mMarkLatLngMap = new LatLng(dialog_lat_double, dialog_lng_double);
                            } else {
                                double[] gcjLonLat = MapUtils.wgs84ToGcj02(dialog_lng_double, dialog_lat_double);
                                mMarkLatLngMap = new LatLng(gcjLonLat[1], gcjLonLat[0]);
                            }
                            mMarkName = "手动输入的坐标";

                            markMap();
                            moveMapTo(mMarkLatLngMap);

                            dialog.dismiss();
                        }
                    }
                }
            });

            Button btnCancel = view.findViewById(R.id.input_position_cancel);
            btnCancel.setOnClickListener(v1 -> dialog.dismiss());
        });
    }

    //标定选择的位置
    private void markMap() {
        updateMarkMarker();
    }

    private void resetMap() {
        mMarkLatLngMap = null;
        if (mMarkMarker != null) {
            mMarkMarker.remove();
            mMarkMarker = null;
        }
        if (mCurrentLat != 0.0 || mCurrentLon != 0.0) {
            moveMapTo(new LatLng(mCurrentLat, mCurrentLon));
        }
    }

    // 在地图上显示位置
    public static boolean showLocation(String name, String customLongitude, String customLatitude) {
        return showLocation(name, customLongitude, customLatitude, MapUtils.COORD_TYPE_BD09);
    }

    public static boolean showLocation(String name, String customLongitude, String customLatitude, String coordType) {
        boolean ret = true;

        try {
            if (!customLongitude.isEmpty() && !customLatitude.isEmpty()) {
                double[] gcjLonLat = MapUtils.toGcj02(Double.parseDouble(customLongitude), Double.parseDouble(customLatitude), coordType);
                mMarkName = name;
                mMarkLatLngMap = new LatLng(gcjLonLat[1], gcjLonLat[0]);
                updateMarkMarker();
                moveMapTo(mMarkLatLngMap);
            }
        } catch (Exception e) {
            ret = false;
            XLog.e("ERROR: showHistoryLocation");
        }

        return ret;
    }

    private void initGoBtn() {
        mButtonStart = findViewById(R.id.faBtnStart);
        mButtonStart.setOnClickListener(this::doGoLocation);
    }

    private void showSnackMessage(String message) {
        View anchor = findViewById(android.R.id.content);
        if (anchor == null) {
            anchor = mButtonStart;
        }
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .show();
    }

    private void startGoLocation() {
        Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
        bindService(serviceGoIntent, mConnection, BIND_AUTO_CREATE);    // 绑定服务和活动，之后活动就可以去调服务的方法了
        double[] latLng = MapUtils.gcj02ToWgs84(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
        serviceGoIntent.putExtra(LNG_MSG_ID, latLng[0]);
        serviceGoIntent.putExtra(LAT_MSG_ID, latLng[1]);
        double alt = Double.parseDouble(sharedPreferences.getString("setting_altitude", "55.0"));
        serviceGoIntent.putExtra(ALT_MSG_ID, alt);

        startForegroundService(serviceGoIntent);
        XLog.d("startForegroundService: ServiceGo");

        isMockServStart = true;
    }

    private void stopGoLocation() {
        unbindService(mConnection); // 解绑服务，服务要记得解绑，不要造成内存泄漏
        Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
        stopService(serviceGoIntent);
        isMockServStart = false;
    }

    private void doGoLocation(View v) {
        if (!GoUtils.isNetworkAvailable(this)) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_network));
            return;
        }

        if (!GoUtils.isGpsOpened(this)) {
            GoUtils.showEnableGpsDialog(this);
            return;
        }

        if (!Settings.canDrawOverlays(getApplicationContext())) {//悬浮窗权限判断
            GoUtils.showEnableFloatWindowDialog(this);
            XLog.e("无悬浮窗权限!");
            return;
        }

        if (isMockServStart) {
            if (mMarkLatLngMap == null) {
                stopGoLocation();
                showSnackMessage("模拟位置已终止");
                mButtonStart.setImageResource(R.drawable.ic_position);
            } else {
                double[] latLng = MapUtils.gcj02ToWgs84(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
                double alt = Double.parseDouble(sharedPreferences.getString("setting_altitude", "55.0"));
                mServiceBinder.setPosition(latLng[0], latLng[1], alt);
                showSnackMessage("已传送到新位置");

                recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

                resetMap();

                if (GoUtils.isWifiEnabled(MainActivity.this)) {
                    GoUtils.showDisableWifiDialog(MainActivity.this);
                }
            }
        } else {
            if (!GoUtils.isAllowMockLocation(this)) {
                GoUtils.showEnableMockLocationDialog(this);
                XLog.e("无模拟位置权限!");
            } else {
                if (mMarkLatLngMap == null) {
                    showSnackMessage("请先点击地图位置或者搜索位置");
                } else {
                    startGoLocation();
                    mButtonStart.setImageResource(R.drawable.ic_fly);
                    showSnackMessage("模拟位置已启动");

                    recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
                    resetMap();

                    if (GoUtils.isWifiEnabled(MainActivity.this)) {
                        GoUtils.showDisableWifiDialog(MainActivity.this);
                    }
                }
            }
        }
    }

    private static void updateMarkMarker() {
        if (mAMap == null || mMarkLatLngMap == null) {
            return;
        }

        MarkerOptions markerOptions = new MarkerOptions().position(mMarkLatLngMap);
        if (mMapIndicator != null) {
            markerOptions.icon(mMapIndicator);
        }
        if (mMarkMarker == null) {
            mMarkMarker = mAMap.addMarker(markerOptions);
        } else {
            mMarkMarker.setPosition(mMarkLatLngMap);
            if (mMapIndicator != null) {
                mMarkMarker.setIcon(mMapIndicator);
            }
        }
    }

    /*============================== 历史记录 相关 ==============================*/
    private void initStoreHistory() {
        try {
            // 定位历史
            DataBaseHistoryLocation dbLocation = new DataBaseHistoryLocation(getApplicationContext());
            mLocationHistoryDB = dbLocation.getWritableDatabase();
            // 搜索历史
            DataBaseHistorySearch dbHistory = new DataBaseHistorySearch(getApplicationContext());
            mSearchHistoryDB = dbHistory.getWritableDatabase();
        } catch (Exception e) {
            XLog.e("ERROR: sqlite init error");
        }
    }

    //获取查询历史
    private List<Map<String, Object>> getSearchHistory() {
        List<Map<String, Object>> data = new ArrayList<>();

        try {
            Cursor cursor = mSearchHistoryDB.query(DataBaseHistorySearch.TABLE_NAME, null,
                    DataBaseHistorySearch.DB_COLUMN_ID + " > ?", new String[] {"0"},
                    null, null, DataBaseHistorySearch.DB_COLUMN_TIMESTAMP + " DESC", null);

            int keyColumn = cursor.getColumnIndexOrThrow(DataBaseHistorySearch.DB_COLUMN_KEY);
            int descriptionColumn = cursor.getColumnIndexOrThrow(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION);
            int timestampColumn = cursor.getColumnIndexOrThrow(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP);
            int isLocationColumn = cursor.getColumnIndexOrThrow(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION);
            int longitudeColumn = cursor.getColumnIndexOrThrow(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM);
            int latitudeColumn = cursor.getColumnIndexOrThrow(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM);
            int coordTypeColumn = cursor.getColumnIndexOrThrow(DataBaseHistorySearch.DB_COLUMN_CUSTOM_COORD_TYPE);
            while (cursor.moveToNext()) {
                Map<String, Object> searchHistoryItem = new HashMap<>();
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_KEY, cursor.getString(keyColumn));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, cursor.getString(descriptionColumn));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, "" + cursor.getInt(timestampColumn));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, "" + cursor.getInt(isLocationColumn));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, cursor.getString(longitudeColumn));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, cursor.getString(latitudeColumn));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_CUSTOM_COORD_TYPE, cursor.getString(coordTypeColumn));
                data.add(searchHistoryItem);
            }
            cursor.close();
        } catch (Exception e) {
            XLog.e("ERROR: getSearchHistory");
        }

        return data;
    }

    // 记录请求的位置信息
    private void recordCurrentLocation(double lng, double lat) {
        double[] wgs84LatLng = MapUtils.gcj02ToWgs84(lng, lat);
        reverseGeocode(new LatLng(lat, lng), new ReverseGeocodeCallback() {
            @Override
            public void onSuccess(String address) {
                saveHistoryLocation(address, lng, lat, wgs84LatLng);
            }

            @Override
            public void onFailure() {
                saveHistoryLocation(mMarkName, lng, lat, wgs84LatLng);
            }
        });
    }

    private void saveHistoryLocation(String location, double lng, double lat, double[] wgs84LatLng) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION,
                TextUtils.isEmpty(location) ? getResources().getString(R.string.history_location_default_name) : location);
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(wgs84LatLng[0]));
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(wgs84LatLng[1]));
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(lng));
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, Double.toString(lat));
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_CUSTOM_COORD_TYPE, MapUtils.COORD_TYPE_GCJ02);
        DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, contentValues);
    }

    /*============================== SearchView 相关 ==============================*/
    private void initSearchView() {
        mSearchLayout = findViewById(R.id.search_linear);
        mHistoryLayout = findViewById(R.id.search_history_linear);

        mSearchList = findViewById(R.id.search_list_view);
        mSearchList.setOnItemClickListener((parent, view, position, id) -> {
            String lng = ((TextView) view.findViewById(R.id.poi_longitude)).getText().toString();
            String lat = ((TextView) view.findViewById(R.id.poi_latitude)).getText().toString();
            mMarkName = ((TextView) view.findViewById(R.id.poi_name)).getText().toString();
            mMarkLatLngMap = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
            markMap();
            moveMapTo(mMarkLatLngMap);
            double[] latLng = MapUtils.gcj02ToWgs84(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

            // mSearchList.setVisibility(View.GONE);
            //搜索历史 插表参数
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, mMarkName);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, ((TextView) view.findViewById(R.id.poi_address)).getText().toString());
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_CUSTOM_COORD_TYPE, MapUtils.COORD_TYPE_GCJ02);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

            DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            mSearchLayout.setVisibility(View.INVISIBLE);
            searchItem.collapseActionView();
        });
        //搜索历史列表的点击监听
        mSearchHistoryList = findViewById(R.id.search_history_list_view);
        mSearchHistoryList.setOnItemClickListener((parent, view, position, id) -> {
            String searchDescription = ((TextView) view.findViewById(R.id.search_description)).getText().toString();
            String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();
            String searchIsLoc = ((TextView) view.findViewById(R.id.search_isLoc)).getText().toString();

            //如果是定位搜索
            if (searchIsLoc.equals("1")) {
                String lng = ((TextView) view.findViewById(R.id.search_longitude)).getText().toString();
                String lat = ((TextView) view.findViewById(R.id.search_latitude)).getText().toString();
                String coordType = ((TextView) view.findViewById(R.id.search_coord_type)).getText().toString();
                double[] gcjLonLat = MapUtils.toGcj02(Double.parseDouble(lng), Double.parseDouble(lat), coordType);
                mMarkLatLngMap = new LatLng(gcjLonLat[1], gcjLonLat[0]);

                markMap();
                moveMapTo(mMarkLatLngMap);
                double[] latLng = MapUtils.gcj02ToWgs84(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

                //设置列表不可见
                mHistoryLayout.setVisibility(View.INVISIBLE);
                searchItem.collapseActionView();
                //更新表
                ContentValues contentValues = new ContentValues();
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, searchKey);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, searchDescription);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, String.valueOf(mMarkLatLngMap.longitude));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, String.valueOf(mMarkLatLngMap.latitude));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_CUSTOM_COORD_TYPE, MapUtils.COORD_TYPE_GCJ02);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

                DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            } else if (searchIsLoc.equals("0")) { //如果仅仅是搜索
                try {
                    searchView.setQuery(searchKey, true);
                } catch (Exception e) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_search));
                    XLog.e(getResources().getString(R.string.app_error_search));
                }
            } else {
                XLog.e(getResources().getString(R.string.app_error_param));
            }
        });
        mSearchHistoryList.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("警告")//这里是表头的内容
                    .setMessage("确定要删除该项搜索记录吗?")//这里是中间显示的具体信息
                    .setPositiveButton("确定",(dialog, which) -> {
                        String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();

                        try {
                            mSearchHistoryDB.delete(DataBaseHistorySearch.TABLE_NAME, DataBaseHistorySearch.DB_COLUMN_KEY + " = ?", new String[] {searchKey});
                            //删除成功
                            //展示搜索历史
                            List<Map<String, Object>> data = getSearchHistory();

                            if (!data.isEmpty()) {
                                SimpleAdapter simAdapt = new SimpleAdapter(
                                        MainActivity.this,
                                        data,
                                        R.layout.search_item,
                                        new String[] {DataBaseHistorySearch.DB_COLUMN_KEY,
                                                DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                                                DataBaseHistorySearch.DB_COLUMN_TIMESTAMP,
                                                DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                                                DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM,
                                                DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM,
                                                DataBaseHistorySearch.DB_COLUMN_CUSTOM_COORD_TYPE}, // 与下面数组元素要一一对应
                                        new int[] {R.id.search_key, R.id.search_description, R.id.search_timestamp, R.id.search_isLoc, R.id.search_longitude, R.id.search_latitude, R.id.search_coord_type});
                                mSearchHistoryList.setAdapter(simAdapt);
                                mHistoryLayout.setVisibility(View.VISIBLE);
                            }
                        } catch (Exception e) {
                            XLog.e("ERROR: delete database error");
                            GoUtils.DisplayToast(MainActivity.this,getResources().getString(R.string.history_delete_error));
                        }
                    })
                    .setNegativeButton("取消",
                            (dialog, which) -> {
                            })
                    .show();
            return true;
        });
    }

    private void requestInputTips(String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            mSearchLayout.setVisibility(View.INVISIBLE);
            return;
        }

        try {
            InputtipsQuery query = new InputtipsQuery(keyword, TextUtils.isEmpty(mCurrentCity) ? "" : mCurrentCity);
            query.setCityLimit(false);
            Inputtips inputtips = new Inputtips(this, query);
            inputtips.setInputtipsListener((tips, rCode) -> {
                if (rCode != AMapException.CODE_AMAP_SUCCESS || tips == null) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_search_null));
                    return;
                }

                List<Map<String, Object>> data = getMapList(tips);
                if (data.isEmpty()) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_search_null));
                    mSearchLayout.setVisibility(View.INVISIBLE);
                    return;
                }

                SimpleAdapter simAdapt = new SimpleAdapter(
                        MainActivity.this,
                        data,
                        R.layout.search_poi_item,
                        new String[]{POI_NAME, POI_ADDRESS, POI_LONGITUDE, POI_LATITUDE},
                        new int[]{R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude});
                mSearchList.setAdapter(simAdapt);
                mSearchLayout.setVisibility(View.VISIBLE);
            });
            inputtips.requestInputtipsAsyn();
        } catch (Exception e) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_search));
            XLog.e("ERROR: requestInputTips");
        }
    }

    @NonNull
    private static List<Map<String, Object>> getMapList(List<Tip> tips) {
        List<Map<String, Object>> data = new ArrayList<>();

        for (Tip tip : tips) {
            if (tip == null || tip.getPoint() == null) {
                continue;
            }

            LatLonPoint point = tip.getPoint();
            String address = tip.getAddress();
            if (TextUtils.isEmpty(address)) {
                address = TextUtils.isEmpty(tip.getDistrict()) ? mCurrentCity : tip.getDistrict();
            }
            Map<String, Object> poiItem = new HashMap<>();
            poiItem.put(POI_NAME, tip.getName());
            poiItem.put(POI_ADDRESS, address);
            poiItem.put(POI_LONGITUDE, String.valueOf(point.getLongitude()));
            poiItem.put(POI_LATITUDE, String.valueOf(point.getLatitude()));
            data.add(poiItem);
        }
        return data;
    }

    /*============================== 更新 相关 ==============================*/
    private void initUpdateVersion() {
        mDownloadManager =(DownloadManager) MainActivity.this.getSystemService(DOWNLOAD_SERVICE);

        // 用于监听下载完成后，转到安装界面
        mDownloadBdRcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                installNewVersion();
            }
        };
        registerReceiver(mDownloadBdRcv, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void checkUpdateVersion(boolean result) {
        String mapApiUrl = "https://api.github.com/repos/litongle/GoGoGo/releases/latest";

        okhttp3.Request request = new okhttp3.Request.Builder().url(mapApiUrl).get().build();
        final Call call = mOkHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                XLog.i("更新检测失败");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull okhttp3.Response response) throws IOException {
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    String resp = responseBody.string();
                    // 注意，该请求在子线程，不能直接操作界面
                    runOnUiThread(() -> {
                        try {
                            JSONObject getRetJson = new JSONObject(resp);
                            String curVersion = GoUtils.getVersionName(MainActivity.this);

                            if (curVersion != null
                                    && (!getRetJson.getString("name").contains(curVersion)
                                    || !getRetJson.getString("tag_name").contains(curVersion))) {
                                final android.app.AlertDialog alertDialog = new android.app.AlertDialog.Builder(MainActivity.this).create();
                                alertDialog.show();
                                alertDialog.setCancelable(false);
                                Window window = alertDialog.getWindow();
                                if (window != null) {
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);      // 防止出现闪屏
                                    window.setContentView(R.layout.update);
                                    window.setGravity(Gravity.CENTER);
                                    window.setWindowAnimations(R.style.DialogAnimFadeInFadeOut);

                                    TextView updateTitle = window.findViewById(R.id.update_title);
                                    updateTitle.setText(getRetJson.getString("name"));
                                    TextView updateTime = window.findViewById(R.id.update_time);
                                    updateTime.setText(getRetJson.getString("created_at"));
                                    TextView updateCommit = window.findViewById(R.id.update_commit);
                                    updateCommit.setText(getRetJson.getString("target_commitish"));

                                    TextView updateContent = window.findViewById(R.id.update_content);
                                    final Markwon markwon = Markwon.create(MainActivity.this);
                                    markwon.setMarkdown(updateContent, getRetJson.getString("body"));

                                    Button updateCancel = window.findViewById(R.id.update_ignore);
                                    updateCancel.setOnClickListener(v -> alertDialog.cancel());

                                    /* 这里用来保存下载地址 */
                                    JSONArray jsonArray = new JSONArray(getRetJson.getString("assets"));
                                    JSONObject jsonObject = jsonArray.getJSONObject(0);
                                    String download_url = jsonObject.getString("browser_download_url");
                                    mUpdateFilename = jsonObject.getString("name");

                                    Button updateAgree = window.findViewById(R.id.update_agree);
                                    updateAgree.setOnClickListener(v -> {
                                        alertDialog.cancel();
                                        GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.update_downloading));
                                        downloadNewVersion(download_url);
                                    });
                                }
                            } else {
                                if (result) {
                                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.update_last));
                                }
                            }
                        } catch (JSONException e) {
                            XLog.e("ERROR: resolve json");
                        }
                    });
                }
            }
        });
    }

    private void downloadNewVersion(String url) {
        if (mDownloadManager == null) {
            return;
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setAllowedOverRoaming(false);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setTitle(GoUtils.getAppName(this));
        request.setDescription("正在下载新版本...");
        request.setMimeType("application/vnd.android.package-archive");

        // DownloadManager不会覆盖已有的同名文件，需要自己来删除已存在的文件
        File file = new File(getExternalFilesDir("Updates"), mUpdateFilename);
        if (file.exists()) {
            if(!file.delete()) {
                return;
            }
        }
        request.setDestinationUri(Uri.fromFile(file));

        mDownloadId = mDownloadManager.enqueue(request);
    }

    private void installNewVersion() {
        Intent install = new Intent(Intent.ACTION_VIEW);
        Uri downloadFileUri = mDownloadManager.getUriForDownloadedFile(mDownloadId);
        File file = new File(getExternalFilesDir("Updates"), mUpdateFilename);
        if (downloadFileUri != null) {
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            // 在Broadcast中启动活动需要添加Intent.FLAG_ACTIVITY_NEW_TASK
            install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);    //添加这一句表示对目标应用临时授权该Uri所代表的文件
            install.addCategory("android.intent.category.DEFAULT");
            install.setDataAndType(ShareUtils.getUriFromFile(MainActivity.this, file), "application/vnd.android.package-archive");
            startActivity(install);
        } else {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            intent.addCategory("android.intent.category.DEFAULT");
            startActivity(intent);
        }
    }
}
