package com.backendguru.recommendationservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {}
