package com.healthsaas.zewapulseox.zewa.pulseox;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;


public class rawEvents {
	private ArrayList<Integer> pulse = new ArrayList<>();
	private ArrayList<Long> eventTimeStamp = new ArrayList<>();
	private ArrayList<Integer> oxygen = new ArrayList<>();
	private ArrayList<Integer> pi = new ArrayList<>();

	private int eventCount;
	
	public void clearEvents() {
		pulse.clear();
		eventTimeStamp.clear();
		oxygen.clear();
		pi.clear();
		eventCount = 0;
	}
	
	public void createEventAndInsert(int pulse,  long timeStamp, int oxygen, int pi) {
	    this.pulse.add(pulse);
	    this.eventTimeStamp.add(timeStamp);
	    this.oxygen.add(oxygen);
	    this.pi.add(pi);
	    eventCount++;
	}

	public int getEventCount() {
		return eventCount;
	}
	
	public long getEventTimeStamp(int index) {
		if(index < eventCount) 
			return eventTimeStamp.get(index);
		else
			return 0;
	}
	public ArrayList<Long> getEventTimeStamps() {
		return this.eventTimeStamp;
	}

	public int getOxygenData(int index) {
		if(index < eventCount)
			return oxygen.get(index);
		else
			return 0;
	}
	public ArrayList<Integer> getOxygens() { return this.oxygen; }

	public int getPulseData(int index) {
		if(index < eventCount)
			return pulse.get(index);
		else
			return 0;
	}
	public ArrayList<Integer> getPulses() { return this.pulse; }

	public int getPIData(int index) {
		if(index < eventCount)
			return pi.get(index);
		else
			return 0;
	}
	public ArrayList<Integer> getPIs() { return this.pi; }

}


