package com.healthsaas.zewamedwell.zewa.dataModels;

public class payloadObject {

    public  payloadObject() {
        this.d = new D();
        this.hi = new Hi();
    }
    public D d;
    public Hi hi;
    public String dt;

    public class D
    {
        public String sn;
        public String mod;
        public String ev;
        public String ba;
        public String man;
        public String mwt;
        public String rt;
    }

    public class Hi
    {
        public String IMEI;
        public String SN;
    }

}
