package com.healthsaas.zewamedwell.zewa.medwell;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;


public class rawEvents {
	private ArrayList<Integer> eventIndex = new ArrayList<Integer>();
	private ArrayList<Long> eventTimeStamp = new ArrayList<Long>();
	private ArrayList<Integer> eventData = new ArrayList<Integer>();

	private int eventCount;
	
	public void clearEvents() {
		eventIndex.clear();
		eventTimeStamp.clear();
		eventData.clear();
		eventCount = 0;
	}
	
	public void createEventAndInsert(int index,  long timeStamp, int data) {
	    eventIndex.add(0, Integer.valueOf(index));
	    eventTimeStamp.add(0, Long.valueOf(timeStamp));
	    eventData.add(0, Integer.valueOf(data));
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

	public int getEventData(int index) {
		if(index < eventCount)
			return eventData.get(index);
		else
			return 0;
	}

	public int getEventIndex(int index) {
		if(index < eventCount)
			return eventIndex.get(index);
		else
			return 0;
	}

	public String writeDebugDataToString() {
    	StringBuilder workingStr = new StringBuilder();
    	
		for(int i=0; i< eventCount; i++) {	
	    	workingStr.append(String.format("%4d, %s 0x%08x, 0x%08x ", eventIndex.get(i), getISO8601(eventTimeStamp.get(i)), eventTimeStamp.get(i), eventData.get(i)));
	        
	        if(eventData.get(i) == 0xFFFFFFFF && eventTimeStamp.get(i) == 0xFFFFFFFFL) {
	            workingStr.append("blank");
	        }
	        else if(eventData.get(i) == 0xA000) {
	            workingStr.append("reset");
	        }
	        else if(eventData.get(i) == 0x9000) {
	            workingStr.append("sync");
	        }
	        else {
	        	workingStr.append("open ");
	            for(int j=0; j<6; j++) {
	                if((( eventData.get(i) >> j) & 0x1) == 0x0) {
	                    workingStr.append(String.format(" %1d",j+1));
	                }
	                else {
	                	workingStr.append("  ");
	                }
	            }
	        }
	        workingStr.append("\n");
		}
	    return workingStr.toString();
	}

	private String getISO8601(long timeStamp) {
		String resp = "";
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
		resp = sdf.format(new Date(timeStamp * 1000));
		return resp;
	}
}


