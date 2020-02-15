package com.healthsaas.zewals.dataModels;

public class ACTPayloadObject {
    public D d;
    public Hi hi;
    public String dt;

    public  ACTPayloadObject() {
        this.d = new D();
        this.hi = new Hi();
        this.dt = "";
    }
    public class D
    {
        public String mod;
        public String et;
        public String sn;
        public String rs;
        public String ws;
        public String tzo;
        public String sl;
        public String man;
        public String mt;
        public String il;
        public String rt;
        public String ca;
        public String di;
        public String b;
        public String b2;
        public String ea;
    }

    public class Hi
    {
        public String IMEI;
        public String SN;
    }
}
