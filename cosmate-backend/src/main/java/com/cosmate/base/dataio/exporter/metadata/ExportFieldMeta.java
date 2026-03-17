package com.cosmate.base.dataio.exporter.metadata;

import lombok.Builder;

import java.lang.reflect.Field;

@Builder
public record ExportFieldMeta(
        String header,
        Field field,
        String dateFormat,
        String path,
        boolean relation,
        String separator
) {
}
