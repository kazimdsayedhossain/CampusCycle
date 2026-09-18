package com.example.campuscycle.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AuthService {
    private static final String SUPABASE_URL= "https://wqybuukgcwhcwiwfffda.supabase.co";
    private static final String SuPABASE_PUB_KEY= "sb_publishable_Hj5sFSnjDqdLBJCeFSskzA_teyYoUIp";
    public static String currentToken=null;

    private static final HttpClient client = HttpClient.newHttpClient();


        public static String login (String email, String password){
            try {
        String endpoint = SUPABASE_URL + "/auth/v1/token?grant_type=password";
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("email", email);
        requestJson.addProperty("password", password);
        String requestBody = requestJson.toString();

        HttpRequest.Builder builder = HttpRequest.newBuilder();

        URI url = URI.create(endpoint);
        builder.uri(url);

        builder.header("Content-Type", "application/json");
        builder.header("apikey", SuPABASE_PUB_KEY);

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(requestBody);
        builder.POST(bodyPublisher);

        HttpRequest request = builder.build();

        HttpResponse.BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString();

        HttpResponse<String> response = client.send(request, bodyHandler);

        if (response.statusCode() == 200) {

            String rawText = response.body();
            JsonElement jsonElement = JsonParser.parseString(rawText);
            JsonObject success_response_obj = jsonElement.getAsJsonObject();
            currentToken = success_response_obj.get("access_token").getAsString();
            return "Success";
        }

        else {
            String rawJsonText = response.body();
            JsonElement jsonElement = JsonParser.parseString(rawJsonText);
            JsonObject errorJson = jsonElement.getAsJsonObject();

            if (errorJson.has("error_description")) {
                return errorJson.get("error_description").getAsString();
            } else if (errorJson.has("msg")) {
                return errorJson.get("msg").getAsString();
            }

            return "Login failed with status code: " + response.statusCode();
        }

    } catch(Exception e)
        {
            return "Connection error " + e.getMessage();
        }
    }

}
