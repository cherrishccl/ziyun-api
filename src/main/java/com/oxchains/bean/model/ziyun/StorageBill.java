package com.oxchains.bean.model.ziyun;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oxchains.common.BaseEntity;

/**
 * Created by root on 17-7-3.
 * 仓储单
 */
public class StorageBill extends BaseEntity {
    @JsonProperty("id")
    private String id;

    @JsonProperty("StorageTitle")
    private String StorageTitle;//贮存标题

    @JsonProperty("WarehouseName")
    private String WarehouseName;//仓库名字

    @JsonProperty("SalesId")
    private String SalesId;//生产销售id

    @JsonProperty("TransportId")
    private String TransportId;//运输id

    @JsonProperty("GiverName")
    private String GiverName;//转交人名字

    @JsonProperty("GiverPhone")
    private String GiverPhone;//转交人电话

    @JsonProperty("RecipientName")
    private String RecipientName;//接收人名字

    @JsonProperty("RecipientPhone")
    private String RecipientPhone;//接收人电话

    @JsonProperty("StartTime")
    private long StartTime;//存储开始时间 时间戳

    @JsonProperty("EndTime")
    private long EndTime;//存储结束时间 时间戳

    @JsonProperty("StorageAddress")
    private String StorageAddress;//存储地址

    @JsonProperty("HandoverInfo")
    private String HandoverInfo;//双方交接情况
}
