package com.healthsaas.zewals.zewadb;

import android.util.Log;

import com.healthsaas.zewals.Constants;
import com.healthsaas.zewals.Utils;
import com.lifesense.ble.bean.PedometerData;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public class PedoEntry {
    public int ID;
    public String modelName;
    public String deviceID;
    public String manufacturer;
    public float batteryLevel;
    public int batteryLevel2;
    public int walkSteps;
    public int runSteps;
    public int distance;
    public int exerciseTime;
    public float calories;
    public int intensityLevel;
    public int sleepStatus;
    public float exerciseAmount;
    public long measurementTime;
    public long receiptTime;
    public int measureTimeUtcOffset;

    public PedoEntry() {
        this.ID = 0;
        this.modelName = Constants.ZEWA_MODEL_ACTRKR_1;
        this.deviceID = "";
        this.manufacturer = Constants.ZEWA_MFG_NAME;
        this.batteryLevel = -1;
        this.walkSteps = 0;
        this.runSteps = 0;
        this.distance = 0;
        this.exerciseTime = 0;
        this.calories = 0;
        this.intensityLevel = 0;
        this.sleepStatus = 0;
        this.exerciseAmount = 0;
        this.measurementTime = 0;
        this.receiptTime = System.currentTimeMillis() / 1000;
        this.measureTimeUtcOffset = 0;
    }
    public PedoEntry(final PedometerData pData) {
        NumberFormat formatter = NumberFormat.getInstance(Locale.US);
        formatter.setMaximumFractionDigits(2);
        formatter.setMinimumFractionDigits(2);
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        this.ID = 0;
        this.modelName = Constants.ZEWA_MODEL_ACTRKR_1;
        this.deviceID = pData.getDeviceId();
        this.manufacturer = Constants.ZEWA_MFG_NAME;
        this.batteryLevel = Float.valueOf(formatter.format(pData.getBatteryVoltage()));;
        this.batteryLevel2 = pData.getBatteryPercent();
        this.walkSteps = pData.getWalkSteps();
        this.runSteps = pData.getRunSteps();
        this.distance = pData.getDistance();
        this.exerciseTime = pData.getExerciseTime();
        this.calories = Float.valueOf(formatter.format(pData.getCalories()));
        this.intensityLevel = pData.getIntensityLevel();
        this.sleepStatus = pData.getSleepStatus();
        this.exerciseAmount = Float.valueOf(formatter.format(pData.getExamount()));
        //this.measurementTime = Utils.getShortDateTimeSeconds(pData.getDate());
        this.measurementTime = pData.getUtc();
        this.receiptTime = System.currentTimeMillis() / 1000;
        //this.measureTimeUtcOffset = Utils.getShortDateOffsetMinutes(pData.getDate());
        this.measureTimeUtcOffset = Utils.getShortDateOffsetMinutes(Utils.getISO8601(pData.getUtc()));
    }
}



