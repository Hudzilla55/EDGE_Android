package com.healthsaas.zewamedwell.zewa.dataModels;

import android.annotation.SuppressLint;

/**
 * Created by Alan on 1/4/2017.
 */

public class medWellReminder {

    public  int well;
    public int startTimeHours; // users relative time (0-23)
    public int startTimeMinutes; // users relative time (0-59)
    public long repeatInterval; // repeat every n seconds - zero (0) = no reminder
    public int advanceAfter; // advance light index (green>yellow, yellow>red) after n seconds
    public int blinkSpeed; // blink every n seconds
    public int beepInterval; // beep cycle every n seconds
    public int beepCount; // number of times to beep for each beeping cycle
    public int sortVer; // the sorting version for this reminder.


    @SuppressWarnings("unused")
    public medWellReminder() {
        this(0);
    }

    private medWellReminder(int well) {
        this(well, 86400); // default repeat is daily
    }

    public medWellReminder(int well, int repeatInterval) {
        this.well = well;
        this.startTimeHours = 0;
        this.startTimeMinutes = 0;
        this.repeatInterval = repeatInterval;
        this.advanceAfter = 1800; // default advance is 1/2 hour
        this.blinkSpeed = 2; // blink every other second
        this.beepInterval = 120; // beep every 2 minutes
        this.beepCount = 6; // beep 6 times
        this.sortVer = 0;
    }
}
