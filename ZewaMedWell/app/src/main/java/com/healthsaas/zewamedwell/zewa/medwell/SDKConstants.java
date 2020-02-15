package com.healthsaas.zewamedwell.zewa.medwell;



public class SDKConstants {


	public static final String MEDWELL_SERVICE_UUID 			= "abcdef00-1234-4679-0abc-0100500abcde";
	public static final String MEDWELL_CLOCK_UUID				= "abcdef01-1234-4679-0abc-0100500abcde"; // Event Log UTC - read / set clock
	public static final String MEDWELL_TEST_PROGRAM_UUID	 	= "abcdef02-1234-4679-0abc-0100500abcde"; // Test Program - only used for testing & programming.
	public static final String MEDWELL_READ_BLOCK_INDEX_UUID 	= "abcdef03-1234-4679-0abc-0100500abcde"; // Log Read Index - set index, value read from char 04.
	public static final String MEDWELL_BLOCK_UUID 				= "abcdef04-1234-4679-0abc-0100500abcde"; // Log Read Data (write = program reminders - see 9/9 document page 8) - see 8/15 document page 14
	public static final String MEDWELL_STATUS_UUID 			= "abcdef05-1234-4679-0abc-0100500abcde"; // Last Log Data = current status - next log index, and timestamp/value of last event.
	public static final String MEDWELL_WRITE_UUID 				= "abcdef06-1234-4679-0abc-0100500abcde"; // Log Write Data - read cleartext, write encrypted value using shared secret.

	public static final byte[] MEDWELL_TEST_PROGRAM_LED		= {0x40, 0x4C, 0x45, 0x44}; // @LED = makes all LED's blink in sequence
	public static final byte[] MEDWELL_TEST_PROGRAM_BUZZ		= {0x40, 0x42, 0x5A, 0x2E}; // @BZ. = fires the buzzer once.
	public static final byte[] MEDWELL_TEST_PROGRAM_KEY		= {0x40, 0x4b, 0x45, 0x59}; // @KEY = read the current switch (well) states
	public static final byte[] MEDWELL_TEST_PROGRAM_DFU		= {0x40, 0x44, 0x46, 0x55}; // @DFU = Place device into OTA-DFU mode
	public static final byte[] MEDWELL_TEST_PROGRAM_REC		= {0x40, 0x52, 0x45, 0x43}; // @REC = check nvram function
	public static final byte[] MEDWELL_TEST_PROGRAM_OFF		= {0x40, 0x4f, 0x46, 0x46}; // @OFF = reset the device after firmware upgrade @DFU
	public static final byte[] MEDWELL_TEST_PROGRAM_GR1		= {0x40, 0x47, 0x51, 0x31}; // @GR1 = get reminder 1
	public static final byte[] MEDWELL_TEST_PROGRAM_GR2		= {0x40, 0x47, 0x51, 0x32}; // @GR2 = get reminder 2
	public static final byte[] MEDWELL_TEST_PROGRAM_GR3		= {0x40, 0x47, 0x51, 0x33}; // @GR3 = get reminder 3
	public static final byte[] MEDWELL_TEST_PROGRAM_GR4		= {0x40, 0x47, 0x51, 0x34}; // @GR4 = get reminder 4
	public static final byte[] MEDWELL_TEST_PROGRAM_GR5		= {0x40, 0x47, 0x51, 0x35}; // @GR5 = get reminder 5
}
