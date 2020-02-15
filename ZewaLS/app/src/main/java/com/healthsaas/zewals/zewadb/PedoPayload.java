package com.healthsaas.zewals.zewadb;

import com.healthsaas.zewals.Constants;

import java.util.ArrayList;
import java.util.List;

public class PedoPayload {
    public String modelName;
    public String deviceID;
    public String manufacturer;
    public List<Float> batteryLevel;
    public List<Integer> batteryLevel2;
    public List<Integer> walkSteps;
    public List<Integer> runSteps;
    public List<Integer> distance;
    public List<Integer> exerciseTime;
    public List<Float>  calories;
    public List<Integer> intensityLevel;
    public List<Integer> sleepStatus;
    public List<Float> exerciseAmount;
    public List<Long> measurementTime;
    public List<Long> receiptTime;
    public List<Integer> measureTimeUtcOffset;

    public PedoPayload(String deviceID) {
        this.modelName = Constants.ZEWA_MODEL_ACTRKR_1;
        this.deviceID = deviceID;
        this.manufacturer = Constants.ZEWA_MFG_NAME;
        this.batteryLevel = new ArrayList<>();
        this.batteryLevel2 = new ArrayList<>();
        this.walkSteps = new ArrayList<>();
        this.runSteps = new ArrayList<>();
        this.distance = new ArrayList<>();
        this.exerciseTime = new ArrayList<>();
        this.calories = new ArrayList<>();
        this.intensityLevel = new ArrayList<>();
        this.sleepStatus = new ArrayList<>();
        this.exerciseAmount = new ArrayList<>();
        this.measurementTime = new ArrayList<>();
        this.receiptTime = new ArrayList<>();
        this.measureTimeUtcOffset = new ArrayList<>();
    }

    public void add(final PedoEntry pData) {
        this.batteryLevel.add(pData.batteryLevel);
        this.batteryLevel2.add(pData.batteryLevel2);
        this.walkSteps.add(pData.walkSteps);
        this.runSteps.add(pData.runSteps);
        this.distance.add(pData.distance);
        this.exerciseTime.add(pData.exerciseTime);
        this.calories.add(pData.calories);
        this.intensityLevel.add(pData.intensityLevel);
        this.sleepStatus.add(pData.sleepStatus);
        this.exerciseAmount.add(pData.exerciseAmount);
        this.measurementTime.add(pData.measurementTime);
        this.receiptTime.add(pData.receiptTime);
        this.measureTimeUtcOffset.add(pData.measureTimeUtcOffset);
    }
}
