package com.racehorse.training.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

@Converter
public class LocalDateListJsonConverter implements AttributeConverter<List<LocalDate>, String> {
    public String convertToDatabaseColumn(List<LocalDate> value) {
        return (value == null ? List.<LocalDate>of() : value).stream().map(day -> "\"" + day + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
    public List<LocalDate> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank() || value.equals("[]")) return List.of();
        String content = value.trim().replaceAll("^\\[|\\]$", "").replace("\"", "");
        return Stream.of(content.split(",")).filter(part -> !part.isBlank()).map(String::trim).map(LocalDate::parse).toList();
    }
}
