package com.neu.bigdata.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Ruolin Li
 * @DATE 2023-10-08
 */
@Component
public class JwtTokenUtil {

    public static final long JWT_TOKEN_VALIDITY = 5 * 60 * 60;

    private final KeyPair keyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);

    public String generateToken() {
        Map<String, Object> claims = new HashMap<>();

        return Jwts.builder().setClaims(claims)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 60 * 60 * 1000))
            .signWith(SignatureAlgorithm.RS256, keyPair.getPrivate())
            .compact();
    }
    /**
     * Retrieving information from token with the public key
     */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser().setSigningKey(keyPair.getPublic()).parseClaimsJws(token).getBody();
    }
    /**
     * Date retrieve expiration date from jwt token
     */
    public Date getExpirationDateFromToken(String token) {
        return getAllClaimsFromToken(token).getExpiration();
    }

    /**
     * @return true for expired; otherwise false
     */
    public boolean isTokenExpired(String token) {
        Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    /**
     * validate is token expired
     * @return true for valid; otherwise false
     */
    public boolean validateToken(String token) {
        return !isTokenExpired(token);
    }
}
