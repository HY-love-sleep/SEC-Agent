package com.cubigdata.controller.qry;

import java.io.Serializable;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/9/17 11:24
 */
public record ColtParam(
        Integer dataSourceId,
        String dataSourceType,
        String dataSourceName,
        String databaseCodes,
        Integer updateType,
        Integer collectType,
        String dataNumStatus,
        Integer collectTaskId) implements Serializable {
}
