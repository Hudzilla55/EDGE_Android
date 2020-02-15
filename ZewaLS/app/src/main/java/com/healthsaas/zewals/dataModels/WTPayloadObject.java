package com.healthsaas.zewals.dataModels;

public class WTPayloadObject {
    public D d;
    public Hi hi;
    public String dt;

    public WTPayloadObject() {
        this.d = new D();
        this.hi = new Hi();
        this.dt = "";
    }
    public class D
    {
        public String mod;
        public String u;
        public String wt;
        public String sn;
        public String Kg;
        public String man;
        public String mt;
        public String rt;
        public String ba;
        public String r2;
    }

    public class Hi
    {
        public String IMEI;
        public String SN;
    }
}
