package com.jaworski.serialprotocol.authorization;

import com.jaworski.serialprotocol.dto.User;
import com.jaworski.serialprotocol.exception.CustomRestException;
import com.jaworski.serialprotocol.resources.Resources;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class UserService {

    private final Resources resources;

    public User getUser() throws CustomRestException {
        String restServiceCredentials = resources.getRestServiceCredentials();
        String[] split = StringUtils.split(restServiceCredentials, ":");
        if (split.length == 2) {
            return new User(split[0], split[1]);
        } else {
            throw new CustomRestException("Invalid credentials format: " + restServiceCredentials);
        }
    }

}
