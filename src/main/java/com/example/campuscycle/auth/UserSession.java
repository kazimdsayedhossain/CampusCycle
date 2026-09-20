package com.example.campuscycle.auth;

public class UserSession {

    private final String userId;
    private final String email;
    private final String role;
    private final String accessToken;

    private static UserSession instance;

    private UserSession(String userId, String email, String role, String accessToken)
    {
        this.userId=userId;
        this.email=email;
        this.role=role;
        this.accessToken=accessToken;
    }

    public static void setSession(String userId, String email, String role, String accessToken){
        instance = new UserSession(userId, email, role, accessToken);
    }

    public static UserSession getInstance() {
        return instance;
    }

    public void clear()
    {
        instance=null;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getEmail() {
        return email;
    }

    public String getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public boolean isAdmin()
    {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
