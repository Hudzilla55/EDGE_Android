package com.healthsaas.zewals.zewadb;

import com.lifesense.ble.bean.PedometerSleepData;

import java.util.List;

public class SleepData {

    private String deviceMac;
    private long utc;
    private int timeOffset;
    private String srcData;


    public SleepData(PedometerSleepData sleepData) {
        this.deviceMac = sleepData.getBroadcastId();
        this.utc=sleepData.getUtc();
        this.timeOffset=sleepData.getDeltaUtc();
        this.srcData=formatSleepStatus(sleepData.getSleeps());
    }

    public SleepData() {
    }

    public String getDeviceMac() {
        return deviceMac;
    }

    public void setDeviceMac(String deviceMac) {
        this.deviceMac = deviceMac;
    }

    public long getUtc() {
        return utc;
    }

    public void setUtc(long utc) {
        this.utc = utc;
    }

    public int getTimeOffset() {
        return timeOffset;
    }

    public void setTimeOffset(int timeOffset) {
        this.timeOffset = timeOffset;
    }

    public String getSrcData() {
        return srcData;
    }

    public void setSrcData(String srcData) {
        this.srcData = srcData;
    }

    @Override
    public String toString() {
        return "SleepData{" +
                "deviceMac='" + deviceMac + '\'' +
                ", utc=" + utc +
                ", timeOffset=" + timeOffset +
                ", srcData='" + srcData + '\'' +
                '}';
    }

    /**
     * Format Sleep Status With HexString
     * @param datas
     * @return
     */
    public static String formatSleepStatus(List<Integer> datas){
        StringBuffer strBuffer=new StringBuffer();
        for(Integer status:datas) {
            String hex = String.format("%02X", status);
            strBuffer.append(hex);
        }
        return strBuffer.toString().trim();
    }

}
