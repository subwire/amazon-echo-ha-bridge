package com.armzilla.ha.devicemanagmeent;

import com.armzilla.ha.api.Device;
import com.armzilla.ha.api.HADevice;
import com.armzilla.ha.api.HAGateway;
import com.armzilla.ha.dao.*;
import com.armzilla.ha.gateway.VeraGatewayImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Created by arm on 4/21/15.
 */
@Controller
@RequestMapping("/api/gateways")
public class GatewayResource {

    @Autowired
    private HAGatewayRepository gatewayRepository;

    @RequestMapping(method = RequestMethod.POST, produces = "application/json", headers = "Accept=application/json")
    public ResponseEntity<HAGatewayDao> createGateway(@RequestBody HAGateway gateway) {
        HAGatewayDao gatewayEntry = new HAGatewayDao();
        gatewayEntry.setId(UUID.randomUUID().toString());
        gatewayEntry.setName(gateway.getName());
        gatewayEntry.setAddress(gateway.getAddress());
        gatewayEntry.setType(gateway.getType());

        gatewayRepository.save(gatewayEntry);

        return new ResponseEntity<>(gatewayEntry, null, HttpStatus.CREATED);
    }

    @RequestMapping(method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<List<HAGatewayDao>> findAllGateways() {
        List<HAGatewayDao> gatewayList = gatewayRepository.findAll();
        List<HAGatewayDao> plainList = new LinkedList<>(gatewayList);
        return new ResponseEntity<>(plainList, null, HttpStatus.OK);
    }

    @RequestMapping(value = "/{gatewayId}", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<HAGatewayDao> findByDevicId(@PathVariable("gatewayId") String id){
        HAGatewayDao descriptor = gatewayRepository.findOne(id);
        if(descriptor == null){
            return new ResponseEntity<>(null, null, HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(descriptor, null, HttpStatus.OK);
    }

    @RequestMapping(value = "/{gatewayId}/_veralist", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<List<HADevice>> availableDevices(@PathVariable("gatewayId") String id){
        HAGatewayDao descriptor = gatewayRepository.findOne(id);
        if(descriptor == null){
            return new ResponseEntity<>(null, null, HttpStatus.NOT_FOUND);
        }
        VeraGatewayImpl gatewayImpl = new VeraGatewayImpl(descriptor.getAddress());
        try {
            List<HADevice> deviceList = gatewayImpl.getAllDevices();
            return new ResponseEntity<>(deviceList, null, HttpStatus.OK);

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return new ResponseEntity<>(null, null, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @RequestMapping(value = "/{gatewayId}", method = RequestMethod.DELETE, produces = "application/json")
    public ResponseEntity<String> deleteGatewayById(@PathVariable("gatewayId") String id){
        HAGatewayDao deleted = gatewayRepository.findOne(id);
        if(deleted == null){
            return new ResponseEntity<>(null, null, HttpStatus.NOT_FOUND);
        }
        gatewayRepository.delete(deleted);
        return new ResponseEntity<>(null, null, HttpStatus.OK);
    }

}
