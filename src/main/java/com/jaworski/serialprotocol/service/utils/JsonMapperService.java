package com.jaworski.serialprotocol.service.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class JsonMapperService {

    public String toJsonString(@NonNull Object object) throws JsonProcessingException {
        JsonMapper jsonMapper = new JsonMapper();
        return jsonMapper.writeValueAsString(object);
    }
}
