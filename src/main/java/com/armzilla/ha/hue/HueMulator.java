package com.armzilla.ha.hue;

import com.armzilla.ha.api.hue.DeviceResponse;
import com.armzilla.ha.api.hue.DeviceState;
import com.armzilla.ha.api.hue.HueApiResponse;
import com.armzilla.ha.dao.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by arm on 4/12/15.
 */
@Controller
@RequestMapping("/api")
public class HueMulator {
    private static final Logger log = Logger.getLogger(HueMulator.class);
    private static final String INTENSITY_PERCENT = "${intensity.percent}";
    private static final String INTENSITY_BYTE = "${intensity.byte}";
    @Autowired
    private DeviceRepository repository;
    private HttpClient httpClient;
    private ObjectMapper mapper;


    public HueMulator(){
        httpClient = HttpClients.createDefault(); //patched for now, moving away from HueMulator doing work
        mapper = new ObjectMapper(); //work around Echo incorrect content type and breaking mapping. Map manually
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }


    @RequestMapping(value = "/{userId}/lights", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<Map<String, String>> getUpnpConfiguration(@PathVariable(value = "userId") String userId, HttpServletRequest request) {
        log.info("hue lights list requested: " + userId + " from " + request.getRemoteAddr());
        Page<DeviceDescriptor> deviceList = repository.findByDeviceType("switch", new PageRequest(0,100));
        Map<String, String> deviceResponseMap = new HashMap<>();
        for (DeviceDescriptor device : deviceList) {
            deviceResponseMap.put(device.getId(), device.getName());
        }
        return new ResponseEntity<>(deviceResponseMap, null, HttpStatus.OK);
    }

    @RequestMapping(value = "/{userId}", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<HueApiResponse> getApi(@PathVariable(value = "userId") String userId, HttpServletRequest request) {
        log.info("hue api root requested: " + userId + " from " + request.getRemoteAddr());
        Page<DeviceDescriptor> descriptorList = repository.findByDeviceType("switch", new PageRequest(0, 100));
        if (descriptorList == null) {
            return new ResponseEntity<>(null, null, HttpStatus.NOT_FOUND);
        }
        Map<String, DeviceResponse> deviceList = new HashMap<>();

        descriptorList.forEach(descriptor -> {
                    DeviceResponse deviceResponse = DeviceResponse.createResponse(descriptor.getName(), descriptor.getId());
                    deviceList.put(descriptor.getId(), deviceResponse);
                }
        );
        HueApiResponse apiResponse = new HueApiResponse();
        apiResponse.setLights(deviceList);

        HttpHeaders headerMap = new HttpHeaders();
        ResponseEntity<HueApiResponse> entity = new ResponseEntity<>(apiResponse, headerMap, HttpStatus.OK);
        return entity;
    }

    @RequestMapping(value = "/{userId}/lights/{lightId}", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<DeviceResponse> getLigth(@PathVariable(value = "lightId") String lightId, @PathVariable(value = "userId") String userId, HttpServletRequest request) {
        log.info("hue light requested: " + lightId + " from " + request.getRemoteAddr());
        DeviceDescriptor device = repository.findOne(lightId);
        if (device == null) {
            return new ResponseEntity<>(null, null, HttpStatus.NOT_FOUND);
        } else {
            log.info("found device named: " + device.getName());
        }
        DeviceResponse lightResponse = DeviceResponse.createResponse(device.getName(), device.getId());

        HttpHeaders headerMap = new HttpHeaders();

        ResponseEntity<DeviceResponse> entity = new ResponseEntity<>(lightResponse, headerMap, HttpStatus.OK);
        return entity;
    }

    @RequestMapping(value = "/{userId}/lights/{lightId}/state", method = RequestMethod.PUT)
    public ResponseEntity<String> stateChange(@PathVariable(value = "lightId") String lightId, @PathVariable(value = "userId") String userId, HttpServletRequest request, @RequestBody String requestString) {
        /**
         * strangely enough the Echo sends a content type of application/x-www-form-urlencoded even though
         * it sends a json object
         */
        log.info("hue state change requested: " + userId + " from " + request.getRemoteAddr());
        log.info("hue stage change body: " + requestString );

        DeviceState state = null;
        try {
            state = mapper.readValue(requestString, DeviceState.class);
        } catch (IOException e) {
            log.info("object mapper barfed on input", e);
            return new ResponseEntity<>(null, null, HttpStatus.BAD_REQUEST);
        }

        DeviceDescriptor device = repository.findOne(lightId);
        if (device == null) {
            return new ResponseEntity<>(null, null, HttpStatus.NOT_FOUND);
        }

        String responseString;
        String url;
        String method;
        String data;
        if (state.isOn()) {
            responseString = "[{\"success\":{\"/lights/" + lightId + "/state/on\":true}}]";
            url = device.getOnUrl();
            method = device.getOnMethod();
            data = device.getOnData();
        } else {
            responseString = "[{\"success\":{\"/lights/" + lightId + "/state/on\":false}}]";
            url = device.getOffUrl();
            method = device.getOffMethod();
            data = device.getOffData();
        }

        /* light weight templating here, was going to use free marker but it was a bit too
        *  heavy for what we were trying to do.
        *
        *  currently provides only two variables:
        *  intensity.byte : 0-255 brightness.  this is raw from the echo
        *  intensity.percent : 0-100, adjusted for the vera
        */
        if(url.contains(INTENSITY_BYTE)){
            String intensityByte = String.valueOf(state.getBri());
            url = url.replace(INTENSITY_BYTE, intensityByte);
        }else if(url.contains(INTENSITY_PERCENT)){
            int percentBrightness = (int) Math.round(state.getBri()/255.0*100);
            String intensityPercent = String.valueOf(percentBrightness);
            url = url.replace(INTENSITY_PERCENT, intensityPercent);
        }

        //make call
        if(method.equals("GET")) {
            if(!doHttpGETRequest(url)){
                log.error("GET request failed");
                return new ResponseEntity<>(null, null, HttpStatus.SERVICE_UNAVAILABLE);
            }
        }
        else if(method.equals("PUT")) {
            if(!doHttpPUTRequest(url,data)){
                log.error("PUT request failed");
                return new ResponseEntity<>(null, null, HttpStatus.SERVICE_UNAVAILABLE);
            }
        }
        else if(method.equals("POST")) {
            if(!doHttpPOSTRequest(url,data)){
               log.error("POST request failed");
               return new ResponseEntity<>(null, null, HttpStatus.SERVICE_UNAVAILABLE);
            }
        }
        HttpHeaders headerMap = new HttpHeaders();

        ResponseEntity<String> entity = new ResponseEntity<>(responseString, headerMap, HttpStatus.OK);
        return entity;
    }


    protected boolean doHttpPUTRequest(String url, String data) {
        log.info("calling PUT on URL: " + url);
        log.info("DATA: " + data);
        HttpPut httpPut = new HttpPut(url);
        try {
            StringEntity params = new StringEntity(data);
            httpPut.addHeader("content-type", "application/x-www-form-urlencoded");
            httpPut.setEntity(params);
            HttpResponse response = httpClient.execute(httpPut);
            String body = EntityUtils.toString(response.getEntity()); //close out inputstream ignore content
            log.info("PUT on URL responded: " + body);
            if(response.getStatusLine().getStatusCode() == 200){
                return true;
            }
        } catch (IOException e) {
            log.error("Error calling out to HA gateway", e);
        }
        return false;
    }


    protected boolean doHttpPOSTRequest(String url, String data) {
        log.info("calling POST on URL: " + url);
        log.info("POSTDATA: " + data);
        HttpPost httpPost = new HttpPost(url);
        try {
            StringEntity params = new StringEntity(data);
            httpPost.addHeader("content-type", "application/x-www-form-urlencoded");
            httpPost.setEntity(params);
            HttpResponse response = httpClient.execute(httpPost);
            EntityUtils.consume(response.getEntity()); //close out inputstream ignore content
            log.info("POST on URL responded: " + response.getStatusLine().getStatusCode());
            if(response.getStatusLine().getStatusCode() == 200){
                return true;
            }
        } catch (IOException e) {
            log.error("Error calling out to HA gateway", e);
        }
        return false;
    }

    protected boolean doHttpGETRequest(String url) {
        log.info("calling GET on URL: " + url);
        HttpGet httpGet = new HttpGet(url);
        try {
            HttpResponse response = httpClient.execute(httpGet);
            EntityUtils.consume(response.getEntity()); //close out inputstream ignore content
            log.info("GET on URL responded: " + response.getStatusLine().getStatusCode());
            if(response.getStatusLine().getStatusCode() == 200){
                return true;
            }
        } catch (IOException e) {
            log.error("Error calling out to HA gateway", e);
        }
        return false;
    }
}
