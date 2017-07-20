package com.oxchains.service;

import com.oxchains.bean.dto.PurchaseInfoDTO;
import com.oxchains.bean.dto.SalesInfoDTO;
import com.oxchains.bean.model.ziyun.Auth;
import com.oxchains.bean.model.ziyun.JwtToken;
import com.oxchains.bean.model.ziyun.PurchaseInfo;
import com.oxchains.bean.model.ziyun.SalesInfo;
import com.oxchains.common.ChaincodeResp;
import com.oxchains.common.ConstantsData;
import com.oxchains.common.RespDTO;
import com.oxchains.dao.ChaincodeData;
import com.oxchains.util.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by root on 17-7-5.
 */
@Service
@Slf4j
public class SalesInfoService extends BaseService  {
    @Resource
    private ChaincodeData chaincodeData;

    public RespDTO<String> addSalesInfo(SalesInfo salesInfo) throws Exception{
        String token = salesInfo.getToken();
        JwtToken jwt = TokenUtils.parseToken(token);
        salesInfo.setToken(jwt.getId());// store username ,not token
        String txID = chaincodeData.invoke("saveSalesInfo", new String[] { gson.toJson(salesInfo) }).filter(ChaincodeResp::succeeded).map(ChaincodeResp::getPayload).orElse(null);
        log.debug("===txID==="+txID);
        if(txID == null){
            return RespDTO.fail("操作失败", ConstantsData.RTN_SERVER_INTERNAL_ERROR);
        }
        return RespDTO.success("操作成功");
    }

    public RespDTO<List<SalesInfo>> querySalesInfoList(String No,String PurchaseId,String GoodsId,String ProductionBatch,String Token ){
        String jsonStr = chaincodeData.query("searchByQuery", new String[]{
                "{\"selector\":{\"No\" : \""+No+"\",\"PurchaseId\" : \"" + PurchaseId + "\"," +
                        "\"GoodsId\" : \"" + GoodsId + "\",\"ProductBatch\" : \"" + ProductionBatch + "\"}}"})
                .filter(ChaincodeResp::succeeded)
                .map(ChaincodeResp::getPayload)
                .orElse(null);
        if (StringUtils.isEmpty(jsonStr) || "null".equals(jsonStr)) {
            return RespDTO.fail("没有数据");
        }
        SalesInfoDTO salesInfoDTO = simpleGson.fromJson(jsonStr, SalesInfoDTO.class);

        JwtToken jwt = TokenUtils.parseToken(Token);
        String username = jwt.getId();
        for (Iterator<SalesInfo> it = salesInfoDTO.getList().iterator(); it.hasNext();) {
            SalesInfo SalesInfo = it.next();
            log.debug("===SalesInfo.getToken()==="+SalesInfo.getToken());
            String jsonAuth = chaincodeData.query("query", new String[] { SalesInfo.getToken() })
                    .filter(ChaincodeResp::succeeded)
                    .map(ChaincodeResp::getPayload)
                    .orElse(null);
            if (StringUtils.isEmpty(jsonAuth) || "null".equals(jsonAuth)) {
                return RespDTO.fail("操作失败", ConstantsData.RTN_UNAUTH);
            }
            log.debug("===jsonAuth==="+jsonAuth);
            Auth auth = gson.fromJson(jsonAuth, Auth.class);
            ArrayList<String> authList = auth.getAuthList();
            log.debug("===username==="+username);
            if(!authList.contains(username)){
                log.debug("===remove===");
                it.remove();
            }
        }
        if(salesInfoDTO.getList().isEmpty()){
            return RespDTO.fail("操作失败", ConstantsData.RTN_UNAUTH);
        }

        return RespDTO.success(salesInfoDTO.getList());
    }
}
