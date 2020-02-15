package com.vivalnk.temptest.utils;

import android.content.Context;
import android.location.LocationManager;


public class GPSUtils {

  public static boolean isGPSEnable(Context context) {
    try {
      LocationManager locationManager = (LocationManager) context
          .getSystemService(Context.LOCATION_SERVICE);
      boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    //  boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
      return gps ;
    } catch (Exception e) {
      return false;
    }
  }
}
