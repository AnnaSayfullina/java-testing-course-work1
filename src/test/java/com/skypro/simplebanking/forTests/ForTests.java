package com.skypro.simplebanking.forTests;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class ForTests {


    public static JSONObject createUser() throws JSONException {
        JSONObject jsonUser = new JSONObject();
        jsonUser.put("id", "1");
        jsonUser.put("username", "Anna");
        jsonUser.put("password", "Anna123");
        return jsonUser;
    }



    public static String getAuthenticationHeader(String username, String password) {

        String encoding = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoding;

    }

}
