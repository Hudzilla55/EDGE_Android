package com.healthsaas.feverscout.dataModels;


public class payloadObject {
    public D d;
    public Hi hi;
    public String dt;

    public payloadObject() {
        this.d = new D();
        this.hi = new Hi();
        this.dt = "";
    }
    public class D
    {
        public String mod;
        public String u;
        public String bl;
        public String sn;
        public String t;
        public String tr;
        public String to;
        public String man;
        public String mt;
        //public String rt; // always same as measure time.
        public String rssi;
        public String bs;
    }

    public class Hi
    {
        public String IMEI;
        public String SN;
        public String bl;
    }
}
