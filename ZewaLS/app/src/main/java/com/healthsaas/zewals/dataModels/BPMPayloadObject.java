package com.healthsaas.zewals.dataModels;

public class BPMPayloadObject {
    public D d;
    public Hi hi;
    public String dt;

    public BPMPayloadObject() {
        this.d = new D();
        this.hi = new Hi();
        this.dt = "";
    }
    public class D
    {
        public String mod;
        public String u;
        public String sys;
        public String p;
        public String sn;
        public String dia;
        public String man;
        public String mt;
        public String rt;
        public String ba;
        public String map;
    }

    public class Hi
    {
        public String IMEI;
        public String SN;
    }
}
