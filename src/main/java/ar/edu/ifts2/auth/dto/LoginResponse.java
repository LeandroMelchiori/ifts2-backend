package ar.edu.ifts2.auth.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
    @Override
    public String toString() {
        return "LoginResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
