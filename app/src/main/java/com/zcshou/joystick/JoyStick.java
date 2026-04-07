package com.zcshou.joystick;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PixelFormat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.SearchView;

import androidx.preference.PreferenceManager;

import com.amap.api.services.core.AMapException;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Poi;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.help.Inputtips;
import com.amap.api.services.help.InputtipsQuery;
import com.amap.api.services.help.Tip;
import com.zcshou.database.DataBaseHistoryLocation;
import com.zcshou.gogogo.HistoryActivity;
import com.zcshou.gogogo.MainActivity;
import com.zcshou.gogogo.R;
import com.zcshou.utils.GoUtils;
import com.zcshou.utils.MapUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JoyStick extends View {
    private static final int DivGo = 1000;    /* 移动的时间间隔，单位 ms */
    private static final int WINDOW_TYPE_JOYSTICK = 0;
    private static final int WINDOW_TYPE_MAP = 1;
    private static final int WINDOW_TYPE_HISTORY = 2;
    private static final float DEFAULT_MAP_ZOOM = 18.0f;

    private final Context mContext;
    private WindowManager.LayoutParams mWindowParamCurrent;
    private WindowManager mWindowManager;
    private int mCurWin = WINDOW_TYPE_JOYSTICK;
    private final LayoutInflater inflater;
    private boolean isWalk;
    private ImageButton btnWalk;
    private boolean isRun;
    private ImageButton btnRun;
    private boolean isBike;
    private ImageButton btnBike;
    private JoyStickClickListener mListener;

    // 移动
    private View mJoystickLayout;
    private GoUtils.TimeCount mTimer;
    private boolean isMove;
    private double mSpeed = 1.2;        /* 默认的速度，单位 m/s */
    private double mAltitude = 55.0;
    private double mAngle = 0;
    private double mR = 0;
    private double disLng = 0;
    private double disLat = 0;
    private final SharedPreferences sharedPreferences;
    /* 历史记录悬浮窗相关 */
    private FrameLayout mHistoryLayout;
    private final List<Map<String, Object>> mAllRecord = new ArrayList<> ();
    private TextView noRecordText;
    private ListView mRecordListView;
    /* 地图悬浮窗相关 */
    private FrameLayout mMapLayout;
    private MapView mMapView;
    private AMap mAMap;
    private Marker mCurrentMarker;
    private Marker mMarkMarker;
    private LatLng mCurMapLngLat;
    private LatLng mMarkMapLngLat;
    private ListView mSearchList;
    private LinearLayout mSearchLayout;

    public JoyStick(Context context) {
        super(context);
        this.mContext = context;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);

        initWindowManager();

        inflater = LayoutInflater.from(mContext);

        if (inflater != null) {
            initJoyStickView();

            initJoyStickMapView();

            initHistoryView();
        }
    }

    public JoyStick(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mContext = context;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);

        initWindowManager();

        inflater = LayoutInflater.from(mContext);

        if (inflater != null) {
            initJoyStickView();

            initJoyStickMapView();

            initHistoryView();
        }
    }

    public JoyStick(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);

        initWindowManager();

        inflater = LayoutInflater.from(mContext);

        if (inflater != null) {
            initJoyStickView();

            initJoyStickMapView();

            initHistoryView();
        }
    }

    public void setCurrentPosition(double lng, double lat, double alt) {
        double[] lngLat = MapUtils.wgs84ToGcj02(lng, lat);
        mCurMapLngLat = new LatLng(lngLat[1], lngLat[0]);
        mAltitude = alt;

        resetAmapMap();
    }

    public void show() {
        switch (mCurWin) {
            case WINDOW_TYPE_MAP:
                if (mJoystickLayout.getParent() != null) {
                    mWindowManager.removeView(mJoystickLayout);
                }
                if (mHistoryLayout.getParent() != null) {
                    mWindowManager.removeView(mHistoryLayout);
                }
                if (mMapLayout.getParent() == null) {
                    resetAmapMap();
                    mWindowManager.addView(mMapLayout, mWindowParamCurrent);
                    if (mMapView != null) {
                        mMapView.onResume();
                    }
                }
                break;
            case WINDOW_TYPE_HISTORY:
                if (mMapLayout.getParent() != null) {
                    if (mMapView != null) {
                        mMapView.onPause();
                    }
                    mWindowManager.removeView(mMapLayout);
                }
                if (mJoystickLayout.getParent() != null) {
                    mWindowManager.removeView(mJoystickLayout);
                }
                if (mHistoryLayout.getParent() == null) {
                    mWindowManager.addView(mHistoryLayout, mWindowParamCurrent);
                }
                break;
            case WINDOW_TYPE_JOYSTICK:
                if (mMapLayout.getParent() != null) {
                    if (mMapView != null) {
                        mMapView.onPause();
                    }
                    mWindowManager.removeView(mMapLayout);
                }
                if (mHistoryLayout.getParent() != null) {
                    mWindowManager.removeView(mHistoryLayout);
                }
                if (mJoystickLayout.getParent() == null) {
                    mWindowManager.addView(mJoystickLayout, mWindowParamCurrent);
                }
                break;
        }
    }

    public void hide() {
        if (mMapLayout.getParent() != null) {
            if (mMapView != null) {
                mMapView.onPause();
            }
            mWindowManager.removeViewImmediate(mMapLayout);
        }

        if (mJoystickLayout.getParent() != null) {
            mWindowManager.removeViewImmediate(mJoystickLayout);
        }

        if (mHistoryLayout.getParent() != null) {
            mWindowManager.removeViewImmediate(mHistoryLayout);
        }
    }

    public void destroy() {
        if (mMapLayout.getParent() != null) {
            if (mMapView != null) {
                mMapView.onPause();
            }
            mWindowManager.removeViewImmediate(mMapLayout);
        }

        if (mJoystickLayout.getParent() != null) {
            mWindowManager.removeViewImmediate(mJoystickLayout);
        }

        if (mHistoryLayout.getParent() != null) {
            mWindowManager.removeViewImmediate(mHistoryLayout);
        }

        if (mCurrentMarker != null) {
            mCurrentMarker.remove();
            mCurrentMarker = null;
        }
        if (mMarkMarker != null) {
            mMarkMarker.remove();
            mMarkMarker = null;
        }
        if (mMapView != null) {
            mMapView.onDestroy();
        }
    }

    public void setListener(JoyStickClickListener mListener) {
        this.mListener = mListener;
    }

    private void initWindowManager() {
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mWindowParamCurrent = new WindowManager.LayoutParams();
        mWindowParamCurrent.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        mWindowParamCurrent.format = PixelFormat.RGBA_8888;
        mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE      // 不添加这个将导致游戏无法启动（MIUI12）,添加之后导致键盘无法显示
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        mWindowParamCurrent.gravity = Gravity.START | Gravity.TOP;
        mWindowParamCurrent.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParamCurrent.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParamCurrent.x = 300;
        mWindowParamCurrent.y = 300;
    }

    @SuppressLint("InflateParams")
    private void initJoyStickView() {
        /* 移动计时器 */
        mTimer = new GoUtils.TimeCount(DivGo, DivGo);
        mTimer.setListener(new GoUtils.TimeCount.TimeCountListener() {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                // 注意：这里的 x y 与 圆中角度的对应问题（以 X 轴正向为 0 度）且转换为 km
                disLng = mSpeed * (double)(DivGo / 1000) * mR * Math.cos(mAngle * 2 * Math.PI / 360) / 1000;// 注意安卓中的三角函数使用的是弧度
                disLat = mSpeed * (double)(DivGo / 1000) * mR * Math.sin(mAngle * 2 * Math.PI / 360) / 1000;// 注意安卓中的三角函数使用的是弧度
                mListener.onMoveInfo(mSpeed, disLng, disLat, 90.0F-mAngle);
                mTimer.start();
            }
        });
        // 获取参数区设置的速度
        try {
            mSpeed = Double.parseDouble(sharedPreferences.getString("setting_walk", getResources().getString(R.string.setting_walk_default)));
        } catch (NumberFormatException e) {  // GOOD: The exception is caught.
            mSpeed = 1.2;
        }
        mJoystickLayout = inflater.inflate(R.layout.joystick, null);

        /* 整个摇杆拖动事件处理 */
        mJoystickLayout.setOnTouchListener(new JoyStickOnTouchListener());

        /* 位置按钮点击事件处理 */
        ImageButton btnPosition = mJoystickLayout.findViewById(R.id.joystick_position);
        btnPosition.setOnClickListener(v -> {
            if (mMapLayout.getParent() == null) {
                mCurWin = WINDOW_TYPE_MAP;
                show();
            }
        });

        /* 历史按钮点击事件处理 */
        ImageButton btnHistory = mJoystickLayout.findViewById(R.id.joystick_history);
        btnHistory.setOnClickListener(v -> {
            if (mHistoryLayout.getParent() == null) {
                mCurWin = WINDOW_TYPE_HISTORY;
                show();
            }
        });

        /* 步行按键的点击处理 */
        btnWalk = mJoystickLayout.findViewById(R.id.joystick_walk);
        btnWalk.setOnClickListener(v -> {
            if (!isWalk) {
                btnWalk.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));
                isWalk = true;
                btnRun.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme()));
                isRun = false;
                btnBike.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme()));
                isBike = false;
                try {
                    mSpeed = Double.parseDouble(sharedPreferences.getString("setting_walk", getResources().getString(R.string.setting_walk_default)));
                } catch (NumberFormatException e) {  // GOOD: The exception is caught.
                    mSpeed = 1.2;
                }
            }
        });
        /* 默认为步行 */
        isWalk = true;
        btnWalk.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));
        /* 跑步按键的点击处理 */
        isRun = false;
        btnRun = mJoystickLayout.findViewById(R.id.joystick_run);
        btnRun.setOnClickListener(v -> {
            if (!isRun) {
                btnRun.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));
                isRun = true;
                btnWalk.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme()));
                isWalk = false;
                btnBike.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme()));
                isBike = false;
                try {
                    mSpeed = Double.parseDouble(sharedPreferences.getString("setting_run", getResources().getString(R.string.setting_run_default)));
                } catch (NumberFormatException e) {  // GOOD: The exception is caught.
                    mSpeed = 3.6;
                }
            }
        });
        /* 自行车按键的点击处理 */
        isBike = false;
        btnBike = mJoystickLayout.findViewById(R.id.joystick_bike);
        btnBike.setOnClickListener(v -> {
            if (!isBike) {
                btnBike.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));
                isBike = true;
                btnWalk.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme()));
                isWalk = false;
                btnRun.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme()));
                isRun = false;
                try {
                    mSpeed = Double.parseDouble(sharedPreferences.getString("setting_bike", getResources().getString(R.string.setting_bike_default)));
                } catch (NumberFormatException e) {  // GOOD: The exception is caught.
                    mSpeed = 10.0;
                }
            }
        });
        /* 方向键点击处理 */
        RockerView rckView = mJoystickLayout.findViewById(R.id.joystick_rocker);
        rckView.setListener(this::processDirection);

        /* 方向键点击处理 */
        ButtonView btnView = mJoystickLayout.findViewById(R.id.joystick_button);
        btnView.setListener(this::processDirection);

        /* 这里用来决定摇杆类型 */
        if (sharedPreferences.getString("setting_joystick_type", "0").equals("0")) {
            rckView.setVisibility(VISIBLE);
            btnView.setVisibility(GONE);
        } else {
            rckView.setVisibility(GONE);
            btnView.setVisibility(VISIBLE);
        }
    }

    private void processDirection(boolean auto, double angle, double r) {
        if (r <= 0) {
            mTimer.cancel();
            isMove = false;
        } else {
            mAngle = angle;
            mR = r;
            if (auto) {
                if (!isMove) {
                    mTimer.start();
                    isMove = true;
                }
            } else {
                mTimer.cancel();
                isMove = false;
                // 注意：这里的 x y 与 圆中角度的对应问题（以 X 轴正向为 0 度）且转换为 km
                disLng = mSpeed * (double)(DivGo / 1000) * mR * Math.cos(mAngle * 2 * Math.PI / 360) / 1000;// 注意安卓中的三角函数使用的是弧度
                disLat = mSpeed * (double)(DivGo / 1000) * mR * Math.sin(mAngle * 2 * Math.PI / 360) / 1000;// 注意安卓中的三角函数使用的是弧度
                mListener.onMoveInfo(mSpeed, disLng, disLat, 90.0F-mAngle);
            }
        }
    }

    private class JoyStickOnTouchListener implements OnTouchListener {
        private int x;
        private int y;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    x = (int) event.getRawX();
                    y = (int) event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    int nowX = (int) event.getRawX();
                    int nowY = (int) event.getRawY();
                    int movedX = nowX - x;
                    int movedY = nowY - y;
                    x = nowX;
                    y = nowY;

                    mWindowParamCurrent.x += movedX;
                    mWindowParamCurrent.y += movedY;
                    mWindowManager.updateViewLayout(view, mWindowParamCurrent);
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    break;
                default:
                    break;
            }
            return false;
        }
    }

    public interface JoyStickClickListener {
        void onMoveInfo(double speed, double disLng, double disLat, double angle);
        void onPositionInfo(double lng, double lat, double alt);
    }


    @SuppressLint({"InflateParams", "ClickableViewAccessibility"})
    private void initJoyStickMapView() {
        mMapLayout = (FrameLayout)inflater.inflate(R.layout.joystick_map, null);
        mMapLayout.setOnTouchListener(new JoyStickOnTouchListener());

        mSearchList = mMapLayout.findViewById(R.id.map_search_list_view);
        mSearchLayout = mMapLayout.findViewById(R.id.map_search_linear);
        mSearchList.setOnItemClickListener((parent, view, position, id) -> {
            mSearchLayout.setVisibility(View.GONE);

            String lng = ((TextView) view.findViewById(R.id.poi_longitude)).getText().toString();
            String lat = ((TextView) view.findViewById(R.id.poi_latitude)).getText().toString();
            markAmapMap(new LatLng(Double.parseDouble(lat), Double.parseDouble(lng)));
        });

        TextView tips = mMapLayout.findViewById(R.id.joystick_map_tips);
        SearchView mSearchView = mMapLayout.findViewById(R.id.joystick_map_searchView);
        mSearchView.setOnSearchClickListener(v -> {
            tips.setVisibility(GONE);

            // 特殊处理：这里让搜索框获取焦点，以显示输入法
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mMapLayout, mWindowParamCurrent);
        });
        mSearchView.setOnCloseListener(() -> {
            tips.setVisibility(VISIBLE);
            mSearchLayout.setVisibility(GONE);

            // 关闭时清除焦点
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mMapLayout, mWindowParamCurrent);

            return false;       /* 这里必须返回false，否则需要自行处理搜索框的折叠 */
        });
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText != null && newText.length() > 0) {
                    try {
                        requestInputTips(newText);
                    } catch (Exception e) {
                        GoUtils.DisplayToast(mContext,getResources().getString(R.string.app_error_search));
                        e.printStackTrace();
                    }
                } else {
                    mSearchLayout.setVisibility(GONE);
                }

                return true;
            }
        });

        ImageButton btnGo = mMapLayout.findViewById(R.id.btnGo);
        btnGo.setOnClickListener(v -> {
            // 关闭时清除焦点
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mMapLayout, mWindowParamCurrent);

            tips.setVisibility(VISIBLE);
            mSearchView.clearFocus();
            mSearchView.onActionViewCollapsed();

            if (mMarkMapLngLat == null) {
                GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_error_location));
            } else {
                if (mCurMapLngLat != mMarkMapLngLat) {
                    mCurMapLngLat = mMarkMapLngLat;
                    mMarkMapLngLat = null;

                    double[] lngLat = MapUtils.gcj02ToWgs84(mCurMapLngLat.longitude, mCurMapLngLat.latitude);
                    mListener.onPositionInfo(lngLat[0], lngLat[1], mAltitude);

                    resetAmapMap();

                    GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_location_ok));
                }
            }
        });
        btnGo.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));

        ImageButton btnClose = mMapLayout.findViewById(R.id.map_close);
        btnClose.setOnClickListener(v -> {
            // 关闭时清除焦点
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

            tips.setVisibility(VISIBLE);
            mSearchLayout.setVisibility(GONE);
            mSearchView.clearFocus();
            mSearchView.onActionViewCollapsed();

            mCurWin = WINDOW_TYPE_JOYSTICK;
            show();
        });

        ImageButton btnBack = mMapLayout.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> resetAmapMap());
        btnBack.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));

        initAmapMap();
    }

    private void initAmapMap() {
        mMapView = mMapLayout.findViewById(R.id.map_joystick);
        mMapView.onCreate(null);
        mAMap = mMapView.getMap();
        mAMap.getUiSettings().setZoomControlsEnabled(false);
        mAMap.setMapType(AMap.MAP_TYPE_NORMAL);
        mAMap.setOnMapClickListener(this::markAmapMap);
        mAMap.setOnMapLongClickListener(this::markAmapMap);
        mAMap.setOnPOIClickListener((Poi poi) -> markAmapMap(poi.getCoordinate()));
    }

    private void resetAmapMap() {
        if (mCurMapLngLat == null) {
            return;
        }

        if (mCurrentMarker == null) {
            mCurrentMarker = mAMap.addMarker(new MarkerOptions().position(mCurMapLngLat));
        } else {
            mCurrentMarker.setPosition(mCurMapLngLat);
        }

        if (mMarkMarker != null) {
            mMarkMarker.remove();
            mMarkMarker = null;
        }

        moveMapTo(mCurMapLngLat);
    }

    private void markAmapMap(LatLng latLng) {
        mMarkMapLngLat = latLng;

        MarkerOptions markerOptions = new MarkerOptions().position(latLng);
        if (MainActivity.mMapIndicator != null) {
            markerOptions.icon(MainActivity.mMapIndicator);
        }
        if (mMarkMarker == null) {
            mMarkMarker = mAMap.addMarker(markerOptions);
        } else {
            mMarkMarker.setPosition(latLng);
            if (MainActivity.mMapIndicator != null) {
                mMarkMarker.setIcon(MainActivity.mMapIndicator);
            }
        }

        moveMapTo(latLng);
    }

    private void moveMapTo(LatLng latLng) {
        if (mAMap != null && latLng != null) {
            mAMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_MAP_ZOOM));
        }
    }

    private void requestInputTips(String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            mSearchLayout.setVisibility(GONE);
            return;
        }

        try {
            InputtipsQuery query = new InputtipsQuery(keyword, TextUtils.isEmpty(MainActivity.mCurrentCity) ? "" : MainActivity.mCurrentCity);
            query.setCityLimit(false);
            Inputtips inputtips = new Inputtips(mContext, query);
            inputtips.setInputtipsListener((List<Tip> tips, int rCode) -> {
                if (rCode != AMapException.CODE_AMAP_SUCCESS || tips == null) {
                    GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_search_null));
                    return;
                }

                List<Map<String, Object>> data = new ArrayList<>();
                for (Tip tip : tips) {
                    if (tip == null || tip.getPoint() == null) {
                        continue;
                    }

                    LatLonPoint point = tip.getPoint();
                    String address = tip.getAddress();
                    if (TextUtils.isEmpty(address)) {
                        address = TextUtils.isEmpty(tip.getDistrict()) ? MainActivity.mCurrentCity : tip.getDistrict();
                    }

                    Map<String, Object> poiItem = new HashMap<>();
                    poiItem.put(MainActivity.POI_NAME, tip.getName());
                    poiItem.put(MainActivity.POI_ADDRESS, address);
                    poiItem.put(MainActivity.POI_LONGITUDE, String.valueOf(point.getLongitude()));
                    poiItem.put(MainActivity.POI_LATITUDE, String.valueOf(point.getLatitude()));
                    data.add(poiItem);
                }

                if (data.isEmpty()) {
                    GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_search_null));
                    mSearchLayout.setVisibility(GONE);
                    return;
                }

                SimpleAdapter simAdapt = new SimpleAdapter(
                        mContext,
                        data,
                        R.layout.search_poi_item,
                        new String[] {MainActivity.POI_NAME, MainActivity.POI_ADDRESS, MainActivity.POI_LONGITUDE, MainActivity.POI_LATITUDE},
                        new int[] {R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude});
                mSearchList.setAdapter(simAdapt);
                mSearchLayout.setVisibility(View.VISIBLE);
            });
            inputtips.requestInputtipsAsyn();
        } catch (Exception e) {
            GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_error_search));
            Log.e("JOYSTICK", "ERROR - requestInputTips", e);
        }
    }


    @SuppressLint({"InflateParams", "ClickableViewAccessibility"})
    private void initHistoryView() {
        mHistoryLayout = (FrameLayout)inflater.inflate(R.layout.joystick_history, null);
        mHistoryLayout.setOnTouchListener(new JoyStickOnTouchListener());

        TextView tips = mHistoryLayout.findViewById(R.id.joystick_his_tips);
        SearchView mSearchView = mHistoryLayout.findViewById(R.id.joystick_his_searchView);
        mSearchView.setOnSearchClickListener(v -> {
            tips.setVisibility(GONE);

            // 特殊处理：这里让搜索框获取焦点，以显示输入法
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mHistoryLayout, mWindowParamCurrent);
        });
        mSearchView.setOnCloseListener(() -> {
            tips.setVisibility(VISIBLE);

            // 关闭时清除焦点
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mHistoryLayout, mWindowParamCurrent);

            return false;       /* 这里必须返回false，否则需要自行处理搜索框的折叠 */
        });
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {// 当点击搜索按钮时触发该方法
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {// 当搜索内容改变时触发该方法
                if (TextUtils.isEmpty(newText)) {
                    showHistory(mAllRecord);
                } else {
                    List<Map<String, Object>> searchRet = new ArrayList<>();
                    for (int i = 0; i < mAllRecord.size(); i++){
                        if (mAllRecord.get(i).toString().indexOf(newText) > 0){
                            searchRet.add(mAllRecord.get(i));
                        }
                    }

                    if (searchRet.size() > 0) {
                        showHistory(searchRet);
                    } else {
                        GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_search_null));
                        showHistory(mAllRecord);
                    }
                }

                return false;
            }
        });

        noRecordText = mHistoryLayout.findViewById(R.id.joystick_his_record_no_textview);
        mRecordListView = mHistoryLayout.findViewById(R.id.joystick_his_record_list_view);
        mRecordListView.setOnItemClickListener((adapterView, view, i, l) -> {
            // 关闭时清除焦点
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mHistoryLayout, mWindowParamCurrent);

            mSearchView.clearFocus();
            mSearchView.onActionViewCollapsed();
            tips.setVisibility(VISIBLE);

            // wgs84坐标
            String wgs84LatLng = (String) ((TextView) view.findViewById(R.id.WGSLatLngText)).getText();
            wgs84LatLng = wgs84LatLng.substring(wgs84LatLng.indexOf('[') + 1, wgs84LatLng.indexOf(']'));
            String[] wgs84latLngStr = wgs84LatLng.split(" ");
            String wgs84Longitude = wgs84latLngStr[0].substring(wgs84latLngStr[0].indexOf(':') + 1);
            String wgs84Latitude = wgs84latLngStr[1].substring(wgs84latLngStr[1].indexOf(':') + 1);

            mListener.onPositionInfo(Double.parseDouble(wgs84Longitude), Double.parseDouble(wgs84Latitude), mAltitude);

            // 注意这里在选择位置之后需要刷新地图
            String customLatLng = (String) ((TextView) view.findViewById(R.id.BDLatLngText)).getText();
            customLatLng = customLatLng.substring(customLatLng.indexOf('[') + 1, customLatLng.indexOf(']'));
            String[] customLatLngStr = customLatLng.split(" ");
            String customLongitude = customLatLngStr[0].substring(customLatLngStr[0].indexOf(':') + 1);
            String customLatitude = customLatLngStr[1].substring(customLatLngStr[1].indexOf(':') + 1);
            String coordType = ((TextView) view.findViewById(R.id.CustomCoordTypeText)).getText().toString();
            double[] gcjLonLat = MapUtils.toGcj02(Double.parseDouble(customLongitude), Double.parseDouble(customLatitude), coordType);
            mCurMapLngLat = new LatLng(gcjLonLat[1], gcjLonLat[0]);
            resetAmapMap();

            GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_location_ok));
        });

        fetchAllRecord();

        showHistory(mAllRecord);

        ImageButton btnClose = mHistoryLayout.findViewById(R.id.joystick_his_close);
        btnClose.setOnClickListener(v -> {
            // 关闭时清除焦点
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

            mSearchView.clearFocus();
            mSearchView.onActionViewCollapsed();
            tips.setVisibility(VISIBLE);

            mCurWin = WINDOW_TYPE_JOYSTICK;
            show();
        });
    }

    private void fetchAllRecord() {
        SQLiteDatabase mHistoryLocationDB;

        try {
            mAllRecord.clear();
            DataBaseHistoryLocation hisLocDBHelper = new DataBaseHistoryLocation(mContext.getApplicationContext());
            mHistoryLocationDB = hisLocDBHelper.getWritableDatabase();

            Cursor cursor = mHistoryLocationDB.query(DataBaseHistoryLocation.TABLE_NAME, null,
                    DataBaseHistoryLocation.DB_COLUMN_ID + " > ?", new String[] {"0"},
                    null, null, DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " DESC", null);

            int idColumn = cursor.getColumnIndexOrThrow(DataBaseHistoryLocation.DB_COLUMN_ID);
            int locationColumn = cursor.getColumnIndexOrThrow(DataBaseHistoryLocation.DB_COLUMN_LOCATION);
            int wgsLongitudeColumn = cursor.getColumnIndexOrThrow(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84);
            int wgsLatitudeColumn = cursor.getColumnIndexOrThrow(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84);
            int timestampColumn = cursor.getColumnIndexOrThrow(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP);
            int customLongitudeColumn = cursor.getColumnIndexOrThrow(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM);
            int customLatitudeColumn = cursor.getColumnIndexOrThrow(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM);
            int coordTypeColumn = cursor.getColumnIndexOrThrow(DataBaseHistoryLocation.DB_COLUMN_CUSTOM_COORD_TYPE);
            while (cursor.moveToNext()) {
                Map<String, Object> item = new HashMap<>();
                int ID = cursor.getInt(idColumn);
                String Location = cursor.getString(locationColumn);
                String Longitude = cursor.getString(wgsLongitudeColumn);
                String Latitude = cursor.getString(wgsLatitudeColumn);
                long TimeStamp = cursor.getInt(timestampColumn);
                String customLongitude = cursor.getString(customLongitudeColumn);
                String customLatitude = cursor.getString(customLatitudeColumn);
                String coordType = cursor.getString(coordTypeColumn);
                Log.d("TB", ID + "\t" + Location + "\t" + Longitude + "\t" + Latitude + "\t" + TimeStamp + "\t" + customLongitude + "\t" + customLatitude);
                BigDecimal bigDecimalLongitude = BigDecimal.valueOf(Double.parseDouble(Longitude));
                BigDecimal bigDecimalLatitude = BigDecimal.valueOf(Double.parseDouble(Latitude));
                BigDecimal bigDecimalCustomLongitude = BigDecimal.valueOf(Double.parseDouble(customLongitude));
                BigDecimal bigDecimalCustomLatitude = BigDecimal.valueOf(Double.parseDouble(customLatitude));
                double doubleLongitude = bigDecimalLongitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleLatitude = bigDecimalLatitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleCustomLongitude = bigDecimalCustomLongitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleCustomLatitude = bigDecimalCustomLatitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                item.put(HistoryActivity.KEY_ID, Integer.toString(ID));
                item.put(HistoryActivity.KEY_LOCATION, Location);
                item.put(HistoryActivity.KEY_TIME, GoUtils.timeStamp2Date(Long.toString(TimeStamp)));
                item.put(HistoryActivity.KEY_LNG_LAT_WGS, "[经度:" + doubleLongitude + " 纬度:" + doubleLatitude + "]");
                item.put(HistoryActivity.KEY_LNG_LAT_CUSTOM, "[经度:" + doubleCustomLongitude + " 纬度:" + doubleCustomLatitude + "]");
                item.put(HistoryActivity.KEY_CUSTOM_COORD_TYPE, MapUtils.normalizeCoordType(coordType));
                mAllRecord.add(item);
            }
            cursor.close();
            mHistoryLocationDB.close();
        } catch (Exception e) {
            Log.e("JOYSTICK", "ERROR - fetchAllRecord");
        }
    }

    private void showHistory(List<Map<String, Object>> list) {
        if (list.size() == 0) {
            mRecordListView.setVisibility(View.GONE);
            noRecordText.setVisibility(View.VISIBLE);
        } else {
            noRecordText.setVisibility(View.GONE);
            mRecordListView.setVisibility(View.VISIBLE);

            try {
                SimpleAdapter simAdapt = new SimpleAdapter(
                        mContext,
                        list,
                        R.layout.history_item,
                        new String[]{HistoryActivity.KEY_ID, HistoryActivity.KEY_LOCATION, HistoryActivity.KEY_TIME, HistoryActivity.KEY_LNG_LAT_WGS, HistoryActivity.KEY_LNG_LAT_CUSTOM, HistoryActivity.KEY_CUSTOM_COORD_TYPE}, // 与下面数组元素要一一对应
                        new int[]{R.id.LocationID, R.id.LocationText, R.id.TimeText, R.id.WGSLatLngText, R.id.BDLatLngText, R.id.CustomCoordTypeText});
                mRecordListView.setAdapter(simAdapt);
            } catch (Exception e) {
                Log.e("JOYSTICK", "ERROR - showHistory");
            }
        }
    }
}
