package com.portfolio.rag.document.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Serializes float[] embeddings to the pgvector text format "[v1,v2,...]".
 * Requires the JDBC connection property {@code stringtype=unspecified} so
 * PostgreSQL implicitly casts the bound string to vector (set in application.yml).
 */
@Converter
public class VectorConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(attribute.length * 12).append('[');
        for (int i = 0; i < attribute.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(attribute[i]);
        }
        return sb.append(']').toString();
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        String body = dbData.trim();
        if (body.startsWith("[")) {
            body = body.substring(1, body.length() - 1);
        }
        if (body.isBlank()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
