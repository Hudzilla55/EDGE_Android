package com.healthsaas.zewapulseox.zewa.ble;

import android.util.Log;


public class BLEDeviceBlocks {
	private final static String TAG = BLEDeviceBlocks.class.getSimpleName();
	
	public int length;
	private byte [] deviceMemory;
	private boolean [] blockFlags;
	
	BLEDeviceBlocks() {
		length = 2000;
		deviceMemory = new byte[length * 20];
		blockFlags = new boolean[length];
		
		clearBlocks();
	}
	
	public void clearBlocks() { 
		Log.i(TAG, "Initializing deviceMemory");
		for(int i=0; i<deviceMemory.length; i++) {
			deviceMemory[i] = (byte) 0x00;
		}
		
		for(int i=0; i<blockFlags.length; i++) {
			blockFlags[i] = false;
		}
		
		Log.i(TAG, "deviceMemory initialized");		
	}

	public boolean getBlockFlag(int index) {
		if(index < blockFlags.length)
			return blockFlags[index];
		else
			return false;
	}
	
	public void setBlockFlag(int index, boolean flag) {
		if(index < blockFlags.length)
			blockFlags[index] = flag;
	}
	
	public void copyData(int index, byte [] data) {
        for(int i=0; i<20; i++) {
        	if(i < data.length && index*20+i < deviceMemory.length)
        		deviceMemory[index*20+i] = data[i];
        }
	}
	
	public byte [] getData(int index) {
		byte [] outData = new byte[20];

		if(index<length) {
	        for(int i=0; i<20; i++) {
	        	outData[i] = deviceMemory[index*20+i];
	        }
	        return outData;
		}
		else {
			return null;
		}
	}	
}
