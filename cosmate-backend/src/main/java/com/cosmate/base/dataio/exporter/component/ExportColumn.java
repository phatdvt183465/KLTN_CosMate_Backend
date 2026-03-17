package com.cosmate.base.dataio.exporter.component;

import lombok.Builder;

import java.util.function.Function;

@Builder
public record ExportColumn<T>(
        String header,
        Function<T, Object> valueExtractor
) {
}

