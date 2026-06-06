package com.garment.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    // ... existing code ...

        /**
         * 生成JWT访问令牌
         *
         * @param userId 用户ID，作为token的主题标识
         * @param username 用户名，存储在token的自定义claim中
         * @return 签名后的JWT token字符串
         */
        public String generateToken(String userId, String username) {
            Date now = new Date();
            Date expireDate = new Date(now.getTime() + expiration);

            return Jwts.builder()
                    .setSubject(userId)
                    .claim("username", username)
                    .setIssuedAt(now)
                    .setExpiration(expireDate)
                    .signWith(SignatureAlgorithm.HS512, secret)
                    .compact();
        }

    // ... existing code ...

    // ... existing code ...

        /**
         * 解析JWT令牌并提取声明信息
         *
         * @param token JWT token字符串
         * @return Claims对象，包含token中的所有声明信息
         */
        public Claims parseToken(String token) {
            return Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token)
                    .getBody();
        }

    // ... existing code ...

    // ... existing code ...

        /**
         * 验证JWT令牌的有效性
         *
         * @param token JWT token字符串
         * @return token有效返回true，过期或无效返回false
         */
        public boolean validateToken(String token) {
            try {
                Claims claims = parseToken(token);
                return !claims.getExpiration().before(new Date());
            } catch (Exception e) {
                return false;
            }
        }

    // ... existing code ...

    // ... existing code ...

        /**
         * 从JWT令牌中提取用户ID
         *
         * @param token JWT token字符串
         * @return 用户ID
         */
        public String getUserIdFromToken(String token) {
            Claims claims = parseToken(token);
            return claims.getSubject();
        }

    // ... existing code ...

    // ... existing code ...

        /**
         * 从JWT令牌中提取用户名
         *
         * @param token JWT token字符串
         * @return 用户名
         */
        public String getUsernameFromToken(String token) {
            Claims claims = parseToken(token);
            return (String) claims.get("username");
        }

    // ... existing code ...
}
