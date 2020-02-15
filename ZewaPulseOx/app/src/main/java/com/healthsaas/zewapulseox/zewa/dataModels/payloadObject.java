package com.healthsaas.zewapulseox.zewa.dataModels;


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
        public String BPM;
        public String sn;
        public String PI;
        public String spo2;
        public String man;
        public String mt;
        public String rt;
    }

    public class Hi
    {
        public String IMEI;
        public String SN;
    }
}
