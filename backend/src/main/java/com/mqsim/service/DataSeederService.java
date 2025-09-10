package com.mqsim.service;

import com.mqsim.model.QueueConfig;
import com.mqsim.model.ResponseMapping;
import com.mqsim.repository.QueueConfigRepository;
import com.mqsim.repository.ResponseMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;

@Service
public class DataSeederService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeederService.class);

    private final QueueConfigRepository queueConfigRepository;
    private final ResponseMappingRepository responseMappingRepository;

    @Autowired
    public DataSeederService(QueueConfigRepository queueConfigRepository,
                           ResponseMappingRepository responseMappingRepository) {
        this.queueConfigRepository = queueConfigRepository;
        this.responseMappingRepository = responseMappingRepository;
    }

    @Override
    public void run(String... args) {
        seedQueueConfigurations();
        seedResponseMappings();
    }

    private void seedQueueConfigurations() {
        if (queueConfigRepository.count() > 0) {
            logger.info("Queue configurations already exist, skipping seed data");
            return;
        }

        logger.info("Seeding queue configurations...");

        // Create queue configurations
        QueueConfig q1 = new QueueConfig("SIM.REQUEST.Q1", "2-5", true);
        QueueConfig q2 = new QueueConfig("SIM.REQUEST.Q2", "1-3", true);
        QueueConfig q3 = new QueueConfig("SIM.REQUEST.Q3", "1", false);

        queueConfigRepository.save(q1);
        queueConfigRepository.save(q2);
        queueConfigRepository.save(q3);

        logger.info("Seeded {} queue configurations", 3);
    }

    private void seedResponseMappings() {
        if (responseMappingRepository.count() > 0) {
            logger.info("Response mappings already exist, skipping seed data");
            return;
        }

        logger.info("Seeding response mappings...");

        // Q1 Mappings
        ResponseMapping okMapping = createXmlMapping(
            "SIM.REQUEST.Q1", 
            "^OK-.*", 
            "<status>OK</status><timestamp>{{now}}</timestamp>", 
            120, 
            1
        );
        
        ResponseMapping errorMapping = createXmlMapping(
            "SIM.REQUEST.Q1", 
            "^ERR-.*", 
            "<error>ABEND</error><code>E001</code>", 
            null, 
            2
        );
        errorMapping.getDelay().setMode(ResponseMapping.DelayConfig.DelayMode.VARIABLE);
        errorMapping.getDelay().setVariableMinMs(300);
        errorMapping.getDelay().setVariableMaxMs(800);

        // Q2 Mappings
        ResponseMapping mfMapping = createMfMapping(
            "SIM.REQUEST.Q2", 
            "MF-1001", 
            createSampleEbcdicBase64(), 
            200, 
            1
        );

        ResponseMapping q2DefaultMapping = createXmlMapping(
            "SIM.REQUEST.Q2", 
            ".*", 
            "<response>DEFAULT</response>", 
            50, 
            5
        );

        responseMappingRepository.save(okMapping);
        responseMappingRepository.save(errorMapping);
        responseMappingRepository.save(mfMapping);
        responseMappingRepository.save(q2DefaultMapping);

        logger.info("Seeded {} response mappings", 4);
    }

    private ResponseMapping createXmlMapping(String queueName, String correlationId, String xmlBody, Integer fixedMs, int priority) {
        ResponseMapping mapping = new ResponseMapping();
        mapping.setQueueName(queueName);
        mapping.setPriority(priority);
        mapping.setEnabled(true);

        // Match criteria
        ResponseMapping.MatchCriteria match = new ResponseMapping.MatchCriteria();
        match.setCorrelationId(correlationId);
        mapping.setMatch(match);

        // Response configuration
        ResponseMapping.ResponseConfig response = new ResponseMapping.ResponseConfig();
        response.setType(ResponseMapping.ResponseConfig.ResponseType.XML);
        response.setXmlBody(xmlBody);
        response.setHeaders(Map.of("Content-Type", "application/xml"));
        mapping.setResponse(response);

        // Delay configuration
        ResponseMapping.DelayConfig delay = new ResponseMapping.DelayConfig();
        delay.setMode(ResponseMapping.DelayConfig.DelayMode.FIXED);
        delay.setFixedMs(fixedMs != null ? fixedMs : 0);
        mapping.setDelay(delay);

        return mapping;
    }

    private ResponseMapping createMfMapping(String queueName, String correlationId, String mfBodyBase64, Integer fixedMs, int priority) {
        ResponseMapping mapping = new ResponseMapping();
        mapping.setQueueName(queueName);
        mapping.setPriority(priority);
        mapping.setEnabled(true);

        // Match criteria
        ResponseMapping.MatchCriteria match = new ResponseMapping.MatchCriteria();
        match.setCorrelationId(correlationId);
        mapping.setMatch(match);

        // Response configuration
        ResponseMapping.ResponseConfig response = new ResponseMapping.ResponseConfig();
        response.setType(ResponseMapping.ResponseConfig.ResponseType.MF);
        response.setMfBodyBase64(mfBodyBase64);
        mapping.setResponse(response);

        // Delay configuration
        ResponseMapping.DelayConfig delay = new ResponseMapping.DelayConfig();
        delay.setMode(ResponseMapping.DelayConfig.DelayMode.FIXED);
        delay.setFixedMs(fixedMs != null ? fixedMs : 0);
        mapping.setDelay(delay);

        return mapping;
    }

    private String createSampleEbcdicBase64() {
        // Sample EBCDIC encoded string for "SUCCESS" in EBCDIC
        // This is a simplified example - real EBCDIC data would be more complex
        String sampleText = "SUCCESS MF RESPONSE DATA";
        byte[] asciiBytes = sampleText.getBytes();
        
        // Convert ASCII to rough EBCDIC equivalent (simplified conversion)
        byte[] ebcdicLike = new byte[asciiBytes.length];
        for (int i = 0; i < asciiBytes.length; i++) {
            // Simple ASCII to EBCDIC-like conversion for demo purposes
            ebcdicLike[i] = (byte) (asciiBytes[i] + 64);
        }
        
        return Base64.getEncoder().encodeToString(ebcdicLike);
    }
}