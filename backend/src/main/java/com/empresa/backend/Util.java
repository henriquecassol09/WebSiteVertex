package com.empresa.backend;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

@Documented
@Constraint(validatedBy = SenhaForteValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@interface SenhaForte {
    String message() default "A senha deve ter no minimo 10 caracteres, incluindo letra maiuscula, minuscula, numero e caractere especial";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

class SenhaForteValidator implements ConstraintValidator<SenhaForte, String> {

    private static final Pattern MAIUSCULA = Pattern.compile("[A-Z]");
    private static final Pattern MINUSCULA = Pattern.compile("[a-z]");
    private static final Pattern NUMERO = Pattern.compile("[0-9]");
    private static final Pattern ESPECIAL = Pattern.compile("[^A-Za-z0-9]");

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext context) {
        if (senha == null || senha.length() < 10 || senha.length() > 128) return false;
        return MAIUSCULA.matcher(senha).find()
                && MINUSCULA.matcher(senha).find()
                && NUMERO.matcher(senha).find()
                && ESPECIAL.matcher(senha).find();
    }
}

/**
 * Geracao de tokens opacos (refresh tokens) e hashing SHA-256 para
 * persistencia — o valor em texto puro nunca e gravado no banco, apenas
 * devolvido uma unica vez ao cliente.
 */
final class HashUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private HashUtil() {}

    static String gerarTokenOpaco() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
