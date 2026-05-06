package com.jaworski.serialprotocol.service.utils;

import jakarta.annotation.Nonnull;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;

@Service
public class JsonMapperService {

    public String toJsonString(@Nonnull Object object) throws JacksonException {
        JsonMapper jsonMapper = new JsonMapper();
        return jsonMapper.writeValueAsString(object);
    }
}
