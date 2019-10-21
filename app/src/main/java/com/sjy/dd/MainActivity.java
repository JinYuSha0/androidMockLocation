package com.sjy.dd;

import android.Manifest;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.CameraUpdateFactory;
import com.amap.api.maps2d.LocationSource;
import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.model.BitmapDescriptor;
import com.amap.api.maps2d.model.BitmapDescriptorFactory;
import com.amap.api.maps2d.model.LatLng;
import com.amap.api.maps2d.model.Marker;
import com.amap.api.maps2d.model.MarkerOptions;

public class MainActivity extends AppCompatActivity implements LocationListener, LocationSource, AMapLocationListener, AMap.OnMapClickListener {
    private static final int MY_PERMISSIONS_REQUEST_FINE_LOCATION = 1;
    public static final String Tag = "DDSJY";

    private MapView mapView;
    private AMap aMap;
    private AMapLocationClient mLocationClient;
    private AMapLocationClientOption mLocationOption;
    private OnLocationChangedListener mListener;
    private MarkerOptions markerOption;
    private BitmapDescriptor ICON_YELLOW = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW);
    private Marker centerMark;

    private boolean isFirstLoc = true;
    private boolean isStartMock = false;

    private Context mContext;
    private MockLocationProvider mockGPS;
    private MockLocationProvider mockWifi;
    private MockLoopThread mockGpsThread;
    private MockLoopThread mockWIfiThread;
    private CurrLocationInfoModel currLocationInfoModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this.getApplicationContext();

        initActionBar();
        observerLocationInfoModel();

        mapView = (MapView) findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);
        aMap = mapView.getMap();
        aMap.setOnMapClickListener(this);
        aMap.setLocationSource(this);
        aMap.getUiSettings().setMyLocationButtonEnabled(true);
        markerOption = new MarkerOptions().draggable(true);
        markerOption.icon(ICON_YELLOW);

        final AppCompatButton btnStartMock = (AppCompatButton) findViewById(R.id.btn_start_mock);
        btnStartMock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isStartMock) {
                    if (isMockSettingON()) {
                        startMock();
                        isStartMock = true;
                        btnStartMock.setText(R.string.stop_mock);
                    } else {
                        settingMockON();
                    }
                } else {
                    stopMock();
                    isStartMock = false;
                    btnStartMock.setText(R.string.start_mock);
                }
            }
        });

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            askRunTimePermissions();
        } else {
            initLoc();
        }
    }

    public void initActionBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.app_name);
        toolbar.setTitleTextColor(0xFFFFFFFF);
        setSupportActionBar(toolbar);

        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_search:
                        Toast.makeText(MainActivity.this, "Search !", Toast.LENGTH_SHORT).show();
                        break;
                }
                return true;
            }
        });
    }

    public void observerLocationInfoModel() {
        final TextView addressTextView = (TextView) findViewById(R.id.addressTextView);
        final TextView longitudeTextView = (TextView) findViewById(R.id.longitudeTextView);
        final TextView latitudeTextView = (TextView) findViewById(R.id.latitudeTextView);
        currLocationInfoModel = ViewModelProviders.of(this).get(CurrLocationInfoModel.class);
        final Observer<String> addressObserver = new Observer<String>() {
            @Override
            public void onChanged(String s) {
                if (s == null || s.equals("")) {
                    s = getString(R.string.unknown);
                }
                addressTextView.setText(getString(R.string.curr_location) + s);
            }
        };
        currLocationInfoModel.getCurrAddress().observe(this,addressObserver);

        final Observer<Double> longitudeObserver = new Observer<Double>() {
            @Override
            public void onChanged(Double aDouble) {
                longitudeTextView.setText(getString(R.string.longitude) + aDouble.toString());
            }
        };
        currLocationInfoModel.getCurrLongitude().observe(this,longitudeObserver);

        final Observer<Double> latitudeObserver = new Observer<Double>() {
            @Override
            public void onChanged(Double aDouble) {
                latitudeTextView.setText(getString(R.string.latitude) + aDouble.toString());
            }
        };
        currLocationInfoModel.getCurrLatitude().observe(this,latitudeObserver);
    }

    public void updateLocationInfoModel(@Nullable String address, @Nullable Double longitude, @Nullable Double latitude) {
        if (address != null) {
            currLocationInfoModel.getCurrAddress().setValue(address);
        }

        if (longitude != null) {
            currLocationInfoModel.getCurrLongitude().setValue(longitude);
        }

        if (latitude != null) {
            currLocationInfoModel.getCurrLatitude().setValue(latitude);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private void initLoc() {
        mLocationClient = new AMapLocationClient(getApplicationContext());
        mLocationClient.setLocationListener(this);
        mLocationOption = new AMapLocationClientOption();
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        mLocationOption.setNeedAddress(true);
        mLocationOption.setOnceLocation(false);
        mLocationOption.setWifiActiveScan(true);
        mLocationOption.setMockEnable(false);
        mLocationOption.setInterval(2000);
        mLocationClient.setLocationOption(mLocationOption);
        mLocationClient.startLocation();
    }

    private boolean isMockSettingON() {
        boolean isMockLocation = false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AppOpsManager opsManager = (AppOpsManager) this.getSystemService(Context.APP_OPS_SERVICE);
                isMockLocation = (opsManager.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, android.os.Process.myUid(), BuildConfig.APPLICATION_ID)) == AppOpsManager.MODE_ALLOWED;
            } else {
                isMockLocation = !android.provider.Settings.Secure.getString(this.getContentResolver(), "mock_location").equals("0");
            }
        } catch (Exception e) {
            return isMockLocation;
        }
        return isMockLocation;
    }

    private void settingMockON() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setMessage(R.string.allow_mock_location).setTitle(R.string.setting_mock_location);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                startActivity(i);
            }
        });
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void startMock() {
        if (isMockSettingON() && ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationManager lm = (LocationManager) mContext.getSystemService(mContext.LOCATION_SERVICE);
            if (mockGPS == null) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,10,this);
                mockGPS = new MockLocationProvider(LocationManager.GPS_PROVIDER, mContext);
                mockGpsThread = new MockLoopThread(new MockLoopThread.Action() {
                    @Override
                    public void todo() {
                        try {
                            mock(mockGPS);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void end() {
                        mockGPS.shutdown();
                        mockGPS = null;
                    }
                }, 500);
            }

            if (mockWifi == null) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,1000,10,this);
                mockWifi = new MockLocationProvider(LocationManager.NETWORK_PROVIDER, mContext);
                mockWIfiThread = new MockLoopThread(new MockLoopThread.Action() {
                    @Override
                    public void todo() {
                        try {
                            mock(mockWifi);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void end() {
                        mockWifi.shutdown();
                        mockWifi = null;
                    }
                }, 500);
            }

            mockGpsThread.start();
            mockWIfiThread.start();
        }
    }

    private void stopMock() {
        if (mockGPS != null) {
            mockGpsThread.end();
        }

        if (mockWifi != null) {
            mockWIfiThread.end();
        }
    }

    public void mock(MockLocationProvider mp) {
        if (mp == null) return;

        double lat, lon;
        lat = currLocationInfoModel.getCurrLatitude().getValue();
        lon = currLocationInfoModel.getCurrLongitude().getValue();

        LocationManager lm = (LocationManager) mContext.getSystemService(mContext.LOCATION_SERVICE);
        if (lm.getProvider(mp.providerName) != null) {
            mp.pushLocation(lat,lon);
            try {
                lm.requestLocationUpdates(mp.providerName,1000,10,this);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        if (centerMark == null) {
            centerMark = aMap.addMarker(markerOption);
        }
        centerMark.setPosition(latLng);
        updateLocationInfoModel(null,latLng.longitude,latLng.latitude);
    }

    private void askRunTimePermissions() {
        ActivityCompat.requestPermissions(this
                ,new  String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                MY_PERMISSIONS_REQUEST_FINE_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_FINE_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initLoc();
                } else {
                    Toast.makeText(this, R.string.allow_location_permission, Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }

    @Override
    public void activate(OnLocationChangedListener listener) {
        mListener = listener;
    }

    @Override
    public void deactivate() {
        mListener = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        stopMock();
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLocationChanged(AMapLocation amapLocation) {
        if (amapLocation != null) {
            if (amapLocation.getErrorCode() == 0) {
                // 如果不设置标志位，此时再拖动地图时，它会不断将地图移动到当前的位置
                if (isFirstLoc) {
                    //定位成功回调信息，设置相关消息
                    Double longitude = amapLocation.getLongitude();//获取经度
                    Double latitude = amapLocation.getLatitude();//获取纬度
                    LatLng latLng = new LatLng(latitude,longitude);

                    //获取定位信息
                    StringBuffer buffer = new StringBuffer();
                    buffer.append(amapLocation.getCountry() + "" + amapLocation.getProvince() + "" + amapLocation.getCity() + "" + amapLocation.getProvince() + "" + amapLocation.getDistrict() + "" + amapLocation.getStreet() + "" + amapLocation.getStreetNum());
                    updateLocationInfoModel(buffer.toString(), longitude, latitude);

                    //设置缩放级别
                    aMap.moveCamera(CameraUpdateFactory.zoomTo(17));
                    //将地图移动到定位点
                    aMap.moveCamera(CameraUpdateFactory.changeLatLng(new LatLng(amapLocation.getLatitude(), amapLocation.getLongitude())));
                    //点击定位按钮 能够将地图的中心移动到定位点
                    mListener.onLocationChanged(amapLocation);
                    // 将定位点放置中心
                    markerOption.position(latLng);
                    centerMark = aMap.addMarker(markerOption);

                    isFirstLoc = false;
                }
            } else {
                //显示错误信息ErrCode是错误码，errInfo是错误信息，详见错误码表。
                Log.e("AmapError", "location Error, ErrCode:"
                        + amapLocation.getErrorCode() + ", errInfo:"
                        + amapLocation.getErrorInfo());

                Toast.makeText(getApplicationContext(), "定位失败", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }
}
