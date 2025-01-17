package org.bahmni.module.pacsintegration.services;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.llp.LLPException;
import ca.uhn.hl7v2.model.AbstractMessage;
import org.bahmni.module.pacsintegration.atomfeed.contract.encounter.OpenMRSEncounter;
import org.bahmni.module.pacsintegration.atomfeed.contract.encounter.OpenMRSOrder;
import org.bahmni.module.pacsintegration.atomfeed.contract.patient.OpenMRSPatient;
import org.bahmni.module.pacsintegration.atomfeed.mappers.OpenMRSEncounterToOrderMapper;
import org.bahmni.module.pacsintegration.model.Order;
import org.bahmni.module.pacsintegration.model.OrderDetails;
import org.bahmni.module.pacsintegration.model.OrderType;
import org.bahmni.module.pacsintegration.repository.OrderDetailsRepository;
import org.bahmni.module.pacsintegration.repository.OrderRepository;
import org.bahmni.module.pacsintegration.repository.OrderTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;

@Component
public class PacsIntegrationService {

    static final Comparator<OpenMRSOrder> ORDER_COMP = 
                                        new Comparator<OpenMRSOrder>() {
            public int compare(OpenMRSOrder o1, OpenMRSOrder o2) {
                String s1 = o1.getOrderNumber();
                String s2 = o2.getOrderNumber();
                if (s1 == null && s2 == null) return 0;
                if (s1 == null) return -1;
                if (s2 == null) return 1;
                return o1.getOrderNumber().compareTo(o2.getOrderNumber());
            }
    };

    @Autowired
    private OpenMRSEncounterToOrderMapper openMRSEncounterToOrderMapper;

    @Autowired
    private OpenMRSService openMRSService;

    @Autowired
    private HL7Service hl7Service;

    @Autowired
    private OrderTypeRepository orderTypeRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDetailsRepository orderDetailsRepository;

    @Autowired
    private ModalityService modalityService;

    private static Logger logger = LoggerFactory.getLogger(PacsIntegrationService.class);

    public void processEncounter(OpenMRSEncounter openMRSEncounter) throws IOException, ParseException, HL7Exception, LLPException {
        OpenMRSPatient patient = openMRSService.getPatient(openMRSEncounter.getPatientUuid());
        List<OrderType> acceptableOrderTypes = orderTypeRepository.findAll();
        logger.info(openMRSEncounter.getOrders().size() + " orders found.");

        List<OpenMRSOrder> newAcceptableTestOrders = openMRSEncounter.getAcceptableTestOrders(acceptableOrderTypes);
        Collections.sort(newAcceptableTestOrders, ORDER_COMP);
        logger.info(newAcceptableTestOrders.size() + " orders acceptable.");
        int succesful = 0;
        for(OpenMRSOrder openMRSOrder : newAcceptableTestOrders) {
            try {
                if(orderRepository.findByOrderUuid(openMRSOrder.getUuid()) == null) {
                    AbstractMessage request = hl7Service.createMessage(openMRSOrder, patient, openMRSEncounter.getProviders());
                    String response = modalityService.sendMessage(request, openMRSOrder.getOrderType());
                    Order order = openMRSEncounterToOrderMapper.map(openMRSOrder, openMRSEncounter, acceptableOrderTypes);

                    orderRepository.save(order);
                    orderDetailsRepository.save(new OrderDetails(order, request.encode(),response));
                }
                succesful++;
            } catch( Exception e) {
                logger.warn("Failed to process order " + openMRSOrder.getOrderNumber() + " : " + e.getMessage());
            }
        }
        logger.info(succesful + " orders processed successfully.");
    }
}
