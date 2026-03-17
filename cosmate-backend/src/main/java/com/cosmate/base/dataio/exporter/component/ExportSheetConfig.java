package com.cosmate.base.dataio.exporter.component;

import lombok.Builder;

import java.util.List;

@Builder
public record ExportSheetConfig<T>(
        String sheetName,
        List<ExportColumn<T>> columns
) {
}
