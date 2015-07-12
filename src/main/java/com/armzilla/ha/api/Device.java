package com.armzilla.ha.api;

/**
 * Created by arm on 4/13/15.
 */
public class Device {
    private String name;
    private String deviceType;
    private String offUrl;
    private String onUrl;
    private String onMethod;
    private String offMethod;
    private String onData;
    private String offData;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getOffUrl() {
        return offUrl;
    }

    public void setOffUrl(String offUrl) {
        this.offUrl = offUrl;
    }

    public String getOnUrl() {
        return onUrl;
    }

    public void setOnUrl(String onUrl) {
        this.onUrl = onUrl;
    }

    public void setOnMethod(String onMethod) {
        this.onMethod = onMethod;
    }

    public void setOnData(String onData) {
        this.onData = onData;
    }
    
    public void setOffMethod(String offMethod) {
        this.offMethod = offMethod;
    }
    
    public void setOffData(String offData) {
        this.offData = offData;
    }
    
    public String getOnMethod() {
        return this.onMethod;
    }
    
    public String getOnData() {
        return this.onData;
    }

    public String getOffMethod() {
        return this.offMethod;
    }

    public String getOffData() {
        return this.offData;
    }

}
